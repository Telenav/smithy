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
package com.mastfrog.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ComparisonBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 * Generates one or more checks of a constructor argument into the body of a
 * constructor.
 *
 * @author Tim Boudreau
 */
public interface ConstructorArgumentCheckGenerator<S extends Shape> {

    <T, B extends BlockBuilderBase<T, B, ?>>
            void generateConstructorArgumentChecks(
                    StructureMember<? extends S> member,
                    StructureGenerationHelper structureOwner,
                    ClassBuilder<?> addTo, B bb,
                    ConstructorKind kind
            );

    final ConstructorArgumentCheckGenerator<Shape> STRING_PATTERN
            = new ConstructorArgumentCheckGenerator<>() {
        @Override
        public <T, B extends BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(
                StructureMember<? extends Shape> member, StructureGenerationHelper structureOwner,
                ClassBuilder<?> addTo, B bb, ConstructorKind kind) {

            member.member().getTrait(PatternTrait.class).ifPresent(pat -> {

                String patternField = patternFieldName(member);
                if (!addTo.containsFieldNamed(patternField)) {
                    addTo.field(patternField, fld -> {
                        fld.withModifier(PRIVATE, STATIC, FINAL);
                        fld.docComment("Pattern the argument " + member.jsonName()
                                + " must match, as defined in the Smithy model.");
                        addTo.importing(Pattern.class, Matcher.class);
                        fld.initializedFromInvocationOf("compile")
                                .withStringLiteral(pat.getValue())
                                .on("Pattern").ofType("Pattern");
                    });
                }
                boolean useToString = member.isModelDefinedType();
                boolean canBeNull = member.hasDefault() || !member.isRequired();
                if (canBeNull) {
                    IfBuilder<B> iff = bb.ifNotNull(member.arg());
                    generatePatternTest(structureOwner, addTo, iff, patternField, member, useToString, pat.getValue());
                    iff.endIf();
                } else {
                    generatePatternTest(structureOwner, addTo, bb, patternField, member, useToString, pat.getValue());
                }
            });
        }

        private <S extends Shape, B extends BlockBuilderBase<T, B, ?>, T>
                void generatePatternTest(
                        StructureGenerationHelper structureOwner,
                        ClassBuilder<?> cb,
                        B bb, String patternField, StructureMember<S> member, boolean useToString,
                        String pattern) {
            String mname = "matcher_" + member.field();
            InvocationBuilder<TypeAssignment<B>> decl
                    = bb.declare(mname).initializedByInvoking("matcher");

            InvocationBuilder<TypeAssignment<B>> addTo;
            if (useToString) {
                addTo = decl.withArgumentFromInvoking("toString")
                        .on(member.arg());
            } else {
                addTo = decl.withArgument(member.arg());
            }
            addTo.on(patternField)
                    .as("Matcher");
            IfBuilder<B> iff = bb.iff().invokeAsBoolean("find").on(mname);
            structureOwner.validation().createThrow(cb, iff, member.jsonName()
                    + " does not match the pattern " + pattern + " - got ",
                    member.arg());
            iff.endIf();
        }

        private String patternFieldName(StructureMember<?> sm) {
            return Strings.camelCaseToDelimited(sm.jsonName(), '_').toUpperCase() + "_PATTERN";
        }
    };

    final ConstructorArgumentCheckGenerator<Shape> COLLECTION_SIZE = new ConstructorArgumentCheckGenerator<Shape>() {

        @Override
        public <T, B extends BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(
                StructureMember<? extends Shape> member,
                StructureGenerationHelper structureOwner,
                ClassBuilder<?> addTo,
                B bb,
                ConstructorKind kind) {
            boolean canBeNull = !member.isRequired() && !member.hasDefault();
            member.member().getTrait(LengthTrait.class).ifPresent(len -> {
                len.getMin().ifPresent(min -> {
                    if (min.intValue() > 0) {
                        if (canBeNull) {
                            IfBuilder<B> iff = bb.ifNotNull(member.arg());
                            generateMinTest(member, structureOwner, min, iff, addTo);
                            iff.endIf();
                        } else {
                            generateMinTest(member, structureOwner, min, bb, addTo);
                        }
                    }
                });
                len.getMax().ifPresent(max -> {
                    if (max.intValue() < Integer.MAX_VALUE) {
                        if (canBeNull) {
                            IfBuilder<B> iff = bb.ifNotNull(member.arg());
                            generateMaxTest(member, structureOwner, max, iff, addTo);
                            iff.endIf();
                        } else {
                            generateMaxTest(member, structureOwner, max, bb, addTo);
                        }
                    }
                });
            });
        }

        private <B extends BlockBuilderBase<T, B, ?>, T> void generateMinTest(StructureMember<?> member, StructureGenerationHelper structureOwner, Long min, B bb, ClassBuilder<?> addTo) {
            IfBuilder<B> test = bb.iff().invocationOf("size").on(member.arg())
                    .isLessThan(min.intValue());
            structureOwner.validation().createThrow(addTo, test, "Size of "
                    + member.jsonName() + " is less than the minimum of "
                    + min + " in the schema - got ", member.arg() + ".size()");
            test.endIf();
        }

        private <B extends BlockBuilderBase<T, B, ?>, T> void generateMaxTest(StructureMember<?> member, StructureGenerationHelper structureOwner, Long max, B bb, ClassBuilder<?> addTo) {
            IfBuilder<B> test = bb.iff().invocationOf("size").on(member.arg())
                    .isGreaterThan(max.intValue());
            structureOwner.validation().createThrow(addTo, test,
                    "Size of " + member.jsonName() + " is greater than the maximum of "
                    + max + " in the schema - got ", member.arg() + ".size()");
            test.endIf();
        }

    };

