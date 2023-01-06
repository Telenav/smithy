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
package com.telenav.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;

import static com.mastfrog.util.strings.Escaper.BASIC_HTML;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.telenav.smithy.validation.ValidationExceptionProvider.generateNullCheck;
import java.io.IOException;
import static java.lang.Character.isUpperCase;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.DocumentShape;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentGenerator extends AbstractJavaGenerator<DocumentShape> {

    DocumentGenerator(DocumentShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void applyModifiers(ClassBuilder<String> cb) {
        cb.withModifier(PUBLIC, ABSTRACT);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = classHead()
                .implementing("Supplier<T>", "Serializable")
                .importing(Supplier.class);
        applyDocumentation(cb);
        // This requires some serious Jackson black magic.
        cb.importing(
                "com.fasterxml.jackson.core.JacksonException",
                "com.fasterxml.jackson.core.JsonParser",
                "com.fasterxml.jackson.core.JsonToken",
                "com.fasterxml.jackson.databind.DeserializationContext",
                "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
                "com.fasterxml.jackson.databind.deser.std.StdDeserializer",
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonIgnore",
                "java.util.List",
                "java.util.Map",
                "java.util.Optional",
                "java.io.Serializable"
        );

        cb.constructor(con -> {
            con.body(bb -> {
                bb.lineComment("Package private constructor to prevent subclassing")
                        .lineComment("outside of this package.");
            });
        });

        String factoryMethodName = "new" + capitalize(cb.className());

        generateAbstractClass(cb);
        generateDeserializer(factoryMethodName, cb);
        generateSubtypes(factoryMethodName, cb);

        addTo.accept(cb);
    }

    private void generateAbstractClass(ClassBuilder<String> cb) {
        cb.withTypeParameters("T");
        cb.method("isNumber", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents a number."
                            + "\nTrue if it is a number")
                    .body(bb -> {
                        bb.returning("get() instanceof Number");
                    });
        });

        cb.method("isDouble", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents a floating point number."
                            + "\n@return true if it is a double")
                    .body(bb -> {
                        bb.returning("get() instanceof Double");
                    });
        });

        cb.method("isLong", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents an integral number."
                            + "\n@return true if it is a long")
                    .body(bb -> {
                        bb.returning("get() instanceof Long");
                    });
        });

        cb.method("isBoolean", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents a boolean."
                            + "\n@return true if it is a boolean")
                    .body(bb -> {
                        bb.returning("get() instanceof Boolean");
                    });
        });
        cb.method("isString", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents a string."
                            + "\n@return true if it is a string")
                    .body(bb -> {
                        bb.returning("get() instanceof String");
                    });
        });
        cb.method("isMap", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents a map."
                            + "\n@return true if it is a map")
                    .body(bb -> {
                        cb.importing(Map.class);
                        bb.returning("get() instanceof Map<?,?>");
                    });
        });
        cb.method("isList", mth -> {
            mth.withModifier(PUBLIC, FINAL)
                    .annotatedWith("JsonIgnore").closeAnnotation()
                    .returning("boolean")
                    .docComment("Determine if this instance represents a list."
                            + "\n@return true if it is a list")
                    .body(bb -> {
                        cb.importing(Map.class);
                        bb.returning("get() instanceof List<?>");
                    });
        });
        cb.method("as", mth -> {
            cb.importing(Optional.class);
            mth.withModifier(PUBLIC, FINAL)
                    .withTypeParam("R")
                    .addArgument("Class<R>", "type")
                    .returning("Optional<R>")
                    .docComment("Get the value of this document, cast to the passed type,"
                            + " if it is an instance of the passed type."
                            + "\n@return an Optional")
                    .body(bb -> {
                        bb.declare("obj").initializedByInvoking("get")
                                .inScope().as("T");
                        bb.iff().invocationOf("isInstance")
                                .withArgument("obj")
                                .on("type")
                                .isTrue()
                                .endCondition()
                                .returningInvocationOf("of")
                                .withArgumentFromInvoking("cast")
                                .withArgument("obj")
                                .on("type")
                                .on("Optional")
                                .endIf();
                        bb.returningInvocationOf("empty")
                                .on("Optional");
                    });
        });

        cb.overridePublic("hashCode", mth -> {
            mth.returning("int").withModifier(FINAL)
                    .bodyReturning("get().hashCode()");
        });

        cb.overridePublic("toString", mth -> {
            mth.returning("String").withModifier(FINAL)
                    .bodyReturning("get().toString()");
        });
    }

    @Override
    protected String additionalDocumentation() {
        return "This class represents a Smithy `Document` shape - meaning it can by "
                + "any of a map, array, number or boolean.";
    }

    private void generateDeserializer(String factoryMethodName, ClassBuilder<String> outerType) {
        String desImpl = outerType.className() + "Deserializer";

        outerType.annotatedWith("JsonDeserialize", ab -> {
            ab.addClassArgument("using", outerType.className() + "." + desImpl);
        }).importing(IOException.class);

        ClassBuilder<ClassBuilder<String>> cb = outerType.innerClass(desImpl)
                .withModifier(STATIC, FINAL)
                .extending("StdDeserializer<" + outerType.className() + "<?>>")
                .constructor(con -> {
                    con.body(bb -> {
                        bb.invoke("super")
                                .withClassArgument(outerType.className())
                                .inScope();
                    });
                });
        cb.overridePublic("deserialize", mth -> {
            mth.addArgument("JsonParser", "jp")
                    .addArgument("DeserializationContext", "dc")
                    .annotatedWith("SuppressWarnings").withValue("unchecked")
                    .throwing("IOException")
                    .throwing("JacksonException")
                    .returning(outerType.className() + "<?>")
                    .body(bb -> {
                        bb.lineComment("Jackson black-magic - examine the start token");
                        bb.lineComment("and return the appropriate type - it will be unambiguous.");
                        // JsonToken tok = jp.currentToken();
                        bb.declare("tok")
                                .initializedByInvoking("currentToken")
                                .on("jp")
                                .as("JsonToken");

                        bb.iff().invokeAsBoolean("isBoolean")
                                .on("tok")
                                .returningInvocationOf(factoryMethodName)
                                .withArgumentFromInvoking("readValueAs")
                                .withClassArgument("Boolean")
                                .on("jp")
                                .inScope()
                                .endIf();

                        bb.iff().invocationOf("equals")
                                .withArgumentFromField("START_OBJECT")
                                .of("JsonToken")
                                .on("tok")
                                .isTrue()
                                .endCondition()
                                .returningInvocationOf(factoryMethodName)
                                .withArgumentFromInvoking("readValueAs")
                                .withClassArgument("Map")
                                .on("jp")
                                .inScope()
                                .endIf();
                        IfBuilder<?> num = bb.iff().invokeAsBoolean("isNumeric")
                                .on("tok");
                        num.switchingOn("tok.name()", sw -> {
                            sw.inStringLiteralCase("VALUE_NUMBER_FLOAT", cs -> {
                                cs.returningInvocationOf(factoryMethodName)
                                        .withArgumentFromInvoking("readValueAs")
                                        .withClassArgument("Double")
                                        .on("jp")
                                        .inScope();
                            });
                            sw.inDefaultCase(cs -> {
                                cs.returningInvocationOf(factoryMethodName)
                                        .withArgumentFromInvoking("readValueAs")
                                        .withClassArgument("Long")
                                        .on("jp")
                                        .inScope();
                            });
                        });
                        num.endIf();

                        bb.iff().invokeAsBoolean("equals")
                                .withArgumentFromField("START_ARRAY")
                                .of("JsonToken")
                                .on("tok")
                                .returningInvocationOf(factoryMethodName)
                                .withArgumentFromInvoking("readValueAs")
                                .withClassArgument("List")
                                .on("jp")
                                .inScope()
                                .endIf();

                        bb.returningInvocationOf(factoryMethodName)
                                .withArgumentFromInvoking("readValueAs")
                                .withClassArgument("String")
                                .on("jp")
                                .inScope();
                    });
        });
        cb.build();
    }

    private void generateSubtypes(String factoryMethodName, ClassBuilder<String> cb) {
        generateOneSubtype(factoryMethodName, "boolean", "Boolean", cb);
        generateOneSubtype(factoryMethodName, "long", "Long", cb);
        generateOneSubtype(factoryMethodName, "double", "Double", cb);
        generateOneSubtype(factoryMethodName, "String", cb);
        generateOneSubtype(factoryMethodName, "List<L>", cb, "L");
        generateOneSubtype(factoryMethodName, "Map<K, V>", cb, "K", "V");
    }

    private void generateOneSubtype(String factoryMethodName, String type,
            ClassBuilder<String> cb, String... typeParameters) {
        generateOneSubtype(factoryMethodName, type, type, cb, typeParameters);
    }

    private void generateOneSubtype(String factoryMethodName, String inputType, String outputType,
            ClassBuilder<String> outerType, String... typeParameters) {
        String outputTypeStripped = stripGenerics(outputType);
        String inner = outerType.className() + "As" + outputTypeStripped;
        outerType.importing(outerType.fqn() + "." + inner);

        outerType.method(factoryMethodName, mth -> {
            for (String tp : typeParameters) {
                mth.withTypeParam(tp);
            }
            if (typeParameters.length > 0) {
                mth.annotatedWith("SuppressWarnings").withValue("unchecked");
            }
            mth.withModifier(PUBLIC, STATIC)
                    .returning(outerType.className() + "<" + outputType + ">")
                    .addArgument(inputType, "value")
                    .docComment("Create a new <code>" + outerType.className()
                            + "<code> from a <code>" + BASIC_HTML.escape(inputType)
                            + "</code>."
                            + "\n@param value a " + inputType
                            + "\n@return a " + outerType.className());
            String ret = typeParameters.length > 0 ? inner + "<>" : inner;
            mth.body(bb -> {
                bb.returningNew(nb -> {
                    nb.withArgument("value")
                            .ofType(ret);
                });
            });
        });

        ClassBuilder<ClassBuilder<String>> cb = outerType.innerClass(inner)
                .withModifier(STATIC, FINAL)
                .extending(outerType.className() + "<" + outputType + ">");

        for (String tp : typeParameters) {
            cb.withTypeParameters(tp);
        }

        cb.field("serialVersionUid")
                .withModifier(PRIVATE, STATIC, FINAL)
                .initializedWith(1L);

        cb.field("value")
                .withModifier(PRIVATE, FINAL)
                .ofType(inputType);

        cb.constructor(con -> {
            con.addArgument(inputType, "value")
                    .body(bb -> {
                        if (inputType.equals(outputType)) {
                            generateNullCheck("value", bb, cb);
                        }
                        bb.assignField("value").ofThis()
                                .toExpression("value");
                    });
        });

        cb.overridePublic("get", mth -> {
            mth.returning(outputType)
                    .annotatedWith("JsonValue").closeAnnotation()
                    .bodyReturning("value");
        });

        cb.overridePublic("equals", mth -> {
            mth.addArgument("Object", "o")
                    .returning("boolean")
                    .body(bb -> {
                        String v = generateInitialEqualsTest(cb, bb);
                        if (!isUpperCase(inputType.charAt(0))) {
                            if ("double".equals(inputType)) {

                                bb.returning(invocationOf("doubleToLongBits")
                                        .withArgument("value")
                                        .on("Double")
                                        .isEqualTo(invocationOf("doubleToLongBits")
                                                .withArgumentFromField("value").of(v)
                                                .on("Double")));
                            } else {
                                bb.returning("value == " + v + ".value");
                            }
                        } else {
                            bb.returningInvocationOf("equals")
                                    .withArgumentFromField("value").of(v)
                                    .on("value");
                        }
                    });
        });

        cb.build();
    }

    static String stripGenerics(String txt) {
        int ix = txt.indexOf('<');
        if (ix > 0) {
            return txt.substring(0, ix);
        }
        return txt;
    }

}
