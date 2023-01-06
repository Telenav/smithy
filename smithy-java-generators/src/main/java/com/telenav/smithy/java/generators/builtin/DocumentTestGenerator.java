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
import com.mastfrog.java.vogon.ClassBuilder.DeclarationBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaTestGenerator;
import com.mastfrog.util.strings.RandomStrings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentTestGenerator extends AbstractJavaTestGenerator<DocumentShape> {

    private final RandomStrings strings;

    DocumentTestGenerator(DocumentShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        strings = new RandomStrings(rnd);
    }

    @Override
    protected void generate(ClassBuilder<String> cb, String typeName) {
        String factoryMethodName = "new" + typeName;
        generateOneTestMethod(factoryMethodName, LONG, "long", "Long");
        generateOneTestMethod(factoryMethodName, DOUBLE, "double", "Double");
        generateOneTestMethod(factoryMethodName, BOOLEAN, "boolean", "Boolean");
        generateOneTestMethod(factoryMethodName, STRING, "String", "String");
        generateOneTestMethod(factoryMethodName, MAP, "Map<String, Integer>", "Map<String, Integer>");
        generateOneTestMethod(factoryMethodName, LIST, "List<String>", "List<String>");
    }

    @Override
    protected void onCreateTestMethod(String name, ClassBuilder.MethodBuilder<?> mth) {
        mth.annotatedWith("SuppressWarnings").withValue("unchecked");
    }

    private void generateOneTestMethod(String factoryMethodName, ShapeType shapeType,
            String inputType, String outputType) {
        testMethod("As" + DocumentGenerator.stripGenerics(outputType), currentClassBuilder, bb -> {
            String input = "rawValue";
            DeclarationBuilder<?> decl = bb.declare(input);
            switch (shapeType) {
                case LONG:
                    decl.initializedTo((long) rnd.nextInt());
                    break;
                case DOUBLE:
                    decl.initializedTo(rnd.nextDouble());
                    break;
                case STRING:
                    decl.initializedWithStringLiteral(strings.get(16)).asString();
                    break;
                case BOOLEAN:
                    decl.initializedTo(rnd.nextBoolean());
                    break;
                case MAP:
                    currentClassBuilder.importing(Map.class, LinkedHashMap.class);
                    int mapCount = rnd.nextInt(5) + 2;
                    bb.declare("map")
                            .initializedWithNew(nb -> {
                                nb.withArgument(mapCount)
                                        .ofType("LinkedHashMap<>");
                            }).as("Map<String, Integer>");
                    for (int i = 0; i < mapCount; i++) {
                        String k = strings.randomChars(12);
                        int v = rnd.nextInt();
                        bb.invoke("put")
                                .withStringLiteral(k)
                                .withArgument(v)
                                .on("map");
                    }
                    decl.initializedWith("map")
                            .as(inputType);
                    break;
                case LIST:
                    currentClassBuilder.importing(List.class, ArrayList.class);
                    int listCount = rnd.nextInt(5) + 2;
                    decl
                            .initializedWithNew(nb -> {
                                nb.withArgument(listCount)
                                        .ofType("ArrayList<>");
                            }).as(inputType);
                    for (int i = 0; i < listCount; i++) {
                        bb.invoke("add").withStringLiteral(strings.get()).on("rawValue");
                    }
                    break;
                default:
                    throw new AssertionError(shapeType);
            }
            bb.blankLine().lineComment("Instantiate an instance.");
            bb.declare("instance")
                    .initializedByInvoking(factoryMethodName)
                    .withArgument("rawValue")
                    .on(currentTypeName)
                    .as(currentTypeName + "<" + outputType + ">");

            bb.blankLine().lineComment("Ensure the factory method is sane.");
            bb.invoke("assertNotNull").withArgument("instance").inScope();

            bb.blankLine().lineComment("Ensure get(), equals(), toString() and hashCode() do the right thing.");
            switch (shapeType) {
                case LIST:
                case STRING:
                case MAP:
                    bb.invoke("assertSame").withArgument("rawValue")
                            .withArgumentFromInvoking("get")
                            .on("instance")
                            .withStringLiteral("get() should return the input argument.")
                            .inScope();

                    bb.invoke("assertEquals")
                            .withArgumentFromInvoking("hashCode")
                            .on("rawValue")
                            .withArgumentFromInvoking("hashCode")
                            .on("instance")
                            .withStringLiteral("hashCode() for the " + currentTypeName
                                    + " should proxy the hash code of the wrapped object.")
                            .inScope();
                    bb.invoke("assertEquals")
                            .withArgumentFromInvoking("toString")
                            .on("rawValue")
                            .withArgumentFromInvoking("toString")
                            .on("instance")
                            .withStringLiteral("toString() for the " + currentTypeName
                                    + " should proxy toString() of the wrapped object.")
                            .inScope();
                    break;
                default:
                    bb.declare("boxed")
                            .initializedWith("rawValue")
                            .as(outputType);
                    bb.invoke("assertEquals")
                            .withArgument("boxed")
                            .withArgumentFromInvoking("get")
                            .on("instance")
                            .withStringLiteral("get() should return an object equal to the boxed original.")
                            .inScope();
                    bb.invoke("assertEquals")
                            .withArgumentFromInvoking("hashCode")
                            .on("boxed")
                            .withArgumentFromInvoking("hashCode")
                            .on("instance")
                            .withStringLiteral("hashCode() for the " + currentTypeName
                                    + " should proxy the hash code of the wrapped object.")
                            .inScope();
                    bb.invoke("assertEquals")
                            .withArgumentFromInvoking("toString")
                            .on("boxed")
                            .withArgumentFromInvoking("toString")
                            .on("instance")
                            .withStringLiteral("toString() for the " + currentTypeName
                                    + " should proxy toString() of the wrapped object.")
                            .inScope();
                    break;
            }
            bb.blankLine().lineComment("Test JSON serialization - the JSON body should be.")
                    .lineComment("The raw value with no type information or extraneous fields.");
            bb.declare("mapper")
                    .initializedWithNew(nb -> {
                        nb.ofType("ObjectMapper");
                    }).as("ObjectMapper");

            bb.declare("json")
                    .initializedByInvoking("writeValueAsString")
                    .withArgument("instance")
                    .on("mapper")
                    .as("String");

            bb.declare("reconstituted")
                    .initializedByInvoking("readValue")
                    .withArgument("json")
                    .withClassArgument(currentTypeName)
                    .on("mapper")
                    .as(currentTypeName + "<" + outputType + ">");

            assertEquals("instance", "reconstituted", "JSON deserialized "
                    + currentTypeName + "<" + outputType + "> "
                    + " does not match the original", bb);

            bb.blankLine().lineComment("Test the casting getter.");
            bb.invoke("assertTrue")
                    .withArgumentFromInvoking("isPresent")
                    .onInvocationOf("as")
                    .withClassArgument(DocumentGenerator.stripGenerics(outputType))
                    .on("instance")
                    .inScope();

            bb.invoke("assertTrue")
                    .withArgumentFromInvoking("isPresent")
                    .onInvocationOf("as")
                    .withClassArgument("Object")
                    .on("instance")
                    .inScope();

            if (shapeType == STRING) {
                bb.invoke("assertTrue")
                        .withArgumentFromInvoking("isPresent")
                        .onInvocationOf("as")
                        .withClassArgument("CharSequence")
                        .on("instance")
                        .inScope();
            }
            bb.invoke("assertFalse")
                    .withArgumentFromInvoking("isPresent")
                    .onInvocationOf("as")
                    .withClassArgument("Exception")
                    .on("instance")
                    .inScope();

            bb.blankLine().lineComment("Test java serialization.");

            currentClassBuilder.importing(
                    ObjectInputStream.class,
                    ObjectOutputStream.class,
                    ByteArrayInputStream.class,
                    ByteArrayOutputStream.class
            );
            bb.declare("bytesOut")
                    .initializedWithNew(nb -> nb.ofType("ByteArrayOutputStream"))
                    .as("ByteArrayOutputStream");
            bb.declare("objectOut")
                    .initializedWithNew(nb -> {
                        nb.withArgument("bytesOut").ofType("ObjectOutputStream");
                    }).as("ObjectOutputStream");

            bb.invoke("writeObject")
                    .withArgument("instance")
                    .on("objectOut");
            bb.invoke("close").on("objectOut");

            bb.blankLine();
            bb.declare("bytesIn")
                    .initializedWithNew(nb -> {
                        nb.withArgumentFromInvoking("toByteArray")
                                .on("bytesOut")
                                .ofType("ByteArrayInputStream");
                    }).as("ByteArrayInputStream");

            bb.declare("objectIn")
                    .initializedWithNew(nb -> {
                        nb.withArgument("bytesIn")
                                .ofType("ObjectInputStream");
                    }).as("ObjectInputStream");

            bb.declare("deserialized")
                    .initializedWithCastTo(currentTypeName + "<" + outputType + ">")
                    .ofInvocationOf("readObject")
                    .on("objectIn")
                    .as(currentTypeName + "<" + outputType + ">");

            bb.invoke("close").on("objectIn");

            assertEquals("instance", "deserialized",
                    "Deserialized instance is not equal to original", bb);

        });
    }

}
