/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.smithy.smithy.ts.generator.type;

import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.utils.EnumCharacteristics;
import static com.telenav.smithy.utils.EnumCharacteristics.HETEROGENOUS;
import static com.telenav.smithy.utils.EnumCharacteristics.NONE;
import static com.telenav.smithy.utils.EnumCharacteristics.STRING_VALUED;
import static com.telenav.smithy.utils.EnumCharacteristics.STRING_VALUED_MATCHING_NAMES;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import static java.util.Collections.synchronizedMap;
import java.util.Map;
import java.util.WeakHashMap;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BLOB;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.DOCUMENT;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import static software.amazon.smithy.model.shapes.ShapeType.MEMBER;
import static software.amazon.smithy.model.shapes.ShapeType.SET;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import static software.amazon.smithy.model.shapes.ShapeType.UNION;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public class TypeStrategies {

    private static final Map<Model, TypeStrategies> CACHE
            = synchronizedMap(new WeakHashMap<>());
    private final TsTypeUtils types;

    TypeStrategies(Model model) {
        this(new TsTypeUtils(model));
    }

    TypeStrategies(TsTypeUtils types) {
        this.types = types;
    }

    public Model model() {
        return types.model();
    }

    public TsTypeUtils types() {
        return types;
    }

    public static TypeStrategies strategies(Model mdl) {
        return CACHE.computeIfAbsent(mdl, m -> new TypeStrategies(m));
    }

    public TypeStrategy<?> strategy(Shape shape) {
        return createStrategy(shape);
    }

    public String tsTypeName(Shape shape) {
        return types.tsTypeName(shape);
    }

    public final String jsTypeOf(Shape target) {
        return types.jsTypeOf(target);
    }

    @SuppressWarnings("deprecation")
    public final String typeNameOf(Shape target, boolean readOnly) {
        return types.typeNameOf(target, readOnly);
    }

    public static boolean isNotUserType(Shape shape) {
        return isNotUserType(shape.getId());
    }

    public static boolean isNotUserType(ShapeId shape) {
        return "smithy.api".equals(shape.getNamespace());
    }

    protected <S extends Shape> TypeStrategy<?> createStrategy(S shape) {
        boolean prim = TypeStrategies.isNotUserType(shape);
        switch (shape.getType()) {
            case MEMBER:
                return createStrategy(model().expectShape(shape.asMemberShape().get().getTarget()));
            case STRUCTURE:
                return new StructureStrategy(shape.asStructureShape().get(), this);
            case LONG:
            case INTEGER:
            case FLOAT:
            case DOUBLE:
            case BYTE:
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case SHORT:
                if (prim) {
                    return primitiveStrategy(shape);
                } else {
                    return new NumberStringAndBooleanStrategy((NumberShape) shape, this);
                }
            case TIMESTAMP:
                return new TimestampStrategy(shape.asTimestampShape().get(), this);
            case BOOLEAN:
                if (prim) {
                    return primitiveStrategy(shape);
                }
                return new NumberStringAndBooleanStrategy(shape, this);
            case UNION:
                return new UnionStrategy(shape.asUnionShape().get(), this);
            case DOCUMENT:
            case BLOB:
                return primitiveStrategy(shape);
            case STRING:
                if (prim) {
                    return new PrimitiveStringStrategy(shape.asStringShape().get(), this);
                } else {
                    return new NumberStringAndBooleanStrategy(shape, this);
                }
            case ENUM:
                EnumShape enumShape = shape.asEnumShape().get();
                EnumCharacteristics enumType = characterizeEnum(enumShape);
                switch (enumType) {
                    case HETEROGENOUS:
                    case NONE:
                    case STRING_VALUED:
                        return new DefaultEnumStrategy(enumShape, this);
                    case INT_VALUED:
                        return new IntKeyedEnumStrategy(enumShape, this);
                    case STRING_VALUED_MATCHING_NAMES:
                        return new StringEnumStrategy(enumShape, this);
                    default:
                        throw new AssertionError(enumType);
                }
            case LIST:
            case SET:
                ListShape listShape = shape.asListShape().get();
                Shape listMember = model().expectShape(listShape.getMember().getTarget());
                boolean isSet = shape.getType() == ShapeType.SET
                        || listShape.getTrait(UniqueItemsTrait.class).isPresent();
                if (isSet) {
                    if (prim) {
                        return new PrimitiveSetStrategy(listShape, this, listMember);
                    }
                    return new SetStrategy(listShape, this, listMember);
                } else {
                    if (prim) {
                        return new PrimitiveListStrategy(listShape, this, listMember);
                    }
                    return new ListStrategy(listShape, this, listMember);
                }
            case INT_ENUM:
                return new IntEnumStrategy(shape.asIntEnumShape().get(), this);
            case MAP:
                MapShape mapShape = shape.asMapShape().get();
                Shape mapKeyShape = model().expectShape(mapShape.getKey().getTarget());
                Shape mapValueShape = model().expectShape(mapShape.getValue().getTarget());
                if (prim) {
                    return new PrimitiveMapStrategy(mapShape, this, mapKeyShape, mapValueShape);
                } else {
                    return new MapStrategy(mapShape, this, mapKeyShape, mapValueShape);
                }
            default:
                throw new AssertionError("Not convertable into a typescript type: "
                        + shape.getId() + " of type " + shape.getType());
        }
    }

    <S extends Shape> PrimitiveStrategy<S> primitiveStrategy(S shape) {
        return new PrimitiveStrategy<>(shape, this);
    }

}
