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
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaTestGenerator;
import com.telenav.smithy.extensions.UnitsTrait;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.RangeTrait;

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
        if (isAmountStructure()) {
            generateAmountTypeTest();
        }
    }

    void generateAmountTypeTest() {
        withEnumAndNumberShape((String enumMemberName, MemberShape enumMember, EnumShape enu,
                String numberMemberName, MemberShape numberMember, NumberShape numberShape,
                boolean enumFirst) -> {
            testMethod("Amount", currentClassBuilder, bb -> {
                String[] fqns = new String[]{names().packageOf(enu) + "." + typeNameOf(enu)};
                maybeImport(currentClassBuilder, fqns);

                String minVal = instantiateMinimalValue(numberMember, numberShape, bb);
                if (minVal == null) {
                    // gave up deciphering a minimal value with a low maximum
                    return;
                }

                Map.Entry<String, MemberShape> onesMember = findOnesUnitMember(enu);
                String onesvar = escape(onesMember.getKey() + "_value");
                bb.declare(onesvar).initializedFromField(escape(onesMember.getKey()))
                        .of(typeNameOf(enu))
                        .as(typeNameOf(enu));

                String inst = newVarName("instance");
                bb.declare(inst).initializedWithNew(nb -> {
                    if (enumFirst) {
                        nb.withArgument(onesvar);
                    }
                    nb.withArgument(minVal);
                    if (!enumFirst) {
                        nb.withArgument(onesvar);
                    }
                    nb.ofType(currentTypeName);
                }).as(currentTypeName);

//                StructureInstantiation structInfo = instantiateStructure("instance", shape, bb);
                bb.lineComment("This is an amount-pattern shape. Test its conversion methods.");
//                MemberInstantiation<?> enumVar = structInfo.find(enumMember);
//                MemberInstantiation<?> numberVar = structInfo.find(numberMember);

                bb.simpleLoop(typeNameOf(enu), "unit")
                        .over(typeNameOf(enu) + ".values()", loop -> {

                            loop.iff().booleanExpression("Math.abs(unit.ordinal() - " + onesvar + ".ordinal()) > 1")
                                    .lineComment("Stick to testing adjacent units so we don't wind up trying to convert")
                                    .lineComment("things that overflow, like trillions of months to nanoseconds.")
                                    .statement("continue").endIf();

                            loop.declare("transformed")
                                    .initializedByInvoking("to")
                                    .withArgument("unit")
                                    .on(inst)
                                    .as(currentTypeName);

                            loop.invoke("assertNotNull")
                                    .withArgument("transformed")
                                    .withStringLiteral("to() should never return null")
                                    .inScope();

                            ClassBuilder.ElseClauseBuilder<?> els = loop.iff().booleanExpression("unit == " + onesvar)
                                    .invoke("assertSame")
                                    .withArgument(inst)
                                    .withArgument("transformed")
                                    .withStringLiteral("to(" + typeNameOf(enu) + ") should return 'this' "
                                            + "when passed the same unit as the instance it is invoked on.")
                                    .inScope()
                                    .orElse();

                            // Pending - this can work, but we will need to delve much deeper into
                            // the details of the enum and the rounding errors involved
                            /*
                            String rev = newVarName("reverted");
                            els.declare(rev)
                                    .initializedByInvoking("to")
                                    .withArgument(onesvar)
                                    .on("transformed")
                                    .as(currentTypeName);
                            if (numberShape.isDoubleShape() || numberShape.isFloatShape()) {
                                String unitMethod = escape(decapitalize(enumMemberName));
                                els.invoke("assertSame")
                                        .withArgumentFromInvoking(unitMethod)
                                        .on(inst)
                                        .withArgumentFromInvoking(unitMethod)
                                        .on(rev)
                                        .inScope();
                            }
                            if (numberShape.isDoubleShape()) {
                                String methodName = escape(decapitalize(numberMemberName));
                                els.invoke("assertEquals")
                                        .withArgumentFromInvoking(methodName)
                                        .on(inst)
                                        .withArgumentFromInvoking(methodName)
                                        .on(rev)
                                        .withArgument(0.0000001D)
                                        .inScope();
                            } else if (numberShape.isFloatShape()) {
                                String methodName = escape(decapitalize(numberMemberName));
                                els.invoke("assertEquals")
                                        .withArgumentFromInvoking(methodName)
                                        .on(inst)
                                        .withArgumentFromInvoking(methodName)
                                        .on(rev)
                                        .withArgument(0.0000001F)
                                        .inScope();

                            } else {
                                els.invoke("assertEquals")
                                        .withArgument(inst)
                                        .withArgument(rev)
                                        .withStringConcatentationArgument("Converting back to original")
                                        .append("Converting back to original " + typeNameOf(enu) + " should result in "
                                                + "an instance which equals() the original (possibly modulo rounding errors)")
                                        .append(" but this seems not to be the case converting ")
                                        .appendExpression(onesvar)
                                        .append(" -> ")
                                        .appendExpression("unit")
                                        .append(" -> ")
                                        .appendExpression(onesvar)
                                        .append(" via ")
                                        .appendExpression("transformed")
                                        .endConcatenation()
                                        .inScope();
                            }
                             */
                            els.endIf();
                        });
            });
        });
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

                        bb.invoke("assertEquals")
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

            // This will work except in the case of union types.
            // A union type has a leading field name that identifies which
            // branch of the union it is, and the value cannot know that it
            // is being used in a union.
            //
            // This might be fixable in the generation of toString() on structures,
            // but would not be trivial
//            if (!containsUnionType()) {
            bb.blankLine()
                    .lineComment("Test that string representation is valid json.")
                    .lineComment("the generated code SHOULD always return valid json unless")
                    .lineComment("foreign implementations of types are added to collections held by our types.");

            bb.trying(tri -> {
                currentClassBuilder.importing("com.fasterxml.jackson.databind.JsonMappingException");
                tri.declare("deserializedFromToString")
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
                        tri);
                tri.catching(cat -> {
                    cat.andThrow(nb -> {
                        nb.withStringConcatentationArgument("toString() on a ")
                                .append(currentTypeName)
                                .append(" did not return valid JSON.\nContent:\n'")
                                .appendInvocationOf("toString")
                                .on("reconstituted")
                                .append('\'')
                                .endConcatenation()
                                .withArgument("thrown")
                                .ofType("AssertionError");
                    });
                }, "JsonMappingException");
            });
