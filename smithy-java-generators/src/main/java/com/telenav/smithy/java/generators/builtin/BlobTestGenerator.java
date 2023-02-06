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
import com.telenav.smithy.java.generators.builtin.BlobModelGenerator.BlobEncodings;
import com.telenav.smithy.validation.ValidationExceptionProvider;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.traits.LengthTrait;

/**
 *
 * @author Tim Boudreau
 */
public class BlobTestGenerator extends AbstractJavaTestGenerator<BlobShape> {

    private BlobEncodings enc;

    public BlobTestGenerator(BlobShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    private BlobEncodings enc() {
        return enc != null ? enc : (enc = BlobModelGenerator.encodingFor(shape, null, model));
    }

    @Override
    protected void generate(ClassBuilder<String> cb, String typeName) {
        if (enc().isRaw()) {
            generateRawTest(cb, typeName);
        } else if (enc().isBase64()) {
            generateBase64Test(cb, typeName);
        } else {
            generateHexTest(cb, typeName);
        }
        generateJsonSerializationTest(cb, typeName);
        generateToStringDeserializesAsJsonTest(cb, typeName);
        generateConstructorDoesNotAcceptNullTest(cb, typeName);
        if (shape.getTrait(LengthTrait.class).isPresent()) {
            generateInvalidInstanceTest(cb, typeName);
            if (!enc.isRaw()) {
                generateSecondaryConstructorInvalidInstanceTest(cb, typeName);
            }
        }
        generateJavaSerializationTest(cb, typeName);
        if (!enc().isRaw()) {
            generateInvalidCharactersTest(cb, typeName);
        }
        cb.importing(validationExceptions().fqn());
    }

    private void generateInvalidCharactersTest(ClassBuilder<String> cb, String typeName) {
        byte[] bytes = randomBytesForBlob(shape, null);
        if (bytes.length == 0) {
            return;
        }
        String str = enc().encodeBytes(bytes);
        char[] nue = str.toCharArray();
        for (int i = 0; i < nue.length; i++) {
            nue[i] += '\u0410';
        }
        String invalidString = new String(nue);
        super.testMethod("InvalidCharactersInInput", cb, bb -> {
            bb.declare("invalidInput").initializedWithStringLiteral(invalidString).as("String");
            bb.trying(tri -> {
                tri.declare("item").initializedWithNew(nb -> {
                    nb.withArgument("invalidInput")
                            .ofType(typeName);
                }).as(typeName);
                tri.invoke("fail")
                        .withStringConcatentationArgument("Constructor passed text with invalid characters in "
                                + enc() + " encoding should have thrown ")
                        .append(ValidationExceptionProvider.validationExceptions().name())
                        .append(" but instead created an instance: ")
                        .appendExpression("item")
                        .endConcatenation().inScope();
                tri.catching(cat -> {
                    cat.lineComment("Success - do nothing.");
                }, validationExceptions().name());
            });
        });
    }

    private void generateJavaSerializationTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("javaSerialization", cb, bb -> {
            bb.debugLog("Test java serialization for " + typeName);
            InstanceInfo info = declareInstance("instance", shape, bb, typeName);
            cb.importing(ObjectInputStream.class, ObjectOutputStream.class, ByteArrayOutputStream.class, ByteArrayInputStream.class);
            bb.declare("bytesOut")
                    .initializedWithNew(nb -> nb.ofType("ByteArrayOutputStream"))
                    .as("ByteArrayOutputStream");
            bb.declare("objectOut")
                    .initializedWithNew(nb -> nb.withArgument("bytesOut").ofType("ObjectOutputStream"))
                    .as("ObjectOutputStream");
            bb.invoke("writeObject")
                    .withArgument(info.instanceVar)
                    .on("objectOut");

            bb.invoke("close").on("objectOut");

            bb.declare("bytesIn")
                    .initializedWithNew(nb -> nb.withArgumentFromInvoking("toByteArray").on("bytesOut")
                    .ofType("ByteArrayInputStream"))
                    .as("ByteArrayInputStream");
            bb.declare("objectIn")
                    .initializedWithNew(nb -> nb.withArgument("bytesIn").ofType("ObjectInputStream"))
                    .as("ObjectInputStream");

            bb.declare("deserialized")
                    .initializedWithCastTo(typeName)
                    .ofInvocationOf("readObject")
                    .on("objectIn")
                    .as(typeName);

            assertEquals(info.instanceVar, "deserialized", "Java-deserialized instance was not equal", bb);
        });
    }