    final ConstructorArgumentCheckGenerator<Shape> STRING_LENGTH = new ConstructorArgumentCheckGenerator<Shape>() {

        @Override
        public <T, B extends BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(
                StructureMember<? extends Shape> member,
                StructureGenerationHelper structureOwner,
                ClassBuilder<?> addTo,
                B bb,
                ConstructorKind kind) {
            boolean canBeNull = !member.isRequired() && !member.hasDefault();
            member.member().getTrait(LengthTrait.class).ifPresent(len -> {
                len.getMin().ifPresent(min -> {
                    if (min.intValue() > 0) {
                        if (canBeNull) {
                            IfBuilder<B> iff = bb.ifNotNull(member.arg());
                            generateMinTest(member, structureOwner, min, iff, addTo);
                            iff.endIf();
                        } else {
                            generateMinTest(member, structureOwner, min, bb, addTo);
                        }
                    }
                });
                len.getMax().ifPresent(max -> {
                    if (max.intValue() < Integer.MAX_VALUE) {
                        if (canBeNull) {
                            IfBuilder<B> iff = bb.ifNotNull(member.arg());
                            generateMaxTest(member, structureOwner, max, iff, addTo);
                            iff.endIf();
                        } else {
                            generateMaxTest(member, structureOwner, max, bb, addTo);
                        }
                    }
                });
            });
        }

        private <B extends BlockBuilderBase<T, B, ?>, T> void generateMinTest(StructureMember<?> member, StructureGenerationHelper structureOwner, Long min, B bb, ClassBuilder<?> addTo) {
            IfBuilder<B> test = bb.iff().invocationOf("length").on(member.arg())
                    .isLessThan(min.intValue());
            structureOwner.validation().createThrow(addTo, test, "Length of "
                    + member.jsonName() + " is less than the minimum of "
                    + min + " in the schema - got ",
                    member.arg() + ".length() + ' ' + " + member.arg());
            test.endIf();
        }

        private <B extends BlockBuilderBase<T, B, ?>, T> void generateMaxTest(StructureMember<?> member, StructureGenerationHelper structureOwner, Long max, B bb, ClassBuilder<?> addTo) {
            IfBuilder<B> test = bb.iff().invocationOf("length").on(member.arg())
                    .isGreaterThan(max.intValue());
            structureOwner.validation().createThrow(addTo, test, "Length of "
                    + member.jsonName() + " is greater than the maximum of "
                    + max + " in the schema - got ",
                    member.arg() + ".length() + ' ' + " + member.arg());
            test.endIf();
        }
    };

