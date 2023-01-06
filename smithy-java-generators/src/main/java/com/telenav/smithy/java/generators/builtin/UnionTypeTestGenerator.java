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
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaTestGenerator;
import com.telenav.smithy.names.JavaTypes;
import static com.telenav.smithy.names.JavaTypes.forShapeType;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import java.nio.file.Path;
import java.util.Optional;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 *
 * @author Tim Boudreau
 */
final class UnionTypeTestGenerator extends AbstractJavaTestGenerator<UnionShape> {

    UnionTypeTestGenerator(UnionShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(ClassBuilder<String> cb, String typeName) {
        shape.getAllMembers().forEach((name, member) -> {
            generateUnionMemberTest(cb, typeName, name, member);
        });
    }

    @Override
    protected void onCreateTestMethod(String name, MethodBuilder<?> bldr) {
        bldr.annotatedWith("SuppressWarnings")
                .withValue("unchecked");
    }

    private void generateUnionMemberTest(ClassBuilder<String> cb,
            String typeName, String memberName, MemberShape member) {
        Shape memberShape = model.expectShape(member.getTarget());
        String memberTypeName = typeNameOf(memberShape.getId(), false);

        String mname = "As" + memberTypeName;
        String factoryMethodName = "new" + typeName;
        String parameterizedInstanceType
                = typeName + "<" + memberTypeName + ">";
        testMethod(mname, cb, bb -> {
            // Generate a println only when debug is set to true
            bb.debugLog("Union test test" + mname + "() of " + parameterizedInstanceType
                    + " on " + cb.className());
            InstanceInfo inst = declareInstance("source" + memberTypeName,
                    memberShape, bb, memberTypeName, member);

            if ("smithy.api".equals(memberShape.getId().getNamespace())) {
                JavaTypes jt = forShapeType(memberShape.getType());
                if (jt != null) {
                    String asObject = inst.instanceVar + "AsObject";
                    bb.lineComment("Need an object instance to call toString() on below");
                    bb.declare(asObject)
                            .initializedWith(inst.instanceVar)
                            .as(jt.javaTypeName());
                    inst = new InstanceInfo(inst.instanceVar, asObject);
                }
            }

            bb.lineComment("Create an instance for " + memberName
                    + " using the generated factory method.");
            bb.declare("instance")
                    .initializedByInvoking(factoryMethodName)
                    .withArgument(inst.instanceVar)
                    .on(typeName)
                    .as(parameterizedInstanceType);

            bb.invoke("assertNotNull")
                    .withArgument("instance")
                    .withStringLiteral("Factory method returned null")
                    .inScope();

            bb.declare("returned")
                    .initializedByInvoking("get")
                    .on("instance")
                    .as(memberTypeName);

            bb.invoke("assertNotNull")
                    .withArgument("returned")
                    .withStringLiteral("get() on a " + parameterizedInstanceType
                            + " returned null.")
                    .inScope();

            bb.invoke("assertSame")
                    .withArgument(inst.instanceVar)
                    .withArgument("returned")
                    .withStringLiteral("get() on a " + parameterizedInstanceType
                            + " did not return the instance it was passed.")
                    .inScope();

            bb.blankLine()
                    .lineComment("Test the optional getter");

            currentClassBuilder.importing(Optional.class);
            bb.declare("opt")
                    .initializedByInvoking("as")
                    .withClassArgument(memberTypeName)
                    .on("instance")
                    .as("Optional<" + memberTypeName + ">");

            bb.invoke("assertNotNull")
                    .withArgument("opt")
                    .withStringLiteral("as(Class) should always return an Optional, never null");

            bb.invoke("assertTrue")
                    .withArgumentFromInvoking("isPresent")
                    .on("opt")
                    .withStringLiteral("Optional should be present when as("
                            + memberTypeName + ".class) is passed and that is the type the "
                            + " union type " + parameterizedInstanceType + " was instantiated with.")
                    .inScope();

            bb.invoke("assertSame")
                    .withArgument(inst.instanceVar)
                    .withArgumentFromInvoking("get")
                    .on("opt")
                    .withStringLiteral("Optional returns different instance")
                    .inScope();

            bb.blankLine().lineComment("Sanity check the equals implementation");
            bb.invoke("assertTrue")
                    .withArgumentFromInvoking("equals")
                    .withArgument("instance")
                    .on("instance")
                    .withStringLiteral(parameterizedInstanceType + " does not equals() itself!")
                    .inScope();

            bb.blankLine().lineComment("And test that JSON serialization works "
                    + "in both directions.");
            bb.declare("mapper")
                    .initializedWithNew(nb -> nb.ofType("ObjectMapper"))
                    .as("ObjectMapper");

            if (hasTimestampInClosure(this.shape)) {
                bb.lineComment("Needed for Instant deserialization");
                currentClassBuilder.importing("com.mastfrog.jackson.configuration.JacksonConfigurer");
                bb.invoke("configureFromMetaInfServices")
                        .withArgument("mapper")
                        .on("JacksonConfigurer");
            }

            bb.declare("json")
                    .initializedByInvoking("writeValueAsString")
                    .withArgument("instance")
                    .on("mapper")
                    .asString();
            bb.declare("reconstituted")
                    .initializedWithCastTo(parameterizedInstanceType)
                    .ofInvocationOf("readValue")
                    .withArgument("json")
                    .withClassArgument(typeName)
                    .on("mapper")
                    .as(parameterizedInstanceType);

            bb.invoke("assertNotNull")
                    .withArgument("reconstituted")
                    .inScope();

            bb.invoke("assertNotNull")
                    .withArgumentFromInvoking("get")
                    .on("reconstituted")
                    .withStringLiteral("Getter on JSON deserialized "
                            + parameterizedInstanceType + " returned null.")
                    .inScope();

            assertEquals("instance", "reconstituted",
                    "JSON deserialised instance of " + parameterizedInstanceType
                    + " does not equals() the original", bb);

            bb.declare("hc1")
                    .initializedByInvoking("hashCode")
                    .on("instance")
                    .asInt();

            bb.declare("hc2")
                    .initializedByInvoking("hashCode")
                    .on("reconstituted")
                    .asInt();

            assertEquals("hc1", "hc2", "JSON-deserialized instance hash code "
                    + " of a " + parameterizedInstanceType
                    + " does not match original for " + typeName, bb);

            bb.invoke("assertEquals")
                    .withArgumentFromInvoking("get")
                    .on("instance")
                    .withArgumentFromInvoking("get")
                    .on("reconstituted")
                    .withStringLiteral("Underlying " + memberTypeName
                            + "s are not equal for " + parameterizedInstanceType)
                    .inScope();

            bb.blankLine().lineComment("Ensure that as() only returns an object for compatible types");
            String noType = "Nothing" + typeName + memberTypeName;
            bb.invoke("assertFalse")
                    .withArgumentFromInvoking("isPresent")
                    .onInvocationOf("as")
                    .withClassArgument(noType)
                    .on("instance")
                    .inScope();
            bb.lineComment("But it should return an Optional<Object> for Object.class:");
            bb.invoke("assertTrue")
                    .withArgumentFromInvoking("isPresent")
                    .onInvocationOf("as")
                    .withClassArgument("Object")
                    .on("instance")
                    .inScope();
            // Create a dummy class that as() cannot possibly return an object for
            currentClassBuilder.innerClass(noType,
                    icb -> {
                        icb.docComment("Used by " + mname + "() as a type nothing "
                                + "else can know about.");
                        icb.withModifier(PRIVATE, STATIC, FINAL)
                                .utilityClassConstructor();
                    });

        });

    }

}
