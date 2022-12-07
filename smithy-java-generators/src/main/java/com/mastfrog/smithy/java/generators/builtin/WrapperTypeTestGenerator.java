
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.function.state.Int;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.NewBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TryBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaTestGenerator;
import com.mastfrog.smithy.simple.extensions.SamplesTrait;
import static com.mastfrog.util.strings.Escaper.JAVA_IDENTIFIER_CAMEL_CASE;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.mastfrog.util.strings.Strings.escape;
import static com.telenav.smithy.names.NumberKind.forShape;
import static com.telenav.validation.ValidationExceptionProvider.validationExceptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.MEMBER;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 *
 * @author Tim Boudreau
 */
final class WrapperTypeTestGenerator extends AbstractJavaTestGenerator<Shape> {

    WrapperTypeTestGenerator(Shape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(ClassBuilder<String> cb, String typeName) {
        if (shape.getType() == BOOLEAN) {
            generateBooleanFactoryMethodTest();
        } else {
            generateEqualityTest();
        }
        generateJsonSerializationTest();
        generateJavaSerializationTest();
        generateGetterTest();
        generateToStringTest();

        if (shape.getType() == STRING) {
            invalidStringSamples().forEach(inv -> generateInvalidValueTest(inv));
            if (shape.getTrait(SamplesTrait.class).isPresent()) {
                generateValidSampleTests();
            }
        } else if (forShape(shape) != null) {
            generateInvalidNumberTests();
        }
    }

    private void generateValidSampleTests() {
        for (String sample : validStringSamples()) {
            String mname = "Valid_"
                    + (sample.isEmpty() ? "EmptyString"
                    : escape(sample, JAVA_IDENTIFIER_CAMEL_CASE));
            testMethod(mname, currentClassBuilder, bb -> {
                bb.lineComment("If an exception is thrown, either the sample")
                        .lineComment("or the pattern or length constraint is broken.");

                bb.trying(tri -> {
                    String v = decapitalize(currentTypeName);
                    tri.declare(decapitalize(currentTypeName))
                            .initializedWithNew(nb -> {
                                nb.withStringLiteral(sample)
                                        .ofType(currentTypeName);
                            }).as(currentTypeName);
                    currentClassBuilder.importing(validationExceptions().fqn());
                    tri.catching(cat -> {
                        cat.andThrow(nb -> {
                            nb.withStringConcatentationArgument("The string '")
                                    .append(sample)
                                    .append("' is listed as a *valid* example of a ")
                                    .append(currentClassBuilder.className())
                                    .append(" but constructing one with it as an argument "
                                            + "threw a validation exception.  Either the sample "
                                            + "is in error, or the @pattern or @length traits for it are.")
                                    .endConcatenation()
                                    .withArgument("thrown")
                                    .ofType("AssertionError");
                        });
                    }, validationExceptions().name());
                });

            });
        }
    }

    private void generateToStringTest() {
        if (shape.getType() == STRING && shape.getTrait(SensitiveTrait.class).isPresent()) {
            generateElidedToStringTest();
        } else {
            generateToUnelidedStringTest();
        }
    }

    private void generateToUnelidedStringTest() {
        testMethod("NormalToString", currentClassBuilder, bb -> {
            InstanceInfo inst = declareInstance(bb);
            bb.invoke("assertEquals")
                    .withArgumentFromInvoking("toString")
                    .onInvocationOf("get")
                    .on(inst.instanceVar)
                    .withArgumentFromInvoking("toString")
                    .on(inst.instanceVar)
                    .withStringLiteral("toString() on a wrapper type should be a pass-through to that of the wrapped object")
                    .inScope();
        });
    }

    private void generateElidedToStringTest() {
        testMethod("ObfucatedToString", currentClassBuilder, bb -> {
            InstanceInfo inst = declareInstance(bb);
            bb.declare("stringValue")
                    .initializedByInvoking("toString")
                    .on(inst.instanceVar)
                    .asString();

            bb.iff(invocationOf("length")
                    .on("stringValue").isEqualTo(number(0)))
                    .statement("return").endIf();

            bb.declare("origStringValue")
                    .initializedByInvoking("toString")
                    .onInvocationOf("get")
                    .on(inst.instanceVar)
                    .asString();

            bb.invoke("assertNotEquals")
                    .withArgument("origStringValue")
                    .withArgument("stringValue")
                    .withStringLiteral("String wrappers marked with"
                            + " @sensitive must not return "
                            + "the raw value from toString().")
                    .inScope();

            bb.invoke("assertEquals")
                    .withArgumentFromInvoking("length")
                    .on("origStringValue")
                    .withArgumentFromInvoking("length")
                    .on("stringValue")
                    .withStringLiteral("Obfuscated string should "
                            + "have the same length")
                    .inScope();

            bb.declare("first")
                    .initializedByInvoking("charAt")
                    .withArgument(0)
                    .on("origStringValue")
                    .as("char");

            bb.declare("last")
                    .initializedByInvoking("charAt")
                    .withArgument("origStringValue.length() - 1")
                    .on("origStringValue")
                    .as("char");

            bb.declare("obfuscatedFirst")
                    .initializedByInvoking("charAt")
                    .withArgument(0)
                    .on("stringValue")
                    .as("char");

            bb.declare("obfuscatedLast")
                    .initializedByInvoking("charAt")
                    .withArgument("stringValue.length() - 1")
                    .on("stringValue")
                    .as("char");

            assertEquals("first", "obfuscatedFirst", "Initial characters should match.", bb);
            assertEquals("last", "obfuscatedLast", "Initial characters should match.", bb);
        });
    }

    private void generateInvalidNumberTests() {
        Int ct = Int.create();
        switch (shape.getType()) {
            case INTEGER:
                super.withOutOfRangeInts(shape, inv -> {
                    expectFailMethod(ct.increment(), inv, nb -> {
                        nb.withArgument(inv);
                    });
                });
                break;
            case LONG:
                super.withOutOfRangeLongs(shape, inv -> {
                    expectFailMethod(ct.increment(), inv, nb -> {
                        nb.withArgument(inv);
                    });
                });
                break;
            case SHORT:
                super.withOutOfRangeShorts(shape, inv -> {
                    expectFailMethod(ct.increment(), inv, nb -> {
                        nb.withArgument(inv);
                    });
                });
                break;
            case BYTE:
                super.withOutOfRangeBytes(shape, inv -> {
                    expectFailMethod(ct.increment(), inv, nb -> {
                        nb.withArgument(inv);
                    });
                });
                break;
            case FLOAT:
                super.withOutOfRangeFloats(shape, inv -> {
                    expectFailMethod(ct.increment(), inv, nb -> {
                        nb.withArgument(inv);
                    });
                });
                break;
            case DOUBLE:
                super.withOutOfRangeDoubles(shape, inv -> {
                    expectFailMethod(ct.increment(), inv, nb -> {
                        nb.withArgument(inv);
                    });
                });
                break;
        }
    }

    private void expectFailMethod(int index, Number num, Consumer<NewBuilder<?>> c) {
        String mname = "Invalid" + index + "_"
                + escape(num.toString(), JAVA_IDENTIFIER_CAMEL_CASE);
        testMethod(mname, currentClassBuilder, bb -> {
            bb.trying((TryBuilder<?> tri) -> {
                tri.declare("invalid")
                        .initializedWithNew((NewBuilder<?> nb) -> {
                            c.accept(nb);
                            nb.ofType(currentTypeName);
                        }).as(currentTypeName);
                currentClassBuilder.importing(validationExceptions().fqn());
                tri.invoke("fail")
                        .withStringLiteral(num + " is an out-of-range value for "
                                + shape.getId() + " - an exception should have been thrown "
                                + "by " + currentTypeName)
                        .inScope();
                tri.catching(cat -> {
                    cat.lineComment("Exception is expected");
                }, validationExceptions().name());
            });
        });
    }

    private void generateInvalidValueTest(String val) {
        String elided = val.isEmpty() ? "EmptyString" : escape(val, JAVA_IDENTIFIER_CAMEL_CASE);
        testMethod("Invalid_" + elided, currentClassBuilder, bb -> {
            bb.debugLog("Equality and hash code test of " + currentTypeName + " in " + currentClassBuilder.className()
                    + " with '" + val + "'");

            bb.trying(tri -> {
                tri.declare("shouldThrow")
                        .initializedWithNew(nb -> {
                            nb.withStringLiteral(val)
                                    .ofType(currentTypeName);
                        }).as(currentTypeName);
                tri.invoke("fail")
                        .withStringConcatentationArgument("Exception should have "
                                + " been thrown attempting to create an instance of ")
                        .append(
                                currentTypeName)
                        .append(" with '")
                        .append(val)
                        .append("', which is an invalid "
                                + "value according to the @samples trait in its schema")
                        .endConcatenation().inScope();
                currentClassBuilder.importing(validationExceptions().fqn());
                tri.catching(cat -> {
                    cat.lineComment("Exception is expected - do nothing.");
                }, validationExceptions().name());
            });
        });
    }

    private void generateGetterTest() {
        testMethod("Getter", currentClassBuilder, bb -> {
            bb.debugLog("Getter test of " + currentTypeName + " in " + currentClassBuilder.className());
            InstanceInfo info = declareInstance(bb);
            String method = getterMethodForWrapper(shape);
            bb.declare("value").
                    initializedByInvoking(method)
                    .on(info.instanceVar)
                    .as(underlyingType(shape));
            assertEquals(info.contents, "value", "Getter return value does not match", bb);
        });
    }

    private void generateBooleanFactoryMethodTest() {
        testMethod("BooleanFactoryMethod", currentClassBuilder, bb -> {
            bb.debugLog("Boolean factory method test of " + currentTypeName + " in " + currentClassBuilder.className());
            String mn = decapitalize(currentTypeName);
            bb.declare("trueValue1")
                    .initializedByInvoking(mn)
                    .withArgument(true)
                    .on(currentTypeName)
                    .as(currentTypeName);
            bb.declare("trueValue2")
                    .initializedByInvoking(mn)
                    .withArgument(true)
                    .on(currentTypeName)
                    .as(currentTypeName);

            bb.declare("falseValue1")
                    .initializedByInvoking(mn)
                    .withArgument(false)
                    .on(currentTypeName)
                    .as(currentTypeName);

            bb.declare("falseValue2")
                    .initializedByInvoking(mn)
                    .withArgument(false)
                    .on(currentTypeName)
                    .as(currentTypeName);

            bb.invoke("assertSame")
                    .withArgument("trueValue1")
                    .withArgument("trueValue2")
                    .inScope();

            bb.invoke("assertSame")
                    .withArgument("falseValue1")
                    .withArgument("falseValue2")
                    .inScope();

            bb.invoke("assertNotSame")
                    .withArgument("trueValue1")
                    .withArgument("falseValue2")
                    .inScope();

            bb.invoke("assertEquals")
                    .withArgument("trueValue1")
                    .withArgument("trueValue2")
                    .withStringLiteral("Same value does not equals() itself")
                    .inScope();

            bb.invoke("assertNotEquals")
                    .withArgument("trueValue1")
                    .withArgument("falseValue1")
                    .withStringLiteral("True and false values equals() each other")
                    .inScope();

            bb.invoke("assertNotEquals")
                    .withArgumentFromInvoking("hashCode").on("trueValue1")
                    .withArgumentFromInvoking("hashCode").on("falseValue1")
                    .withStringLiteral("True and false values have the same hash code")
                    .inScope();
        });
    }

    private void generateJavaSerializationTest() {
        testMethod("JavaSerialization", currentClassBuilder, bb -> {
            bb.debugLog("Java serialization test of " + currentTypeName + " in " + currentClassBuilder.className());
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

            InstanceInfo info = declareInstance(currentTypeName, bb);

            bb.invoke("writeObject")
                    .withArgument(info.instanceVar)
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

            assertEquals(info.instanceVar, "reconstituted",
                    "Deserialized instance is not equal to original", bb);

        });
    }

    private void generateJsonSerializationTest() {
        testMethod("JsonSerialization", currentClassBuilder, bb -> {
            bb.debugLog("JSON serialization test of " + currentTypeName + " in " + currentClassBuilder.className());
            bb.declare("mapper").initializedWithNew(nb -> {
                nb.ofType("ObjectMapper");
            }).as("ObjectMapper");

            if (shape.getType().equals(TIMESTAMP)) {
                currentClassBuilder.importing("com.mastfrog.jackson.configuration.JacksonConfigurer");
                bb.invoke("configureFromMetaInfServices")
                        .withArgument("mapper")
                        .on("JacksonConfigurer");
            }

            InstanceInfo info = declareInstance(currentTypeName, bb);

            bb.declare("stringRepresentation")
                    .initializedByInvoking("writeValueAsString")
                    .withArgument(info.instanceVar)
                    .on("mapper")
                    .asString();

            bb.declare("reconstituted")
                    .initializedByInvoking("readValue")
                    .withArgument("stringRepresentation")
                    .withClassArgument(currentTypeName)
                    .on("mapper")
                    .as(currentTypeName);

            assertEquals(info.instanceVar, "reconstituted",
                    "Deserialized instance is not equal to original", bb);

        });
    }

    private void generateEqualityTest() {
        testMethod("Equals", currentClassBuilder, bb -> {
            bb.debugLog("Equality and hash code test of " + currentTypeName + " in " + currentClassBuilder.className());
            InstanceInfo info = declareInstance(currentTypeName, bb);

            assertEquals(info.instanceVar, info.instanceVar,
                    "Instance does not equals() itself!", bb);

            String two = declareInstanceWithArg(info.contents, bb);
            assertEquals(info.instanceVar, two, "Instances with same source value should be equal", bb);
            bb.declare("hc1").initializedByInvoking("hashCode").on(info.instanceVar)
                    .as("int");
            bb.declare("hc2").initializedByInvoking("hashCode").on(two)
                    .as("int");
            assertEquals("hc1", "hc2", "Instances are equal but do not have the same hash code", bb);

            bb.declare("str1").initializedByInvoking("toString").on(info.instanceVar).asString();
            bb.declare("str2").initializedByInvoking("toString").on(two).asString();

            assertEquals("str1", "str2", "Instances and hash codes are equal, but string representation does not match", bb);

            bb.invoke("assertTrue")
                    .withArgument(variable(info.instanceVar).isInstance("Comparable"));
        });
    }

    protected <B extends BlockBuilderBase<T, B, ?>, T> String declareInstanceWithArg(String arg, B bb) {
        String nv = newVarName("arg" + capitalize(arg));
        bb.declare(nv)
                .initializedWithNew(nb -> {
                    nb.withArgument(arg)
                            .ofType(currentTypeName);
                }).as(currentTypeName);
        return nv;
    }

    protected String getterMethodForWrapper(Shape wrapper) {
        if (wrapper.getType() == MEMBER) {
            wrapper = model.expectShape(wrapper.asMemberShape().get().getTarget());
        }
        switch (wrapper.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case TIMESTAMP:
            case ENUM:
            case STRING:
            case LIST:
            case SET:
            case MAP:
                return "get";
            case BOOLEAN:
            case DOUBLE:
            case LONG:
            case SHORT:
            case BYTE:
            case FLOAT:
                return "getAs" + capitalize(wrapper.getType().name().toLowerCase());
            case INTEGER:
                return "getAsInt";
            case INT_ENUM:
                return "intValue";
            default:
                throw new AssertionError("No getter in a " + wrapper.getType());
        }
    }

}
