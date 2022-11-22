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
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaTestGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
final class StructureTestGenerator extends AbstractJavaTestGenerator<StructureShape> {

    StructureTestGenerator(StructureShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(ClassBuilder<String> cb, String typeName) {
        generateInstantiationAndSerializationTest();
        generateGettersTest();
    }

    void generateGettersTest() {
        testMethod("Getters", currentClassBuilder, bb -> {
            bb.debugLog("Getters of "
                    + currentTypeName + " in " + currentClassBuilder.className());

            bb.blankLine().lineComment("Create an instance of " + currentTypeName
                    + ", first instantiating any arguments needed:")
                    .blankLine();

            StructureInstantiation structInfo = instantiateStructure("instance", shape, bb);
            for (MemberInstantiation<?> mi : structInfo.members) {
                if (mi.contentsVar != null) {
                    if (!mi.member.isRequired() && !mi.member.hasDefault()) {

                        bb.invoke("assertTrue")
                                .withArgumentFromInvoking("isPresent")
                                .onInvocationOf(mi.member.getterName())
                                .on(structInfo.name)
                                .withStringLiteral("Optional for " + mi.member.jsonName() + " should not be empty")
                                .inScope();

                        bb.invoke("assertSame")
                                .withArgument(mi.memberVar)
                                .withArgumentFromInvoking("get")
                                .onInvocationOf(mi.member.getterName())
                                .on(structInfo.name)
                                .withStringLiteral("Return value of getter for " + mi.member.jsonName() + " does not match.")
                                .inScope();

                    } else {
                        bb.invoke("assertSame")
                                .withArgument(mi.memberVar)
                                .withArgumentFromInvoking(mi.member.getterName())
                                .on(structInfo.name)
                                .withStringLiteral("Return value of getter for " + mi.member.jsonName() + " does not match.")
                                .inScope();

                    }
                } else {
                    if (!mi.member.isRequired() && !mi.member.hasDefault()) {

                        bb.invoke("assertTrue")
                                .withArgumentFromInvoking("isPresent")
                                .onInvocationOf(mi.member.getterName())
                                .on(structInfo.name)
                                .withStringLiteral("Optional for " + mi.member.jsonName() + " should not be empty")
                                .inScope();

                        bb.invoke("assertSame")
                                .withArgument(mi.memberVar)
                                .withArgumentFromInvoking("get")
                                .onInvocationOf(mi.member.getterName())
                                .on(structInfo.name)
                                .withStringLiteral("Return value of getter for " + mi.member.jsonName() + " does not match.")
                                .inScope();

                    } else {
                        bb.invoke("assertEquals")
                                .withArgument(mi.memberVar)
                                .withArgumentFromInvoking(mi.member.getterName())
                                .on(structInfo.name)
                                .withStringLiteral("Return value of getter for " + mi.member.jsonName() + " does not match.")
                                .inScope();

                    }
                }
            }

        });
    }

    void generateInstantiationAndSerializationTest() {
        testMethod("InstantiationAndSerialization", currentClassBuilder, bb -> {
            bb.debugLog("Instantiation and serialization test of "
                    + currentTypeName + " in " + currentClassBuilder.className());

            bb.blankLine().lineComment("Create an instance of " + currentTypeName
                    + ", first instantiating any arguments needed:")
                    .blankLine();

            String fieldName = declareInstance(bb).instanceVar;

            bb.blankLine().lineComment("Test JSON serialization");
            bb.declare("mapper")
                    .initializedWithNew(nb -> nb.ofType("ObjectMapper"))
                    .as("ObjectMapper");

            bb.lineComment("Needed for Instant deserialization");
            currentClassBuilder.importing("com.mastfrog.jackson.configuration.JacksonConfigurer");
            bb.invoke("configureFromMetaInfServices")
                    .withArgument("mapper")
                    .on("JacksonConfigurer");

            bb.declare("json")
                    .initializedByInvoking("writeValueAsString")
                    .withArgument(fieldName)
                    .on("mapper")
                    .asString();

            bb.declare("deserialized")
                    .initializedByInvoking("readValue")
                    .withArgument("json")
                    .withClassArgument(currentTypeName)
                    .on("mapper")
                    .as(currentTypeName);

            bb.declare("hc1")
                    .initializedByInvoking("hashCode")
                    .on(fieldName)
                    .asInt();
            bb.declare("hc2")
                    .initializedByInvoking("hashCode")
                    .on("deserialized")
                    .asInt();

            assertEquals("hc1", "hc2", "Hash codes do not match", bb);

            assertEquals(fieldName, "deserialized", "Deserialized instance does not match", bb);

            // Need to use LinkedHashSet in all sets
            // for this to pass
            /*
            bb.declare("str1")
            .initializedByInvoking("toString")
            .on(fieldName)
            .asString();

            bb.declare("str2")
            .initializedByInvoking("toString")
            .on("deserialized")
            .asString();

            assertEquals("str1", "str2", "toString() results do not match", bb);
             */
            bb.blankLine().lineComment("Test Java serialization");
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
                    .withArgument(fieldName)
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

            bb.declare("reconstituted")
                    .initializedWithCastTo(currentTypeName)
                    .ofInvocationOf("readObject")
                    .on("objectIn")
                    .as(currentTypeName);

            bb.invoke("close").on("objectIn");

            assertEquals(fieldName, "reconstituted",
                    "Deserialized instance is not equal to original", bb);

            // Pending: There are still a few corner cases for this.
            /*
            bb.blankLine()
                    .lineComment("Test that string representation is valid json:");

            bb.declare("deserializedFromToString")
                    .initializedByInvoking("readValue")
                    .withArgumentFromInvoking("toString")
                    .on("reconstituted")
                    .withClassArgument(currentTypeName)
                    .on("mapper")
                    .as(currentTypeName);
            assertEquals("reconstituted",
                    "deserializedFromToString",
                    "Value deserialized from built-in JSON implementation "
                    + "did not match",
                    bb);
             */
        });
    }

}