    final ConstructorArgumentCheckGenerator<Shape> NUMBER_RANGE = new ConstructorArgumentCheckGenerator<Shape>() {

        @Override
        public <T, B extends BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(
                StructureMember<? extends Shape> member, StructureGenerationHelper structureOwner,
                ClassBuilder<?> addTo, B bb, ConstructorKind kind) {
            switch (member.target().getType()) {
                case DOCUMENT:
                case BLOB:
                case LIST:
                case SET:
                case MEMBER:
                case MAP:
                case UNION:
                case TIMESTAMP:
                case OPERATION:
                case RESOURCE:
                case SERVICE:
                case STRUCTURE:
                case STRING:
                case BOOLEAN:
                    return;
                case BIG_DECIMAL:
                case BIG_INTEGER:
                    throw new UnsupportedOperationException("Min max on BigDecimal/BigInteger not yet supported");
            }
            boolean canBeNull = !member.isRequired() && !member.hasDefault()
                    && kind == ConstructorKind.JSON_DESERIALIZATON;
            member.member().getTrait(RangeTrait.class).ifPresent(range -> {
                range.getMin().ifPresent(min -> {
                    if (min.intValue() > 0) {
                        if (canBeNull) {
                            IfBuilder<B> iff = bb.ifNotNull(member.arg());
                            generateMinTest(member, structureOwner, min, iff, addTo);
                            iff.endIf();
                        } else {
                            generateMinTest(member, structureOwner, min, bb, addTo);
                        }
                    }
                });
                range.getMax().ifPresent(max -> {
                    if (max.intValue() < Integer.MAX_VALUE) {
                        if (canBeNull) {
                            IfBuilder<B> iff = bb.ifNotNull(member.arg());
                            generateMaxTest(member, structureOwner, max, iff, addTo);
                            iff.endIf();
                        } else {
                            generateMaxTest(member, structureOwner, max, bb, addTo);
                        }
                    }
                });

            });
        }

        private <B extends BlockBuilderBase<T, B, ?>, T> void generateMinTest(
                StructureMember<?> member, StructureGenerationHelper structureOwner,
                BigDecimal min, B bb, ClassBuilder<?> addTo) {

            boolean isWrapper = member.isModelDefinedType();
            ComparisonBuilder<IfBuilder<B>> cond;
            if (isWrapper) {
                String mth;
                switch (member.target().getType()) {
                    case BYTE:
                        mth = "getAsByte";
                        break;
                    case SHORT:
                        mth = "getAsShort";
                        break;
                    case INTEGER:
                        mth = "getAsInt";
                        break;
                    case LONG:
                        mth = "getAsLong";
                        break;
                    case FLOAT:
                        mth = "getAsFloat";
                        break;
                    case DOUBLE:
                        mth = "getAsDouble";
                        break;
                    default:
                        throw new UnsupportedOperationException(member.target().getType() + " not supported");
                }
                cond = bb.iff().invocationOf(mth).on(member.arg());
            } else {
                cond = bb.iff().variable(member.arg());
            }

            IfBuilder<B> test;
            switch (member.target().getType()) {
                case BYTE:
                case SHORT:
                case INTEGER:
                    test = cond.isLessThan(min.intValue());
                    break;
                case LONG:
                    test = cond.isLessThan(min.longValue());
                    break;
                case FLOAT:
                case DOUBLE:
                    test = cond.isLessThan(min.doubleValue());
                    break;
                default:
                    throw new UnsupportedOperationException(member.target().getType()
                            + " not implemented");
            }

            structureOwner.validation().createThrow(addTo, test, "Length of "
                    + member.jsonName() + " is less than the minimum of "
                    + min + " in the schema - got ",
                    member.arg());
            test.endIf();
        }

        private <B extends BlockBuilderBase<T, B, ?>, T> void generateMaxTest(
                StructureMember<?> member, StructureGenerationHelper structureOwner,
                BigDecimal max, B bb, ClassBuilder<?> addTo) {

            boolean isWrapper = member.isModelDefinedType();
            ComparisonBuilder<IfBuilder<B>> cond;
            if (isWrapper) {
                String mth;
                switch (member.target().getType()) {
                    case BYTE:
                        mth = "getAsByte";
                        break;
                    case SHORT:
                        mth = "getAsShort";
                        break;
                    case INTEGER:
                        mth = "getAsInt";
                        break;
                    case LONG:
                        mth = "getAsLong";
                        break;
                    case FLOAT:
                        mth = "getAsFloat";
                        break;
                    case DOUBLE:
                        mth = "getAsDouble";
                        break;
                    default:
                        throw new UnsupportedOperationException(member.target().getType() + " not supported");
                }
                cond = bb.iff().invocationOf(mth).on(member.arg());
            } else {
                cond = bb.iff().variable(member.arg());
            }

            IfBuilder<B> test;
            switch (member.target().getType()) {
                case BYTE:
                case SHORT:
                case INTEGER:
                    test = cond.isGreaterThan(max.intValue());
                    break;
                case LONG:
                    test = cond.isGreaterThan(max.longValue());
                    break;
                case FLOAT:
                case DOUBLE:
                    test = cond.isGreaterThan(max.doubleValue());
                    break;
                default:
                    throw new UnsupportedOperationException(member.target().getType()
                            + " not implemented");
            }

            structureOwner.validation().createThrow(addTo, test, "Length of "
                    + member.jsonName() + " is greater than the maximum of "
                    + max + " in the schema - got ",
                    member.arg());
            test.endIf();
        }
    };