    private void generateSecondaryConstructorInvalidInstanceTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("invalidLengthInstanceCannotBeConstructedViaByteArrayConstructor", cb, bb -> {
            Optional<byte[]> bytesOpt = randomInvalidBytesForBlob(shape, null);
            if (!bytesOpt.isPresent()) {
                bb.lineComment("The length constraints do not allow for practical creation")
                        .lineComment("of an invalid instance for min " + minFor(shape, null)
                                + " max " + maxFor(shape, null));
                return;
            }
            byte[] bytes = bytesOpt.get();
            BlobEncodings enc = enc();
            bb.trying(tri -> {
                declareByteArray("invalidBytes", bytes, bb);
                tri.declare("item").initializedWithNew(nb -> {
                    nb.withArgument("invalidBytes")
                            .ofType(typeName);
                }).as(typeName);
                tri.invoke("fail")
                        .withStringConcatentationArgument("Constructor passed null "
                                + "should have thrown ")
                        .append(ValidationExceptionProvider.validationExceptions().name())
                        .append(" but instead created an instance: ")
                        .appendExpression("item")
                        .endConcatenation().inScope();
                tri.catching(cat -> {
                    cat.lineComment("Success - do nothing.");
                }, validationExceptions().name());
            });
        });
    }

    private void generateInvalidInstanceTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("invalidLengthInstanceCannotBeConstructed", cb, bb -> {
            Optional<byte[]> bytesOpt = randomInvalidBytesForBlob(shape, null);
            if (!bytesOpt.isPresent()) {
                bb.lineComment("The length constraints do not allow for practical creation")
                        .lineComment("of an invalid instance for min " + minFor(shape, null)
                                + " max " + maxFor(shape, null));
                return;
            }
            byte[] bytes = bytesOpt.get();
            BlobEncodings enc = enc();
            bb.trying(tri -> {
                tri.lineComment("Array of " + bytes.length + " is not in the")
                        .lineComment("range " + minFor(shape, null) + " to " + maxFor(shape, null)
                                + ", inclusive, so this should throw.");
                if (enc.isRaw()) {
                    declareByteArray("invalidBytes", bytes, bb);
                    tri.declare("item").initializedWithNew(nb -> {
                        nb.withArgument("invalidBytes")
                                .ofType(typeName);
                    }).as(typeName);
                } else {
                    tri.blankLine().lineComment("Encoded with " + enc);
                    String encoded = enc.encodeBytes(bytes);
                    tri.declare("item").initializedWithNew(nb -> {
                        nb.withStringLiteral(encoded)
                                .ofType(typeName);
                    }).as(typeName);
                }
                tri.invoke("fail")
                        .withStringConcatentationArgument("Constructor passed null should have thrown ")
                        .append(ValidationExceptionProvider.validationExceptions().name())
                        .append(" but instead created an instance: ")
                        .appendExpression("item")
                        .endConcatenation().inScope();

                tri.catching(cat -> {
                    cat.lineComment("Success - do nothing.");
                }, validationExceptions().name());
            });
        });
    }

    private void generateConstructorDoesNotAcceptNullTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("ConstructorDoesNotAcceptNull", cb, bb -> {
            String cast = enc.isRaw() ? "(byte[])" : "(String)";
            bb.trying(tri -> {
                tri.declare("item").initializedWithNew(nb -> {
                    nb.withArgument(cast + " null")
                            .ofType(typeName);
                }).as(typeName);
                tri.invoke("fail")
                        .withStringConcatentationArgument("Constructor passed null should have thrown ")
                        .append(ValidationExceptionProvider.validationExceptions().name())
                        .append(" but instead created an instance: ")
                        .append("item")
                        .endConcatenation().inScope();
                tri.catching(cat -> {
                    cat.lineComment("Success - do nothing");
                }, validationExceptions().name());
            });
        });
        if (!enc.isRaw()) {
            super.testMethod("SecondaryConstructorDoesNotAcceptNull", cb, bb -> {
                String cast = "(byte[])";
                bb.trying(tri -> {
                    tri.declare("item").initializedWithNew(nb -> {
                        nb.withArgument(cast + " null")
                                .ofType(typeName);
                    }).as(typeName);
                    tri.invoke("fail")
                            .withStringConcatentationArgument("Constructor passed null should have thrown ")
                            .append(ValidationExceptionProvider.validationExceptions().name())
                            .append(" but instead created an instance: ")
                            .append("item")
                            .endConcatenation().inScope();
                    tri.catching(cat -> {
                        cat.lineComment("Success - do nothing");
                    }, validationExceptions().name());
                });
            });

        }
    }

    private void generateToStringDeserializesAsJsonTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("ToStringIsValidJson", cb, bb -> {
            bb.blankLine().lineComment("The value of toString() should be deserializable as JSON to an identical instance.");
            bb.declare("mapper")
                    .initializedWithNew(nb -> nb.ofType("ObjectMapper"))
                    .as("ObjectMapper");
            currentClassBuilder.importing("com.mastfrog.jackson.configuration.JacksonConfigurer");
            bb.invoke("configureFromMetaInfServices")
                    .withArgument("mapper")
                    .on("JacksonConfigurer");
            InstanceInfo info = declareInstance(typeName, shape, bb, typeName);

            bb.declare("nue").initializedByInvoking("readValue")
                    .withArgumentFromInvoking("toString")
                    .on(info.instanceVar)
                    .withClassArgument(typeName)
                    .on("mapper")
                    .as(typeName);

            assertEquals(info.instanceVar, "nue", "Deserialized from toString is not equal", bb);
        });
    }

    private void generateJsonSerializationTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("JsonSerialization", cb, bb -> {

            bb.blankLine().lineComment("Test JSON serialization");
            bb.declare("mapper")
                    .initializedWithNew(nb -> nb.ofType("ObjectMapper"))
                    .as("ObjectMapper");

            bb.lineComment("Needed for Instant deserialization");
            currentClassBuilder.importing("com.mastfrog.jackson.configuration.JacksonConfigurer");
            bb.invoke("configureFromMetaInfServices")
                    .withArgument("mapper")
                    .on("JacksonConfigurer");
            InstanceInfo info = declareInstance(typeName, shape, bb, typeName);

            bb.declare("jsonString")
                    .initializedByInvoking("writeValueAsString")
                    .withArgument(info.instanceVar)
                    .on("mapper")
                    .as("String");

            bb.invoke("assertNotNull").withArgument("jsonString").inScope();
            bb.invoke("assertFalse").withArgumentFromInvoking("isEmpty").on("jsonString").inScope();

            bb.declare("deserialized")
                    .initializedByInvoking("readValue")
                    .withArgument("jsonString")
                    .withClassArgument(typeName)
                    .on("mapper")
                    .as(typeName);

            assertEquals(info.instanceVar, "deserialized", "Deserialized instance not equal", bb);

        });
    }

    private void generateRawTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("RawValue", cb, bb -> {
            byte[] bytes = someRandomBytes();
            declareByteArray("bytes", bytes, bb);
            bb.declare("instance").initializedWithNew(nb -> {
                nb.withArgument("bytes")
                        .ofType(typeName);
            }).as(typeName);

            bb.declare("instance2").initializedWithNew(nb -> {
                nb.withArgument("bytes")
                        .ofType(typeName);
            }).as(typeName);

            bb.invoke("assertEquals")
                    .withArgument("instance")
                    .withArgument("instance2")
                    .withStringLiteral("Instances initialized with the same argument are not equal")
                    .inScope();

            bb.invoke("assertEquals")
                    .withArgument("instance.toString()")
                    .withArgument("instance2.toString()")
                    .withStringLiteral("Instances initialized with the same argument do not have the same toString()")
                    .inScope();

            bb.invoke("assertEquals")
                    .withArgument("instance.hashCode()")
                    .withArgument("instance2.hashCode()")
                    .withStringLiteral("Instances initialized with the same argument do not have the same hash code")
                    .inScope();

            byte[] more = Arrays.copyOf(bytes, bytes.length);
            for (int i = 0; i < more.length; i++) {
                if (i % 3 == 0) {
                    byte[] b = new byte[1];
                    rnd.nextBytes(b);
                    more[i] = b[0];
                } else {
                    more[i] = (byte) (~(more[i] + 1) & 0xFF);
                }
            }
            if (!Arrays.equals(more, bytes)) { // if we are VERY unlucky
                declareByteArray("arr2", more, bb);
                bb.declare("instance3").initializedWithNew(nb -> {
                    nb.withArgument("arr2")
                            .ofType(typeName);
                }).as(typeName);
                bb.invoke("assertNotEquals")
                        .withArgument("instance")
                        .withArgument("instance3")
                        .withStringLiteral("Unequal instances test as equal")
                        .inScope();
            }
            String b64 = BlobEncodings.BASE_64.encodeBytes(bytes);
            bb.invoke("assertEquals")
                    .withStringLiteral(b64)
                    .withArgumentFromInvoking("stringValue").on("instance")
                    .withStringLiteral("stringValue() of a type-raw blob should be the default base 64 encoding of it")
                    .inScope();
            bb.invoke("assertEquals")
                    .withStringConcatentationArgument("\"", sc -> sc.append(b64).append(('"')))
                    .withArgumentFromInvoking("toString").on("instance")
                    .withStringLiteral("stringValue() of a type-raw blob should be the default base 64 encoding of it")
                    .inScope();
        });
    }

    private void generateBase64Test(ClassBuilder<String> cb, String typeName) {
        super.testMethod("Base64conversion", cb, bb -> {
            byte[] bytes = someRandomBytes();
            declareByteArray("expected", bytes, bb);
            BlobTestGenerator.declareEncodedBlobInput(enc(), "encoded", bytes, bb);

            bb.declare("instance").initializedWithNew(nb -> {
                nb.withArgument("encoded")
                        .ofType(typeName);
            }).as(typeName);

            super.assertEquals("encoded", "instance.stringValue()", "toString() should return original constructor input text", bb);

            bb.lineComment("Array of " + bytes.length);
            bb.lineComment("Min " + minFor(shape, null));
            bb.lineComment("Max " + minFor(shape, null));

            bb.declare("asBytes").initializedByInvoking("get").on("instance").as("byte[]");

            bb.invoke("assertNotNull").withArgument("asBytes")
                    .withStringLiteral("byte array getter should not return null").inScope();

            bb.invoke("assertArrayEquals")
                    .withArgument("expected")
                    .withArgument("asBytes")
                    .withStringLiteral("Decoded bytes do not match")
                    .inScope();
        });
    }

    private void generateHexTest(ClassBuilder<String> cb, String typeName) {
        super.testMethod("HexConversion", cb, bb -> {
            byte[] bytes = someRandomBytes();
            declareByteArray("expected", bytes, bb);
            BlobTestGenerator.declareEncodedBlobInput(enc(), "encoded", bytes, bb);

            bb.declare("instance").initializedWithNew(nb -> {
                nb.withArgument("encoded")
                        .ofType(typeName);
            }).as(typeName);

            super.assertEquals("encoded", "instance.stringValue()", "toString() should return original constructor input text", bb);

            bb.invoke("assertEquals")
                    .withStringConcatentationArgument("\"", cat -> cat.appendExpression("encoded").append('"'))
                    .withArgumentFromInvoking("toString").on("instance")
                    .withStringLiteral("toString() should return the encoded value wrapped in double quotes to return valid JSON")
                    .inScope();

            bb.declare("asBytes").initializedByInvoking("get").on("instance").as("byte[]");

            bb.invoke("assertNotNull").withArgument("asBytes")
                    .withStringLiteral("byte array getter should not return null").inScope();

            bb.invoke("assertArrayEquals")
                    .withArgument("expected")
                    .withArgument("asBytes")
                    .withStringLiteral("Decoded bytes do not match")
                    .inScope();

            bb.declare("inst2")
                    .initializedWithNew(nb -> nb.withArgument("expected").ofType(typeName))
                    .as(typeName);
            bb.invoke("assertEquals")
                    .withArgument("instance")
                    .withArgument("inst2")
                    .withStringLiteral("Instance created from byte array that decodes to the same content as passed as encoded should be equals()");

            bb.invoke("assertEquals")
                    .withArgument("instance.hashCode()")
                    .withArgument("inst2.hashCode()")
                    .withStringLiteral("Instance created from byte array that decodes to the same content as passed as encoded should have same hashCode()");
        });
    }

    protected <B extends BlockBuilderBase<T, B, X>, T, X> void declareEncodedBlobInput(String vn, byte[] bytes, B bb) {
        declareEncodedBlobInput(enc(), vn, bytes, bb);
    }

    private byte[] someRandomBytes() {
        int min = Math.max(0, min());
        int max = max();
        if (max == min) {
            byte[] b = new byte[max];
            rnd.nextBytes(b);
            return b;
        }
        int range = max - min;
        if (range > 256) {
            range = 16;
        }
        int targetSize = min + rnd.nextInt(range);
        
//        int realMax = Math.max(min + 12, Math.min(max, min + 12));
//        int range = realMax - min;
//        int targetSize = min + rnd.nextInt(range);
        byte[] b = new byte[targetSize];
        rnd.nextBytes(b);
        return b;
    }

    private int min() {
        return shape.getTrait(LengthTrait.class).flatMap(len -> {
            return len.getMin().map(min -> min.intValue());
        }).orElse(2);
    }

    private int max() {
        return shape.getTrait(LengthTrait.class).flatMap(len -> {
            return len.getMax().map(min -> min.intValue());
        }).orElse(32);
    }
}
