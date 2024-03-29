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
import com.mastfrog.java.vogon.ClassBuilder.DeclarationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.NewBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import static com.mastfrog.util.strings.Escaper.JAVA_IDENTIFIER_CAMEL_CASE;
import com.telenav.smithy.names.TypeNames;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
public interface Declarer<B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends InvocationBuilderBase<TypeAssignment<B>, Ir>> {

    <I extends InvocationBuilderBase<TypeAssignment<B>, I>> Ir
            doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl);

    default Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> with(InvocationBuilderTransform xform) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>>
                    doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {

                Ir di = Declarer.this.doInit(origin, name, cb, bb, decl);
                return xform.transform(origin, cb, name, di);
            }

            @Override
            public String toString() {
                return Declarer.this + " with " + xform;
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> expect() {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("get");
            }

            @Override
            public String toString() {
                return "expect";
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> nullable() {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument("null");
            }

            @Override
            public String toString() {
                return "nullable";
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> orThrow(String what) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElseThrow").withLambdaArgument().body(lbb -> {
                    lbb.andThrow(nb -> {
                        nb.withStringConcatentationArgument("Parameter " + name + " is not present in ")
                                // XXX the variable name smithyRequest should be passed in - need this
                                // for debugging
                                .appendExpression("smithyRequest").endConcatenation()
                                //                        nb.withStringLiteral("Parameter " + name + " is not present in")
                                .ofType(what);
                    });
                });
            }

            @Override
            public String toString() {
                return "orThrow " + what;
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> orEmptyList() {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgumentFromInvoking("emptyList").on("Collections");
            }

            @Override
            public String toString() {
                return "orEmptyList";
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> orEmptySet() {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgumentFromInvoking("emptySet").on("Collections");
            }

            @Override
            public String toString() {
                return "orEmptySet";
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(String def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withStringLiteral(def);
            }

            @Override
            public String toString() {
                return "withStringDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(int def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withIntDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(long def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withLongDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(short def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withShortDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(byte def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withByteDefault " + def;
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(float def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withFloatDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(double def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withDoubleDefault " + def;
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefault(boolean def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgument(def);
            }

            @Override
            public String toString() {
                return "withBooleanDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withEnumDefault(String name, String type) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgumentFromField(name).of(type);
            }

            @Override
            public String toString() {
                return "withEnumDefault " + type + "." + name;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withNewDefault(String type, String arg) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withNewInstanceArgument(nb -> {
                    nb.withStringLiteral(arg).ofType(type);
                });
            }

            @Override
            public String toString() {
                return "withNewDefault " + type + "(" + arg + ")";
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withBigIntegerDefault(String init) {
        return withNewDefault("BigInteger", init);
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withBigDecimalDefault(String init) {
        return withNewDefault("BigDecimal", init);
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withTimestampDefault(String def) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("orElse").withArgumentFromInvoking("parse").withStringLiteral(def).on("Instant");
            }

            @Override
            public String toString() {
                return "withTimestampDefault " + def;
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> fromFactoryMethod(String factoryMethod) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking(factoryMethod);
            }

            @Override
            public String toString() {
                return "fromFactoryMethod " + factoryMethod;
            }
        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> fromEnumConstant(String stringValue) {
        return new Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> InvocationBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                return decl.initializedByInvoking("valueOf").withArgument(stringValue);
            }

            @Override
            public String toString() {
                return "fromEnumConstant " + stringValue;
            }
        };
    }

    default Declarer<B, Tr, Rr, NewBuilder<TypeAssignment<B>>> wrapInNew(Consumer<Ir> c) {
        return new Declarer<B, Tr, Rr, NewBuilder<TypeAssignment<B>>>() {
            @Override
            public <I extends InvocationBuilderBase<TypeAssignment<B>, I>> NewBuilder<TypeAssignment<B>> doInit(OriginType origin, String name, ClassBuilder<?> cb, B bb, DeclarationBuilder<B> decl) {
                String arg = "inputFor" + JAVA_IDENTIFIER_CAMEL_CASE.escape(name);
                DeclarationBuilder<B> d2 = bb.declare(arg);
                Ir wrapped = Declarer.this.doInit(origin, name, cb, bb, d2);
                c.accept(wrapped);
                return decl.initializedWithNew().withArgument(arg);
            }

            @Override
            public String toString() {
                return Declarer.this + " wrapInNew";
            }

        };
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, NewBuilder<TypeAssignment<B>>> withDefaultFor(
                    DefaultTrait trait, Shape shape, Model model, Consumer<InvocationBuilder<TypeAssignment<B>>> c) {
        if ("smithy.api".equals(shape.getId().getNamespace())) {
            throw new UnsupportedOperationException(trait + " " + shape + " " + shape.getType());
        } else {
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> initial
                    = withDefaultFor(trait, shape, model);
            return initial.wrapInNew(c);
        }
    }

    public static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> withDefaultFor(
                    DefaultTrait trait, Shape shape, Model model) {
        TypeNames tn = new TypeNames(model);
        switch (shape.getType()) {
            case BIG_INTEGER:
                return withBigIntegerDefault(trait.toNode().asStringNode().get().getValue());
            case BIG_DECIMAL:
                return withBigDecimalDefault(trait.toNode().asStringNode().get().getValue());
            case STRING:
                return withDefault(trait.toNode().asStringNode().get().getValue());
            case LONG:
                return withDefault(trait.toNode().asNumberNode().get().getValue().longValue());
            case INTEGER:
                return withDefault(trait.toNode().asNumberNode().get().getValue().intValue());
            case SHORT:
                return withDefault(trait.toNode().asNumberNode().get().getValue().shortValue());
            case BYTE:
                return withDefault(trait.toNode().asNumberNode().get().getValue().byteValue());
            case FLOAT:
                return withDefault(trait.toNode().asNumberNode().get().getValue().floatValue());
            case DOUBLE:
                return withDefault(trait.toNode().asNumberNode().get().getValue().doubleValue());
            case BOOLEAN:
                return withDefault(trait.toNode().asBooleanNode().get().getValue());
            case ENUM:
                String nm = TypeNames.typeNameOf(shape);
                return withEnumDefault(trait.toNode().asStringNode().get().getValue(), nm);
            case INT_ENUM:
            case LIST:
            case MAP:
            case UNION:
            case SET:
            default:
                throw new UnsupportedOperationException("Default not supported for "
                        + shape + " " + shape.getType());
        }
    }

    default Declaration<B, Tr, Rr, Ir> closedWith(DeclarationClose<B, Tr, Rr, Ir> closer) {
        return new Declaration<>(this, closer);
    }

}
