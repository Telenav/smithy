/*
 * Copyright 2023 Mastfrog Technologies.
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
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.extensions.BlobEncodingTrait;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 *
 * @author Tim Boudreau
 */
public final class BlobModelGenerator extends AbstractJavaGenerator<BlobShape> {

    private static final String ENCODED_VALUE_FIELD = "encodedValue";
    private static final String BYTE_VALUE_FIELD = "bytes";
    private static final String HEX_PATTERN_STATIC_FIELD = "HEX_PATTERN";
    private static final String BASE64_PATTERN_STATIC_FIELD = "BASE64_PATTERN";
    private static final String ENCODE_METHOD = "encode";
    private static final String DECODE_METHOD = "decode";
    private BlobEncodings encoding;

    BlobModelGenerator(BlobShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    private static Set<MemberShape> usages(Model model, BlobShape shape) {
        Set<MemberShape> result = new HashSet<>();
        model.getStructureShapes().forEach(struct -> {
            for (Map.Entry<String, MemberShape> e : struct.getAllMembers().entrySet()) {
                if (e.getValue().getTarget().equals(shape.getId())) {
                    result.add(e.getValue());
                }
            }
        });
        return result;
    }

    static <T extends Trait> boolean hasTrait(Set<MemberShape> shapes, Class<T> trait, Model model) {
        for (MemberShape ms : shapes) {
            if (ms.getMemberTrait(model, trait).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Trait> Optional<T> traitFor(BlobShape shape, MemberShape mem, Model model, Class<T> trait) {
        if (mem != null) {
            return mem.getMemberTrait(model, trait);
        } else {
            return shape.getTrait(trait);
        }
    }

    public static BlobEncodings encodingFor(BlobShape shape, MemberShape mem, Model model) {
        return traitFor(shape, mem, model, BlobEncodingTrait.class).map(enc -> {
            return BlobEncodings.find(enc.getValue());
        }).orElseGet(() -> {
            Set<MemberShape> memberUsages = usages(model, shape);
            if (hasTrait(memberUsages, HttpHeaderTrait.class, model)) {
                return BlobEncodings.HEX_UPPER_OR_LOWER_CASE;
            }
            return BlobEncodings.RAW;
        });
    }

    private BlobEncodings encoding() {
        if (encoding != null) {
            return encoding;
        }
        return encoding = encodingFor(shape, null, model);
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder result = new StringBuilder().append("This is a <code>Blob</code> "
                + "shape representing a byte array, using <code>")
                .append(encoding())
                .append("</code> encoding.  Use the <code>Supplier.get()</code> method to "
                        + "get the raw value as a byte array, or <code>toInputStream()</code> "
                        + "or <code>toByteBuffer()</code> to get the decoded value.");
        shape.getTrait(LengthTrait.class).ifPresent(len -> {
            result.append("\n<h3>Length Constraints</h3>\n<ul>");
            len.getMin().ifPresent(min -> {
                result.append("\n    <li>The <b>minimum length in bytes</b> is <code>").append(min).append("</code>");
            });
            len.getMax().ifPresent(max -> {
                result.append("\n    <li>The <b>maximum length in bytes</b> is <code>").append(max).append("</code>");
            });
            result.append("\n</ul>");
        });
        return result.toString();
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = super.classHead()
                .importing(Supplier.class)
                .implementing("Supplier<byte[]>");
        applyDocumentation(cb);
        generateConstructor(cb);
        generateStringValueMethod(cb);
        generateSupplierMethod(cb);
        generateToByteBufferMethod(cb);
        generateToInputStreamMethod(cb);
        generateEquals(cb);
        generateHashCode(cb);
        addTo.accept(cb);
    }

    @Override
    protected <T, R, B extends BlockBuilderBase<R, B, ?>> void generateEqualsComparison(
            String other, ClassBuilder<?> cb, B bb) {
        BlobEncodings enc = encoding();
        if (enc.isRaw()) {
            cb.importing(Arrays.class);
            bb.iff().booleanExpression("!Arrays.equals(this." + BYTE_VALUE_FIELD
                    + ", other." + BYTE_VALUE_FIELD + ")")
                    .returning(false).endIf();
        } else {
            bb.iff().booleanExpression("!this." + ENCODED_VALUE_FIELD
                    + ".equals(other." + ENCODED_VALUE_FIELD + ")").returning(false).endIf();
        }
        bb.returning(true);
    }

    @Override
    protected <T, R> void generateHashCodeComputation(ClassBuilder<T> cb, BlockBuilder<R> bb) {
        BlobEncodings enc = encoding();
        if (enc.isRaw()) {
            cb.importing(Arrays.class);
            bb.returningInvocationOf("hashCode").withArgument(BYTE_VALUE_FIELD).on("Arrays");
        } else {
            bb.returningInvocationOf("hashCode").onField(ENCODED_VALUE_FIELD).ofThis();
        }
    }

    private void generateConstructor(ClassBuilder<String> cb) {
        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator"
        );
        BlobEncodings enc = encoding();
        if (enc.isRaw()) {
            cb.field(BYTE_VALUE_FIELD).withModifier(PRIVATE, FINAL)
                    .ofType("byte[]");
            cb.constructor(con -> {
                con.setModifier(PUBLIC).annotatedWith("JsonCreator").closeAnnotation()
                        .addArgument("byte[]", BYTE_VALUE_FIELD);
                con.body(bb -> {
                    validationExceptions().createNullCheck(BYTE_VALUE_FIELD, cb, bb);
                    shape.getTrait(LengthTrait.class).ifPresent(len -> {
                        len.getMin().ifPresent(min -> {
                            int imin = min.intValue();
                            if (imin > 0) {
                                IfBuilder<?> minTest = bb.iff(variable(BYTE_VALUE_FIELD + ".length").isLessThan(number(imin)));
                                validationExceptions()
                                        .createThrow(cb, minTest, "Length of a "
                                                + cb.className() + " must be >= " + imin
                                                + " but got ", BYTE_VALUE_FIELD + ".length");
                                minTest.endIf();
                            }
                        });
                        len.getMax().ifPresent(max -> {
                            int imax = max.intValue();
                            if (max < Integer.MAX_VALUE) {
                                IfBuilder<?> minTest = bb.iff(variable(BYTE_VALUE_FIELD
                                        + ".length").isGreaterThan(number(imax)));
                                validationExceptions()
                                        .createThrow(cb, minTest, "Length of a "
                                                + cb.className() + " must be <= " + imax
                                                + " but got ", BYTE_VALUE_FIELD + ".length");
                                minTest.endIf();
                            }
                        });
                    });
                    bb.statement("this." + BYTE_VALUE_FIELD + " = " + BYTE_VALUE_FIELD);
                });
            });
        } else {
            cb.field(ENCODED_VALUE_FIELD).withModifier(PRIVATE, FINAL)
                    .ofType("String");
            cb.constructor(con -> {
                con.setModifier(PUBLIC).annotatedWith("JsonCreator").closeAnnotation()
                        .addArgument("String", ENCODED_VALUE_FIELD);
                con.body(bb -> {
                    validationExceptions().createNullCheck(ENCODED_VALUE_FIELD, cb, bb);
                    encoding().generateStringValidation(shape, ENCODED_VALUE_FIELD, bb, cb);
                    bb.assignField(ENCODED_VALUE_FIELD).ofThis().toExpression(ENCODED_VALUE_FIELD);
                });
            });
            cb.constructor(con -> {
                con.setModifier(PUBLIC)
                        .docComment("Convenience constructor which takes a byte array")
                        .addArgument("byte[]", "bytes");
                con.body(bb -> {
                    validationExceptions().createNullCheck("bytes", cb, bb);
                    shape.getTrait(LengthTrait.class)
                            .ifPresent(len -> {
                                len.getMin().ifPresent(min -> {
                                    if (min > 0 && min < Integer.MAX_VALUE) {
                                        IfBuilder<?> test = bb.iff().booleanExpression(
                                                "bytes.length < " + min);
                                        validationExceptions().createThrow(cb, test,
                                                "Minimum length is " + min, null);
                                        test.endIf();
                                    }
                                });
                                len.getMax().ifPresent(max -> {
                                    if (max > 0 && max < Integer.MAX_VALUE) {
                                        IfBuilder<?> test = bb.iff().booleanExpression(
                                                "bytes.length > " + max);
                                        validationExceptions().createThrow(cb, test,
                                                "Maximum length is " + max, null);
                                        test.endIf();
                                    }
                                });
                            });
                    bb.assignField(ENCODED_VALUE_FIELD)
                            .ofThis()
                            .toInvocation(enc.encodeMethod(cb))
                            .withArgument("bytes")
                            .on(cb.className());
                });
            });
        }
    }

    private void generateStringValueMethod(ClassBuilder<String> cb) {
        BlobEncodings enc = encoding();
        if (enc.isRaw()) {
            String mth = BlobEncodings.BASE_64.encodeMethod(cb);
            cb.method("stringValue").withModifier(PUBLIC)
                    .docComment("Returns the un-decoded " + encoding() + " string value."
                            + "\n@return the encoded input value")
                    .returning("String")
                    .body(bb -> {
                        bb.returningInvocationOf(mth)
                                .withArgument(BYTE_VALUE_FIELD).on(cb.className());
                    });
        } else {
            MethodBuilder<ClassBuilder<String>> mth = cb.method("stringValue").withModifier(PUBLIC)
                    .docComment("Returns the un-decoded " + encoding() + " string value."
                            + "\n@return the encoded input value")
                    .returning("String");
            mth.annotatedWith("JsonValue").closeAnnotation();
            mth.body().returningField(ENCODED_VALUE_FIELD).ofThis()
                    .endBlock();
        }
        cb.overridePublic("toString").returning("String")
                .docComment("Returns the string value, quoted to form valid JSON."
                        + "\n@return the string value, double-quoted")
                .body(bb -> {
                    bb.returningStringConcatenation("\"", concat -> {
                        concat.appendInvocationOf("stringValue").onThis()
                                .append('"');
                    });
                });
    }

    private void generateSupplierMethod(ClassBuilder<String> cb) {
        MethodBuilder<ClassBuilder<String>> mth = cb.overridePublic("get")
                .docComment("Implements <code>Supplier&lt;byte[]&gt;</code> "
                        + "to return the decoded bytes."
                        + "\n@return the decoded bytes")
                .returning("byte[]");
        BlobEncodings enc = encoding();
        if (enc.isRaw()) {
            mth.annotatedWith("JsonValue").closeAnnotation();
        }
        mth.body(bb -> {
            if (enc.isRaw()) {
                cb.importing(Arrays.class);
                bb.returningInvocationOf("copyOf")
                        .withArgument(BYTE_VALUE_FIELD)
                        .withArgumentFromField("length")
                        .of(BYTE_VALUE_FIELD)
                        .on("Arrays");
            } else {
                bb.returningInvocationOf(enc.decodeMethod(cb))
                        .withArgument(ENCODED_VALUE_FIELD)
                        .inScope();
            }
        });
    }

    private void generateToByteBufferMethod(ClassBuilder<String> cb) {
        cb.importing(ByteBuffer.class);
        cb.method("toByteBuffer")
                .docComment("Returns the decoded content as a ByteBuffer."
                        + "\n@return a byte buffer")
                .withModifier(PUBLIC)
                .returning("ByteBuffer")
                .body(bb -> {
                    bb.returningInvocationOf("wrap")
                            .withArgumentFromInvoking("get")
                            .onThis()
                            .on("ByteBuffer");

                });
    }

    private void generateToInputStreamMethod(ClassBuilder<String> cb) {
        cb.importing(ByteArrayInputStream.class, InputStream.class);
        cb.method("toInputStream")
                .docComment("Returns the decoded content as an InputStream."
                        + "\n@return an input stream")
                .withModifier(PUBLIC)
                .returning("InputStream")
                .body(bb -> {
                    bb.returningNew()
                            .withArgumentFromInvoking("get")
                            .onThis()
                            .ofType("ByteArrayInputStream");
                });
    }

    public static enum BlobEncodings {
        HEX_LOWER_CASE,
        HEX_UPPER_CASE,
        HEX_UPPER_OR_LOWER_CASE,
        BASE_64,
        BASE_64_MIME,
        BASE_64_URL,
        RAW;

        public String encodeBytes(byte[] bytes) { // for test generation
            switch (this) {
                case HEX_LOWER_CASE:
                case HEX_UPPER_OR_LOWER_CASE:
                    return Strings.toPaddedHex(bytes).toLowerCase();
                case HEX_UPPER_CASE:
                    return Strings.toPaddedHex(bytes).toUpperCase();
                case BASE_64_MIME:
                    return Base64.getMimeEncoder().encodeToString(bytes);
                case BASE_64_URL:
                    return Base64.getUrlEncoder().encodeToString(bytes);
                case BASE_64:
                default:
                    return Base64.getEncoder().encodeToString(bytes);
            }
        }

        public static BlobEncodings find(String traitValue) {
            for (BlobEncodings b : values()) {
                if (b.name().equals(traitValue) || b.toString().equals(traitValue)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Not a known blob encoding: " + traitValue);
        }

        public boolean isRaw() {
            return this == RAW;
        }

        public boolean isBase64() {
            switch (this) {
                case BASE_64:
                case BASE_64_MIME:
                case BASE_64_URL:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isHex() {
            switch (this) {
                case HEX_LOWER_CASE:
                case HEX_UPPER_CASE:
                case HEX_UPPER_OR_LOWER_CASE:
                    return true;
                default:
                    return false;
            }
        }

        public String base64EncoderMethod() {
            switch (this) {
                case BASE_64:
//                    Base64.getEncoder();
                    return "getEncoder";
                case BASE_64_MIME:
//                    Base64.getMimeEncoder();
                    return "getMimeEncoder";
                case BASE_64_URL:
//                    Base64.getUrlEncoder();
                    return "getUrlEncoder";
                default:
                    throw new AssertionError("Not base-64: " + this);
            }
        }

        public String base64DecoderMethod() {
            switch (this) {
                case BASE_64:
//                    Base64.getEncoder();
                    return "getDecoder";
                case BASE_64_MIME:
//                    Base64.getMimeEncoder();
                    return "getMimeDecoder";
                case BASE_64_URL:
//                    Base64.getUrlEncoder();
                    return "getUrlDecoder";
                default:
                    throw new AssertionError("Not base-64: " + this);
            }
        }

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', '-');
        }

        private String hexPattern(BlobShape shape) {
            String prefix;
            switch (this) {
                case HEX_LOWER_CASE:
                    prefix = "^[0-9a-f]";
                    break;
                case HEX_UPPER_CASE:
                    prefix = "^[0-9A-F]";
                    break;
                case HEX_UPPER_OR_LOWER_CASE:
                    prefix = "^[0-9A-Fa-f]";
                    break;
                default:
                    throw new AssertionError(this);
            }
            return prefix + shape.getTrait(LengthTrait.class).map(len -> {
                StringBuilder suffix = new StringBuilder().append('{');
                len.getMin().ifPresent(min -> {
                    suffix.append(min * 2).append(',');
                });
                len.getMax().ifPresent(max -> {
                    if (suffix.length() == 1) {
                        suffix.append("0,");
                    }
                    if (max < Integer.MAX_VALUE / 2) {
                        suffix.append(max * 2);
                    } else {
                        suffix.append(Integer.MAX_VALUE);
                    }
                });
                return suffix.append("}$").toString();
            }).orElse("*$");
        }

        private <C> void generateEncodeMethod(ClassBuilder<C> cb) {
            if (!cb.containsMethodNamed(ENCODE_METHOD)) {
                cb.method(ENCODE_METHOD)
                        .docComment("Encodes a byte array into " + this + "."
                                + "\n@param bytes the input bytes"
                                + "\n@return the bytes encoded to " + this)
                        .withModifier(PRIVATE, STATIC)
                        .addArgument("byte[]", "bytes")
                        .returning("String")
                        .body(bb -> {
                            generateEncodeMethodBody("bytes", bb, cb);
                        });
            }
        }

        private <C> void generateDecodeMethod(ClassBuilder<C> cb) {
            if (!cb.containsMethodNamed(DECODE_METHOD)) {
                cb.method(DECODE_METHOD)
                        .docComment("Decodes a byte array from a string in " + this + " encoding."
                                + "\n@param input the encoded string input"
                                + "\n@return the decoded bytes")
                        .withModifier(PRIVATE, STATIC)
                        .addArgument("String", "input")
                        .returning("byte[]")
                        .body(bb -> {
                            generateDecodeMethodBody("input", bb, cb);
                        });
            }
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateDecodeMethodBody(
                String varName, B bb, ClassBuilder<C> cb) {
            if (this.isHex()) {
                generateHexDecodeMethodBody(varName, bb, cb);
            } else if (this.isBase64()) {
                generateBase64DecodeMethodBody(varName, bb, cb);
            }
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateHexDecodeMethodBody(
                String varName, B bb, ClassBuilder<C> cb) {
            bb.declare("result")
                    .initializedWith("new byte[" + varName + ".length() / 2]")
                    .as("byte[]");
            bb.declare("len").initializedByInvoking("length").on(varName).as("int");
            bb.forVar("i").initializedWith(0).condition().lessThan(variable("len"))
                    .endCondition().running(lbb -> {
                        lbb.declare("ix").initializedWith("i / 2").as("int");
                        lbb.declare("val")
                                .initializedByInvoking("parseInt")
                                .withArgumentFromInvoking("toUpperCase")
                                .onInvocationOf("substring")
                                .withArgument("i")
                                .withArgument("i + 2")
                                .on(varName)
                                .withArgument(16)
                                .on("Integer")
                                .as("int");
                        lbb.statement("result[ix] = (byte) (val & 0xFF)");
                        lbb.statement("i++");
                    });
            bb.returning("result");
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateEncodeMethodBody(
                String varName, B bb, ClassBuilder<C> cb) {
            if (this.isHex()) {
                generateHexEncodeMethodBody(varName, bb, cb);
            } else if (this.isBase64()) {
                generateBase64EncodeMethodBody(varName, bb, cb);
            }
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateBase64DecodeMethodBody(
                String varName, B bb, ClassBuilder<C> cb) {
            cb.importing(Base64.class);
            bb.declare("decoder")
                    .initializedByInvoking(this.base64DecoderMethod())
                    .on("Base64")
                    .as("Base64.Decoder");
            bb.returningInvocationOf("decode").withArgument(varName).on("decoder");
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateHexEncodeMethodBody(
                String varName, B bb, ClassBuilder<C> cb) {
            bb.declare("sb").initializedWithNew()
                    .withArgument(varName + ".length * 2")
                    .ofType("StringBuilder").as("StringBuilder");

            bb.forVar("i").initializedWith(0).condition().lessThan(variable(varName + ".length"))
                    .endCondition()
                    .running(lbb -> {
                        lbb.declare("curr")
                                .initializedByInvoking("toHexString").withArgument(varName + "[ i ]")
                                .on("Integer")
                                .as("String");
                        lbb.iff().booleanExpression("curr.length() == 1")
                                .invoke("append")
                                .withArgument('0')
                                .on("sb").endIf();
                        lbb.invoke("append").withArgument("curr").on("sb");
                    });
            bb.returningInvocationOf("toString").on("sb");
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateBase64EncodeMethodBody(
                String varName, B bb, ClassBuilder<C> cb) {
            cb.importing(Base64.class);
            bb.declare("encoder")
                    .initializedByInvoking(base64EncoderMethod())
                    .on("Base64")
                    .as("Base64.Encoder");
            bb.returningInvocationOf("encodeToString").withArgument(varName).on("encoder");
        }

        public <B extends BlockBuilderBase<T, B, X>, T, X, C> String encodeMethod(
                ClassBuilder<C> cb) {
            generateEncodeMethod(cb);
            return ENCODE_METHOD;
        }

        public <B extends BlockBuilderBase<T, B, X>, T, X, C> String decodeMethod(
                ClassBuilder<C> cb) {
            generateDecodeMethod(cb);
            return DECODE_METHOD;
        }

        private <C> String lengthDerivationMethod(ClassBuilder<C> cb) {
            String result = "deriveLength";
            if (!cb.containsMethodNamed(result)) {
                cb.method(result).withModifier(PRIVATE, STATIC)
                        .addArgument("String", "stringValue")
                        .docComment("Takes a string value and computes the number of bytes of base-64 it represents"
                                + " by stripping padding and dividing by three, for length checking against shape constraints."
                                + "\n@param stringValue the input being passed to a constructor"
                                + "\n@return the number of bytes represented by the string")
                        .returning("int")
                        .body(bb -> {
                            bb.declare("strip").initializedTo(0);
                            bb.declare("cursor").initializedTo()
                                    .numeric().invoke("length")
                                    .on("stringValue").minus(1)
                                    .endNumericExpression()
                                    .as("int");
                            bb.whileLoop(wh -> {
                                wh.statement("cursor--");
                                wh.statement("strip++");
                                wh.underCondition().booleanExpression("stringValue.charAt(cursor) == '='");
//                                wh.underCondition().invocationOf("charAt")
//                                            .withArgument("cursor")
//                                            .on("stringValue")
//                                            .isEqualTo((int) '=');
                            });
                            decodeMethod(cb);
                            String exp = "(3 * (stringValue.length() / 4)) - strip";
//                            bb.invoke("println")
//                                    .withStringConcatentationArgument("Have working length of ")
//                                    .appendExpression("(" + exp + ")")
//                                    .append(" for '")
//                                    .appendExpression("stringValue")
//                                    .append(" actual length ")
//                                    .appendExpression(DECODE_METHOD + "(stringValue).length")
//                                    .endConcatenation()
//                                    .onField("out").of("System");
                            bb.returning(exp);
                        });
            }
            return result;
        }

        <B extends BlockBuilderBase<T, B, X>, T, X, C> void generateStringValidation(
                BlobShape shape, String varName, B bb, ClassBuilder<C> cb) {
            switch (this) {
                case HEX_UPPER_OR_LOWER_CASE:
                case HEX_LOWER_CASE:
                case HEX_UPPER_CASE: {
                    String hp = hexPattern(shape);
                    if (!cb.containsFieldNamed(HEX_PATTERN_STATIC_FIELD)) {
                        cb.importing(Pattern.class)
                                .field(HEX_PATTERN_STATIC_FIELD)
                                .withModifier(PRIVATE, STATIC, FINAL)
                                .initializedFromInvocationOf("compile")
                                .withStringLiteral(hp)
                                .on("Pattern")
                                .ofType("Pattern");
                    }
                    IfBuilder<B> lengthTest = bb.iff().booleanExpression("(" + varName + ".length() % 2) != 0");
                    validationExceptions().createThrow(cb, lengthTest, "Value cannot be hex - its length is not divisible by 2.", null);
                    lengthTest.endIf();

                    shape.getTrait(LengthTrait.class).ifPresent(len -> {
                        len.getMin().ifPresent(min -> {
                            int imin = min.intValue();
                            if (min > 0) {
                                bb.lineComment("Shape has a minimum length of " + min + " which translates to " + (min * 2) + " hex characters");
                                IfBuilder<B> minTest = bb.iff(invocationOf("length").on(varName).isLessThan(number(min * 2)));
                                validationExceptions().createThrow(cb, minTest, "Minimum length of a " + cb.className() + " is " + imin, null);
                                minTest.endIf();
                            }
                        });
                        len.getMax().ifPresent(max -> {
                            if (max > 0 && max < Integer.MAX_VALUE / 2) { // will overflow to negative
                                bb.lineComment("Shape has a maximum length of " + max + " which translates to " + (max * 2) + " hex characters");
                                IfBuilder<B> maxTest = bb.iff(invocationOf("length").on(varName).isGreaterThan(number(max * 2)));
                                validationExceptions().createThrow(cb, maxTest, "Maximum length of a " + cb.className() + " is " + max, null);
                                maxTest.endIf();
                            }
                        });
                    });

                    String s = "!" + invocationOf("matches").onInvocationOf("matcher").withArgument(varName).onField(HEX_PATTERN_STATIC_FIELD).of(cb.className());
                    IfBuilder<B> patternTest = bb.iff().booleanExpression(s);
                    validationExceptions().createThrow(cb, patternTest, "Value cannot be hex - does not match the pattern `" + hp + "` for " + this + ":\n", varName);
                    patternTest.endIf();
                    break;
                }
                case BASE_64:
                case BASE_64_MIME:
                case BASE_64_URL:
                    if (!cb.containsFieldNamed(BASE64_PATTERN_STATIC_FIELD)) {
                        cb.importing(Pattern.class);
                        String txt = "^[a-zA-Z0-9_=+/-]{3,}$";
                        cb.field(BASE64_PATTERN_STATIC_FIELD)
                                .initializedFromInvocationOf("compile")
                                .withStringLiteral(txt)
                                .on("Pattern")
                                .ofType("Pattern");
                    }
                    shape.getTrait(LengthTrait.class).ifPresent(len -> {
                        String mth = lengthDerivationMethod(cb);
                        bb.declare("actualLength").initializedByInvoking(mth)
                                .withArgument(varName).inScope()
                                .as("int");
                        len.getMin().ifPresent(min -> {
                            IfBuilder<B> test = bb.iff(variable("actualLength").isLessThan(number(min)));
                            validationExceptions().createThrow(cb, test, "Length in bytes is below the minimum, " + min + ": ", "actualLength");
                            test.endIf();
                        });
                        len.getMax().ifPresent(max -> {
                            IfBuilder<B> test = bb.iff(variable("actualLength").isGreaterThan(number(max)));
                            validationExceptions().createThrow(cb, test, "Length in bytes is above the minimum, " + max + ": ", "actualLength");
                            test.endIf();
                        });
                    });
                    String inv;
                    if (this == BASE_64_URL) {
                        cb.importing(URLDecoder.class);
                        cb.importing("static " + StandardCharsets.class.getName() + ".UTF_8");
                        inv = varName + ".length() > 0 && !" + invocationOf("matches")
                                .onInvocationOf("matcher")
                                .withArgumentFromInvoking("decode")
                                .withArgument(varName)
                                .withArgument("UTF_8")
                                .on("URLDecoder")
                                .on(BASE64_PATTERN_STATIC_FIELD);

                    } else {
                        inv = varName + ".length() > 0 && !" + invocationOf("matches")
                                .onInvocationOf("matcher")
                                .withArgument(varName)
                                .on(BASE64_PATTERN_STATIC_FIELD);
                    }
                    IfBuilder<B> test = bb.iff().booleanExpression(inv);
                    validationExceptions().createThrow(cb, test, "String contains invalid base-64: ", varName);
                    test.endIf();
                    break;

            }
        }
    }
}