    final ConstructorArgumentCheckGenerator<Shape> BIG_NUMBER_RANGE = new ConstructorArgumentCheckGenerator<Shape>() {
        @Override
        public <T, B extends BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(
                StructureMember<? extends Shape> member, StructureGenerationHelper structureOwner,
                ClassBuilder<?> cb, B bb, ConstructorKind kind) {
            boolean isWrapper = member.isModelDefinedType();
            boolean canBeNull = !member.isRequired() && !member.hasDefault();

            boolean decimal;
            switch (member.target().getType()) {
                case BIG_DECIMAL:
                    decimal = true;
                    break;
                case BIG_INTEGER:
                    decimal = false;
                    break;
                default:
                    throw new AssertionError(member.target().getType());
            }

            RangeTrait rng = member.member().getTrait(RangeTrait.class).get();
            if (!rng.getMin().isPresent() && !rng.getMax().isPresent()) {
                return;
            }
            // We need the null check regardless, so we don't throw an NPE on
            // null input with a default that will be assigned later (and which
            // we don't want to test).
            IfBuilder<B> test = bb.ifNotNull(member.arg());
            createTests(rng, member, cb, decimal, test, structureOwner, canBeNull, isWrapper);
            test.endIf();
        }

        public <B extends BlockBuilderBase<T, B, ?>, T> void createTests(RangeTrait rng, StructureMember<? extends Shape> member, ClassBuilder<?> cb, boolean decimal, B bb, StructureGenerationHelper structureOwner, boolean canBeNull, boolean isWrapper) {
            rng.getMin().ifPresent(min -> {
                String fld = createMinMaxFieldFor(member, cb, decimal, min, true);
                createTest(member, fld, true, bb, structureOwner, canBeNull, isWrapper, cb, min);
            });
            rng.getMax().ifPresent(max -> {
                String fld = createMinMaxFieldFor(member, cb, decimal, max, false);
                createTest(member, fld, false, bb, structureOwner, canBeNull, isWrapper, cb, max);
            });
        }

        private <T, B extends BlockBuilderBase<T, B, ?>>
                void createTest(StructureMember<?> mem, String minMaxField, boolean isMin,
                        B bb, StructureGenerationHelper structureOwner, boolean canBeNull,
                        boolean isWrapper, ClassBuilder<?> cb, BigDecimal val) {
            Value v;
            if (isWrapper) {
                v = invocationOf("compareTo")
                        .withArgument(minMaxField)
                        .onInvocationOf("get")
                        .on(mem.arg());
            } else {
                v = invocationOf("compareTo")
                        .withArgument(minMaxField)
                        .on(mem.arg());
            }
            if (isMin) {
                v = v.isLessThan(number(0));
            } else {
                v = v.isGreaterThan(number(0));
            }

            IfBuilder<B> test = bb.iff(v);
            String msg = isMin ? mem.jsonName() + " is less than the minimum, "
                    : mem.jsonName() + " is greater than the maximum, " + val + " - got ";
            structureOwner.validation().createThrow(cb, test, msg, mem.arg());
            test.endIf();
        }

        private String createMinMaxFieldFor(StructureMember<? extends Shape> member,
                ClassBuilder<?> cb, boolean decimal, BigDecimal value, boolean isMin) {
            String nm = Strings.camelCaseToDelimited(Strings.escape(member.jsonName().replace('-', '_'), Escaper.JAVA_IDENTIFIER_DELIMITED), '_').toUpperCase()
                    + (isMin ? "_MIN" : "_MAX");
            if (!cb.containsFieldNamed(nm)) {
                if (decimal) {
                    cb.importing(BigDecimal.class);

                    // If it already exists as a constant in the JDK, just use that
                    // and we will get the right result
                    if (BigDecimal.ZERO.equals(value)) {
                        return "BigDecimal.ZERO";
                    } else if (BigDecimal.ONE.equals(value)) {
                        return "BigDecimal.ONE";
                    } else if (BigDecimal.TEN.equals(value)) {
                        return "BigDecimal.TEN";
                    }
                    cb.field(nm, fld -> {
                        fld.withModifier(PRIVATE, STATIC, FINAL)
                                .initializedWithNew(nb -> {
                                    nb.withStringLiteral(value.toString())
                                            .ofType("BigDecimal");
                                }).ofType("BigDecimal");
                    });
                } else {
                    cb.importing(BigInteger.class);
                    BigInteger biVal = value.toBigInteger();
                    if (BigInteger.ZERO.equals(biVal)) {
                        return "BigInteger.ZERO";
                    } else if (BigInteger.ONE.equals(biVal)) {
                        return "BigInteger.ONE";
                    } else if (BigInteger.TWO.equals(biVal)) {
                        return "BigInteger.TWO";
                    } else if (BigInteger.TEN.equals(biVal)) {
                        return "BigInteger.TEN";
                    }
                    cb.field(nm, fld -> {
                        fld.withModifier(PRIVATE, STATIC, FINAL)
                                .initializedWithNew(nb -> {
                                    nb.withStringLiteral(biVal.toString())
                                            .ofType("BigInteger");
                                }).ofType("BigInteger");
                    });

                }

            }
            return nm;
        }
    };

}