//            }
        });
    }

    private boolean containsUnionType() {
        return containsUnionType(shape, model, new HashSet<>());
    }

    private static boolean containsUnionType(Shape shape, Model model, Set<ShapeId> seen) {
        if (shape.isUnionShape()) {
            return true;
        }
        if (seen.contains(shape.getId())) {
            return false;
        }
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            Shape target = model.expectShape(e.getValue().getTarget());
            if (!"smithy.api".equals(target.getId().getNamespace())) {
                boolean result = containsUnionType(target, model, seen);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    interface EnumInfoConsumer {

        void accept(String enumMemberName, MemberShape enumMember, EnumShape enumShape,
                String numberMemberName, MemberShape numberMember, NumberShape numberShape,
                boolean enumFirst);
    }

    private boolean withEnumAndNumberShape(EnumInfoConsumer c) {
        Map<String, MemberShape> mems = shape.getAllMembers();
        if (mems.size() != 2) {
            return false;
        }
        String numberMemberName = null;
        String enumMemberName = null;
        MemberShape enumMember = null;
        MemberShape numberMember = null;
        NumberShape num = null;
        EnumShape enu = null;
        boolean enumFirst = false;
        for (Map.Entry<String, MemberShape> e : mems.entrySet()) {
            Shape target = model.expectShape(e.getValue().getTarget());
            if (target.isEnumShape()) {
                enu = target.asEnumShape().get();
                enumMemberName = e.getKey();
                enumMember = e.getValue();
                enumFirst = num == null;
            } else if (target.isIntegerShape()) {
                num = target.asIntegerShape().get();
                numberMember = e.getValue();
                numberMemberName = e.getKey();
            } else if (target.isDoubleShape()) {
                num = target.asDoubleShape().get();
                numberMember = e.getValue();
                numberMemberName = e.getKey();
            } else if (target.isLongShape()) {
                num = target.asLongShape().get();
                numberMember = e.getValue();
                numberMemberName = e.getKey();
            } else if (target.isShortShape()) {
                num = target.asShortShape().get();
                numberMember = e.getValue();
                numberMemberName = e.getKey();
            } else if (target.isFloatShape()) {
                num = target.asFloatShape().get();
                numberMember = e.getValue();
                numberMemberName = e.getKey();
            } else if (target.isByteShape()) {
                num = target.asByteShape().get();
                numberMember = e.getValue();
                numberMemberName = e.getKey();
            }
        }
        boolean result = num != null && enu != null;
        if (result) {
            Collection<MemberShape> enumMembers = enu.getAllMembers().values();
            for (MemberShape ms : enumMembers) {
                result = ms.getTrait(UnitsTrait.class).isPresent();
                if (!result) {
                    break;
                }
            }
        }
        if (result) {
            c.accept(enumMemberName, enumMember, enu, numberMemberName, numberMember, num, enumFirst);
        }
        return result;
    }

    private Map.Entry<String, MemberShape> findOnesUnitMember(EnumShape shp) {
        for (Map.Entry<String, MemberShape> e : shp.getAllMembers().entrySet()) {
            UnitsTrait units = e.getValue().expectTrait(UnitsTrait.class);
            if (units.isOnesUnit()) {
                return e;
            }
        }
        throw new IllegalStateException("No ones member in " + shp);
    }

    private <B extends BlockBuilderBase<T, B, X>, T, X> String instantiateMinimalValue(
            MemberShape mem, NumberShape shp, B bb) {

        String nv = newVarName(escape("nv" + shp.getId().getName()));

        Optional<RangeTrait> rng = mem.getTrait(RangeTrait.class).or(() -> shp.getTrait(RangeTrait.class));

        double proposedValue = 2;
        if (rng.isPresent()) {
            Optional<BigDecimal> min = rng.get().getMin();
            Optional<BigDecimal> max = rng.get().getMax();
            if (min.isPresent() && min.get().doubleValue() > proposedValue) {
                proposedValue = min.get().doubleValue();
            }
            if (max.isPresent() && max.get().doubleValue() < proposedValue) {
                return null; // give up
            }
        }

        switch (shp.getType()) {
            case INTEGER:
                bb.declare(nv).initializedTo((int) proposedValue);
                break;
            case LONG:
                bb.declare(nv).initializedTo((long) proposedValue);
                break;
            case SHORT:
                bb.declare(nv).initializedTo((short) proposedValue);
                break;
            case BYTE:
                bb.declare(nv).initializedTo((byte) proposedValue);
                break;
            case DOUBLE:
                bb.declare(nv).initializedTo(proposedValue);
                break;
            case FLOAT:
                bb.declare(nv).initializedTo((float) proposedValue);
                break;
            default:
                throw new AssertionError("Amount types for " + shp.getType() + " not supported");
        }

        return nv;
    }

    private boolean isAmountStructure() {
        return withEnumAndNumberShape((a, b, c, d, e, f, g) -> {
        });
    }
}
