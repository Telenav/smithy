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
package com.telenav.smithy.server.common;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import static com.telenav.smithy.server.common.OriginType.HTTP_HEADER;
import static com.telenav.smithy.server.common.OriginType.URI_PATH;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import static java.lang.Integer.parseInt;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public interface InvocationBuilderTransform {

    <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>>
            InvocationBuilder<TypeAssignment<B>>
            transform(OriginType origin, ClassBuilder<?> cb, String name, I inv);

    default InvocationBuilderTransform andThen(InvocationBuilderTransform xf) {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                InvocationBuilder<TypeAssignment<B>> first = InvocationBuilderTransform.this.transform(origin, cb, name, inv);
                return xf.transform(origin, cb, name, first);
            }

            @Override
            public String toString() {
                return InvocationBuilderTransform.this + " and then " + xf;
            }
        };
    }

    default InvocationBuilderTransform precededBy(InvocationBuilderTransform xf) {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                InvocationBuilder<TypeAssignment<B>> first = xf.transform(origin, cb, name, inv);
                return InvocationBuilderTransform.this.transform(origin, cb, name, first);
            }

            @Override
            public String toString() {
                return xf + " (prec) and then " + InvocationBuilderTransform.this;
            }

        };
    }

    static InvocationBuilderTransform originMethod() {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                if (origin == URI_PATH) {
                    return inv.onInvocationOf(origin.method()).withArgument(parseInt(name));
                }
                return inv.onInvocationOf(origin.method()).withStringLiteral(name);
            }

            @Override
            public String toString() {
                return "originMethod";
            }

        };
    }

    static <N extends Number> InvocationBuilderTransform originMethodCastTo(Class<N> num) {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf(origin.method()).withStringLiteral(name).withClassArgument(num.getSimpleName());
            }

            @Override
            public String toString() {
                return "originMethodCastTo " + num.getSimpleName();
            }

        };
    }

    static <N extends Number> InvocationBuilderTransform originMethodConvertedToString() {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf("map")
                        .withMethodReference("toString").on("CharSequence")
                        .onInvocationOf(origin.method()).withStringLiteral(name);
            }

            @Override
            public String toString() {
                return "originMethodConvertedToString";
            }
        };
    }

    static InvocationBuilderTransform constructingType(String typeName) {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf("map")
                        .withMethodReference("new").on(name);
            }

            @Override
            public String toString() {
                return "constructingType " + typeName;
            }

        };
    }

    static InvocationBuilderTransform splitToStringList() {
        return splitToCollection(false);
    }

    static InvocationBuilderTransform splitToStringSet() {
        return splitToCollection(false);
    }

    static InvocationBuilderTransform splitToMappedCollection(Shape shape, Model model) {
        switch (shape.getType()) {
            case LIST:
            case SET:
                break;
            default:
                throw new AssertionError("Not a list or set shape: " + shape + " " + shape.getType());
        }
        boolean isSet = shape.getTrait(UniqueItemsTrait.class).isPresent();
        ListShape ls = shape.asListShape().get();
        MemberShape member = ls.getMember();
        Shape memberTarget = model.expectShape(member.getTarget());
        TypeNames tn = new TypeNames(model);
        boolean isModelDefined = !"smithy.api".equals(shape.getId().getNamespace());

        // PENDING: Use Declaration
        return new InvocationBuilderTransform() {

            @Override
            public String toString() {
                return "splitToMappedCollection set=" + isSet + " member " + memberTarget
                        + " modelDefined=" + isModelDefined;
            }

            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                String resultType = typeNameOf(shape);
                maybeImport(cb, tn.packageOf(shape) + "." + resultType);
                String memberType = tn.typeNameOf(cb, memberTarget, true);
                String declaredType = isSet ? "Set<" + memberType + ">" : "List<" + memberType + ">";
                if (isSet) {
                    cb.importing(Set.class, HashSet.class);
                } else {
                    cb.importing(List.class, ArrayList.class);
                }
                return inv.onInvocationOf("map")
                        .withLambdaArgument(lb -> {
                            lb.withArgument("items")
                                    .body(lbb -> {
                                        lbb.declare("parts")
                                                .initializedByInvoking("split")
                                                .withStringLiteral("\\s*,\\s*")
                                                .onInvocationOf("toString")
                                                .on("items")
                                                .as("String[]");
                                        lbb.declare("result")
                                                .initializedWithNew(nb -> {
                                                    nb.withArgumentFromField("length")
                                                            .of("parts")
                                                            .ofType(isSet ? "HashSet<>" : "ArrayList<>");
                                                }).as(declaredType);
                                        lbb.simpleLoop("String", "part", loop -> {
                                            loop.over("parts", loopBody -> {
                                                loopBody.assign("part")
                                                        .toInvocation("trim")
                                                        .on("part");
                                                loopBody.iff().booleanExpression("!part.isEmpty()")
                                                        .invoke("add")
                                                        .withArgument("part")
                                                        .on("result")
                                                        .endIf();
                                            });
                                        });

                                        if (isModelDefined) {
                                            lbb.returningNew(nb -> {
                                                nb.withArgument("result")
                                                        .ofType(resultType);
                                            });
                                        } else {
                                            lbb.returning("result");
                                        }
                                    });
                        });

            }
        };
    }

    static InvocationBuilderTransform splitToCollection(boolean isSet) {
        return new InvocationBuilderTransform() {

            @Override
            public String toString() {
                return "splitToCollection(" + (isSet ? "set)" : "list)");
            }

            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                if (isSet) {
                    cb.importing(Set.class, HashSet.class);
                } else {
                    cb.importing(List.class, ArrayList.class);
                }
                return inv.onInvocationOf("map")
                        .withLambdaArgument(lb -> {
                            lb.withArgument("items")
                                    .body(lbb -> {
                                        lbb.declare("parts")
                                                .initializedByInvoking("split")
                                                .withStringLiteral("\\s*,\\s*")
                                                .onInvocationOf("toString")
                                                .on("items")
                                                .as("String[]");
                                        lbb.declare("result")
                                                .initializedWithNew(nb -> {
                                                    nb.withArgumentFromField("length")
                                                            .of("parts")
                                                            .ofType(isSet ? "HashSet<>" : "ArrayList<>");
                                                }).as(isSet ? "Set<String>" : "List<String>");
                                        lbb.simpleLoop("String", "part", loop -> {
                                            loop.over("parts", loopBody -> {
                                                loopBody.assign("part")
                                                        .toInvocation("trim")
                                                        .on("part");
                                                loopBody.iff().booleanExpression("!part.isEmpty()")
                                                        .invoke("add")
                                                        .withArgument("part")
                                                        .on("result")
                                                        .endIf();
                                            });
                                        });
                                        lbb.returning("result");
                                    });
                        });
            }
        };
    }

