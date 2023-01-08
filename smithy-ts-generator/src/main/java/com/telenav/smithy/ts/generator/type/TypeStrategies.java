/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.ts.generator.type;

import com.telenav.smithy.utils.EnumCharacteristics;

import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import static java.util.Collections.synchronizedMap;
import java.util.Map;
import java.util.WeakHashMap;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import software.amazon.smithy.model.shapes.StructureShape;
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

    public <S extends Shape> TypeStrategy<?> strategy(S shape) {
        return createStrategy(shape);
    }

    public <S extends Shape> MemberStrategy<S> memberStrategy(MemberShape member, S shape) {
        assert shape.getId().equals(member.getTarget());
        return createMemberStrategy(member, shape);
    }

    public MemberStrategy<?> memberStrategy(MemberShape member) {
        return createMemberStrategy(member, types.model().expectShape(member.getTarget()));
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

    @SuppressWarnings("unchecked")
    protected <S extends Shape> MemberStrategy<S> createMemberStrategy(MemberShape mem, S shape) {

        assert shape.getType() != ShapeType.MEMBER;
        TypeStrategy<S> strat = (TypeStrategy<S>) createStrategy(shape);
        switch (shape.getType()) {
            case TIMESTAMP:
                return (MemberStrategy<S>) new TimestampMemberStrategy(strat.asTimestampStrategy().get(), mem);
            default:
                Shape container = types().model().expectShape(mem.getContainer());
                if (/* isSingleMember(container) && */isSimpleType(shape) && !isNotUserType(shape)) {
                    // ConvenienceConstructorArgStrategy
                    System.out.println("BINGO " + container.getId().getName() + "." + mem.getMemberName());
                    return (MemberStrategy<S>) new ConvenienceConstructorArgStrategy<>(strat, mem);
                }
//                System.out.println("CHECK " );
//                if (isSingleMember(container)) {
//                    if (isWrapperWrapper(container.asStructureShape().get())) {
//                        System.out.println("BINGO " + container.getId().getName() + "." + mem.getMemberName());
//                        return (MemberStrategy<S>) new SingleMemberStructureStrategy(strat.asStructureStrategy().get(), mem);
//                    }
//                }
        }
        return new DefaultMemberStrategy<>(strat, mem);
    }

    private boolean isSingleMember(Shape shape) {
        return shape.asStructureShape().map(shp -> isSingleMember(shp)).orElse(false);
    }

    private boolean isSingleMember(StructureShape shape) {
        return shape.getAllMembers().size() == 1;
    }

    private boolean isWrapperWrapper(StructureShape shape) {
        System.out.println("CHECKOUT " + shape.getId().getName());
        // ReadBlogInput has shape BlogId which is a string wrapper
        if (isSingleMember(shape)) { // it has one member
            System.out.println("YES SINGLE MEMBER: " + shape.getId().getName());
            MemberShape singleMember = shape.getAllMembers().values().iterator().next();
            Shape target = types().model().expectShape(singleMember.getTarget());

            System.out.println("  HAS TARGET " + target.getId().getName()
                    + " for " + singleMember.getMemberName());

            if (target.isStructureShape() /* && isSingleMember(target.asStructureShape().get()) */) {
                // its one member is a model-file defined type
                MemberShape singleNestedMember = target.getAllMembers()
                        .values().iterator().next();
                Shape nestedTarget = types().model()
                        .expectShape(singleNestedMember.getTarget());
                System.out.println("Doubly nested: " + nestedTarget.getId().getName());
//                return isSimpleType(nestedTarget);
                return true;
            } else {
                System.out.println("Singly nested: " + target.getId().getName());
                return isSimpleType(target);
            }
        }
        return false;
    }

    private static boolean isSimpleType(Shape target) {
        switch (target.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case BOOLEAN:
            case BYTE:
            case FLOAT:
            case INTEGER:
            case LONG:
            case SHORT:
            case STRING:
                return true;
        }
        return false;
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
                    return primitiveNumberStrategy((NumberShape) shape);
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
                return new DocumentStrategy(shape.asDocumentShape().get(), this);
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

    <S extends NumberShape> PrimitiveNumberStrategy<S> primitiveNumberStrategy(S shape) {
        return new PrimitiveNumberStrategy<>(shape, this);
    }

}
