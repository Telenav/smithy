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
package com.telenav.smithy.ts.generator.type;

import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;

import com.telenav.smithy.ts.generator.AbstractTypescriptGenerator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public final class TsTypeUtils {

    private final Model model;

    public TsTypeUtils(Model model) {
        this.model = model;
    }

    public static boolean isNotUserType(Shape shape) {
        return isNotUserType(shape.getId());
    }

    public static boolean isNotUserType(ShapeId shape) {
        return "smithy.api".equals(shape.getNamespace());
    }

    public Model model() {
        return model;
    }

    public String tsTypeName(Shape shape) {
        if (isNotUserType(shape)) {
            return typeNameOf(shape, true);
        }
        return AbstractTypescriptGenerator.escape(shape.getId().getName());
    }

    public final String jsTypeOf(Shape target) {
        switch (target.getType()) {
            case TIMESTAMP:
                return "Date";
            case BOOLEAN:
                return "boolean";
            case STRING:
                return "string";
            case STRUCTURE:
            case DOCUMENT:
            case MAP:
                return "object";
            case ENUM:
                return "string";
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case BYTE:
            case DOUBLE:
            case FLOAT:
            case LONG:
            case SHORT:
            case INTEGER:
            case INT_ENUM:
                return "number";
            case SET:
            case LIST:
                return target.asListShape().map(list -> {
                    return jsTypeOf(model.expectShape(list.getMember().getTarget())) + "[]";
                }).get();
            default:
                return "any";
        }
    }

    @SuppressWarnings("deprecation")
    public final String typeNameOf(Shape target, boolean readOnly) {
        switch (target.getType()) {
            case BLOB:
            case SERVICE:
            case MEMBER:
            case OPERATION:
            case RESOURCE:
                throw new IllegalStateException("TS generation not supported for " + target.getType());
            case STRUCTURE:
//                throw new IllegalArgumentException("Attempting to generate code "
//                        + "for a Structure from the Smithy API?? Check the namespaces configuration "
//                        + "of your build - this should never happen. " + target.getId());
                return tsTypeName(target);
            case BIG_INTEGER:
            case INTEGER:
            case LONG:
            case SHORT:
            case BYTE:
//                return ("bigint");
                return ("number");
            case STRING:
                return ("string");
            case BOOLEAN:
                return ("boolean");
            case BIG_DECIMAL:
            case DOUBLE:
            case FLOAT:
                return ("number");
            case DOCUMENT:
                return ("object");
            case TIMESTAMP:
                return ("Date");
            case LIST:
                return target.asListShape().map(list -> {
                    boolean isSet = target.getTrait(UniqueItemsTrait.class).isPresent();
                    Shape memberType = model.expectShape(list.getMember().getTarget());
                    String memberTypeName;
                    if (isNotUserType(memberType)) {
                        memberTypeName = typeNameOf(memberType, false);
                    } else {
                        memberTypeName = tsTypeName(memberType);
                    }
                    if (readOnly) {
                        if (isSet) {
                            return "ReadOnlySet<" + memberTypeName + ">";
                        }
                        return "ReadOnlyArray<" + memberTypeName + ">";
                    } else {
                        if (isSet) {
                            if (readOnly) {
                                return "ReadOnlySet<" + memberTypeName + ">";
                            }
                            return "Set<" + memberTypeName + ">";
                        }
                        return typeNameOf(memberType, false) + "[]";
                    }
                }).get();
            case SET:
                return target.asSetShape().map(list -> {
                    Shape memberType = model.expectShape(list.getMember().getTarget());
                    String memberTypeName;
                    if (isNotUserType(memberType)) {
                        memberTypeName = typeNameOf(memberType, false);
                    } else {
                        memberTypeName = tsTypeName(memberType);
                    }
                    if (readOnly) {
                        return "ReadOnlySet<" + memberType + ">";
                    } else {
                        return "Set<" + memberType + ">";
                    }
                }).get();
            case MAP:
                return target.asMapShape().map(map -> {
                    String keyType;
                    if (isNotUserType(map.getKey().getTarget())) {
                        keyType = typeNameOf(model.expectShape(map.getKey().getTarget()), false);
                    } else {
                        keyType = tsTypeName(model.expectShape(map.getKey().getTarget()));
                    }
                    String valType;
                    if (isNotUserType(map.getValue().getTarget())) {
                        valType = typeNameOf(model.expectShape(map.getKey().getTarget()), false);
                    } else {
                        valType = tsTypeName(model.expectShape(map.getValue().getTarget()));
                    }
                    return "Map<" + keyType + ", " + valType + ">";
                }).get();
            case ENUM:
                switch (characterizeEnum(target.asEnumShape().get())) {
                    case HETEROGENOUS:
                    case STRING_VALUED:
                    case NONE:
                    case STRING_VALUED_MATCHING_NAMES:
                        return "string";
                    default:
                        return "number";
                }
            case INT_ENUM:
                return "number";
            case UNION:
                return "object";
            default:
                throw new AssertionError(target.getType());
        }
    }

}