//    static InvocationBuilderTransform mapToCollection(CharSequenceTransform xform) {
//        return null;
//    }
    static InvocationBuilderTransform mapToBigDecimal() {
        return mapToNewFromString("BigDecimal");
    }

    static InvocationBuilderTransform mapToBigInteger() {
        return mapToNewFromString("BigInteger");
    }

    static InvocationBuilderTransform mapToBoolean() {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf("map").withLambdaArgument(lb -> {
                    lb.withArgument("item").body(lbb -> {
                        lbb.returningInvocationOf("parseBoolean")
                                .withArgumentFromInvoking("toString")
                                .on("item")
                                .on("Boolean");
                    });
                });
            }

            @Override
            public String toString() {
                return "mapToBoolean";
            }

        };
    }

    static InvocationBuilderTransform mapToTimestamp() {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf("map").withLambdaArgument(lb -> {
                    cb.importing(Instant.class, DateTimeParseException.class);
                    cb.importing(validationExceptions().fqn());
                    lb.withArgument("item").body(lbb -> {
                        if (origin == HTTP_HEADER) {
                            lbb.lineComment("Use the logic in HeaderTypes to parse an ISO 2822 http date header");
                            cb.importing("static com.telenav.smithy.http.HeaderTypes.headerTypes");
                            ClassBuilder.TryBuilder<?> tri = lbb.trying();

                            tri.returningInvocationOf("toValue")
                                    .withArgumentFromInvoking("toString")
                                    .on("item")
                                    .onInvocationOf("dateHeader")
                                    .withStringLiteral(name)
                                    .onInvocationOf("headerTypes")
                                    .inScope();

                            tri.catching(cat -> {
                                cat.andThrow(nb -> {
                                    nb.withStringConcatentationArgument("Invalid date time header '")
                                            .appendExpression("item")
                                            .append("' for ")
                                            .append(name)
                                            .endConcatenation();
                                    nb.withArgument("thrown")
                                            .ofType(validationExceptions().name());
                                });
                            }, "DateTimeParseException", "IllegalArgumentException");

                        } else {
                            ClassBuilder.TryBuilder<?> tri = lbb.trying();
                            tri.returningInvocationOf("parse")
                                    .withArgumentFromInvoking("toString")
                                    .on("item")
                                    .on("Instant");
                            tri.catching(cat -> {
                                cat.andThrow(nb -> {
                                    nb.withStringConcatentationArgument("Invalid date time '")
                                            .appendExpression("item")
                                            .append("' for ")
                                            .append(name)
                                            .endConcatenation();
                                    nb.withArgument("thrown")
                                            .ofType(validationExceptions().name());
                                });
                            }, "DateTimeParseException");
                        }
                    });
                });
            }

            @Override
            public String toString() {
                return "mapToTimestamp";
            }
        };
    }

    static InvocationBuilderTransform mapToNewFromString(String newWhat) {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf("map").withLambdaArgument(lb -> {
                    lb.withArgument("item").body(lbb -> {
                        lbb.lineComment("mapToNewFromString " + name + " " + origin.name()
                                + " as " + newWhat);
                        if ("String".equals(newWhat)) {
                            lbb.returningInvocationOf("toString").on("item");
                        } else {
                            lbb.returningNew(nb -> {
                                nb.withArgumentFromInvoking("toString")
                                        .on("item")
                                        .ofType(newWhat);
                            });
                        }
                    });
                });
            }

            @Override
            public String toString() {
                return "mapToNewFromString(" + newWhat + ")";
            }

        };
    }

    static InvocationBuilderTransform mapToIntEnum(String enumType) {
        return new InvocationBuilderTransform() {
            @Override
            public <B extends BlockBuilderBase<T, B, ?>, T, I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    transform(OriginType origin, ClassBuilder<?> cb, String name, I inv) {
                return inv.onInvocationOf("map").withLambdaArgument(lb -> {
                    lb.withArgument("item").body(lbb -> {
                        lbb.lineComment("mapToIntEnum " + name + " " + origin.name()
                                + " as " + enumType);
                        lbb.trying(tri -> {
                            tri.declare("intValue")
                                    .initializedByInvoking("parseInt")
                                    .withArgument("item")
                                    .on("Integer")
                                    .as("int");
                            tri.returningInvocationOf("valueOf")
                                    .withArgument("intValue").on(enumType);
                            tri.catching(cat -> {
                                validationExceptions().createThrow(cb, cat, "Invalid int value for " + name, "item");
                            }, "NumberFormatException");
                        });
                    });
                });
            }

            @Override
            public String toString() {
                return "mapToNewFromString(" + enumType + ")";
            }

        };
    }

}
