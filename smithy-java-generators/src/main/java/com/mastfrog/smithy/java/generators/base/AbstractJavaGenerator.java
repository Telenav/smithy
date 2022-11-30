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
package com.mastfrog.smithy.java.generators.base;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.FieldBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.ModelElementGenerator;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import static com.mastfrog.smithy.java.generators.builtin.SmithyJavaGenerators.TYPE_NAMES;
import com.mastfrog.smithy.java.generators.builtin.ValidationExceptionProvider;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.telenav.smithy.names.TypeNames;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractJavaGenerator<S extends Shape>
        implements ModelElementGenerator {

    private SmithyGenerationContext ctx;
    protected SmithyGenerationLogger log;
    protected final S shape;
    protected final Model model;
    protected final Path destSourceRoot;
    protected final GenerationTarget target;
    protected final LanguageWithVersion language;
    // An intentionally non-sequential, shuffled array of primes for use
    // in hash-code generation
    private static final int[] PRIMES = {
        83299, 60223, 95621, 57587, 93949, 22907, 5233, 50891, 34127, 71971, 76667,
        55291, 7993, 80141, 64877, 8863, 3181, 41491, 75367, 42227, 98533, 26119,
        19867, 26861, 49477, 19541, 97499, 103889, 103903, 14407, 72911, 21377, 103067,
        18313, 53051, 9619, 58337, 94111, 30911, 43261, 46199, 102871, 53657, 14057,
        929, 67369, 70199, 78721, 10993, 101929, 46477, 91513, 16231, 4733, 79697,
        71597, 65957, 99733, 48313, 33343, 17257, 61603, 33377, 72959, 45763, 77141,
        5659, 7283, 14843, 87427, 503, 7001, 7867, 77489, 103231, 42727, 100103, 30803,
        58031, 36559, 51719, 78877, 25457, 99103, 18743, 64567, 66629, 8807, 2633,
        84229, 59009, 45631, 1777, 44207, 42487, 74149, 103867, 81203, 39047, 63103,
        89449, 76963, 30187, 104033, 44543, 42331, 21943, 48109, 60271, 2381, 67129,
        5881, 52289, 79579, 95441, 86923, 8647, 58151, 59417, 48481, 50111, 28643,
        76163, 53441, 78893, 10601, 38653, 76673, 79537, 57073, 58111, 74521, 77951,
        90527, 17623, 91939, 80963, 101, 82021, 46061, 62801, 191, 92801, 19813, 47807,
        22481, 74381, 75997, 20611, 29191, 98123, 10531, 82171, 5563, 16063, 85247,
        22027, 21319, 37307, 37039, 43969, 84239, 87149, 87433, 35449, 100151, 10861,
        89137, 41201, 41953, 46399, 59069, 89867, 56239, 55469, 47459, 73141, 24421,
        25943, 7741, 100207, 22853, 33199, 37273, 30631, 86201, 83273, 41897, 63487,
        71861, 29599, 82241, 27271, 36919, 77171, 12641, 59359, 29641, 86711, 57089,
        30491, 43411, 61379, 74489, 87509, 90373, 19289, 85237, 103, 70111, 29443,
        98387, 12203, 1153, 40849, 80621, 14107, 98443, 53939, 86069
    };

    protected AbstractJavaGenerator(S shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language) {
        this.shape = shape;
        this.model = model;
        this.destSourceRoot = destSourceRoot;
        this.target = target;
        this.language = language;
    }

    public S shape() {
        return shape;
    }

    public Model model() {
        return model;
    }

    public ValidationExceptionProvider validationExceptions() {
        return ValidationExceptionProvider.validationExceptions();
    }

    protected static int primeCount() {
        return PRIMES.length;
    }

    public static IntUnaryOperator primes(Object hash) {
        int ix = PRIMES.length % hash.hashCode();
        return index -> {
            int target = (ix + index) % PRIMES.length;
            return PRIMES[target];
        };
    }

    /**
     * Useful for generating good hash codes.
     *
     * @param what An object whose hash code is used to find a prime.
     * @return A prime
     */
    public static long prime(Object what) {
        return PRIMES[Math.abs(Objects.toString(what).hashCode()) % PRIMES.length];
    }

    public static String decapitalize(String s) {
        char[] c = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    public final SmithyGenerationContext ctx() {
        return ctx;
    }

    public final TypeNames names() {
        return ctx.get(TYPE_NAMES).get();
    }

    protected final SmithyGenerationLogger log() {
        return log;
    }

    @Override
    public final Collection<? extends GeneratedCode> generate(
            SmithyGenerationContext ctx, SmithyGenerationLogger log) {
        this.ctx = ctx;
        this.log = log;
        Set<GeneratedCode> result = new HashSet<>();
        Consumer<ClassBuilder<String>> c = cb -> {
            result.add(new ClassBuilderWrapper(destSourceRoot, cb));
        };
        generate(c);
        return result;
    }

    protected String additionalDocumentation() {
        return null;
    }

    protected <T> ClassBuilder<T> applyDocumentation(ClassBuilder<T> cb) {
        StringBuilder sb = new StringBuilder();
        shape.getTrait(DocumentationTrait.class).ifPresent(doc -> {
            sb.append(doc.getValue());
        });
        String addt = additionalDocumentation();
        if (addt != null) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(addt);
        }
        shape.getTrait(DeprecatedTrait.class).ifPresent(dep -> {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("@deprecated");
            dep.getMessage().ifPresent(msg -> {
                sb.append(' ').append(msg);
            });
            dep.getSince().ifPresent(since -> {
                sb.append(" (since ").append(since).append(")");
            });
        });

        if (sb.length() > 0) {
            cb.docComment(sb);
        }
        return cb;
    }

    protected <T> MethodBuilder<T> applyDocumentation(MethodBuilder<T> cb) {
        shape.getTrait(DocumentationTrait.class).ifPresent(doc -> {
            cb.docComment(doc.getValue());
        });
        return cb;
    }

    protected <T> FieldBuilder<T> applyDocumentation(FieldBuilder<T> cb) {
        shape.getTrait(DocumentationTrait.class).ifPresent(doc -> {
            cb.docComment(doc.getValue());
        });
        return cb;
    }

    protected <T, R> String generateInitialEqualsTest(ClassBuilder<R> cb, ClassBuilder.BlockBuilder<T> bb) {
        return generateInitialEqualsTest(cb.unusedFieldName("o"), cb, bb);
    }

    protected <T, R> String generateInitialEqualsTest(String argName, ClassBuilder<R> cb, ClassBuilder.BlockBuilder<T> bb) {
        bb.iff().booleanExpression(argName + " == this")
                .returning(true)
                .elseIf().booleanExpression(argName + " == null || "
                        + argName + ".getClass() != " + cb.className() + ".class")
                .returning(false)
                .endIf();

        String fldName = cb.unusedFieldName("other");

        bb.declare(fldName).initializedWithCastTo(cb.className())
                .ofExpression(argName)
                .as(cb.className());

        return fldName;
    }

    /**
     * Generic equals computation which takes care of the equals test head
     * comparisons of null, == this and wrong type.
     *
     * @param cb A class builder
     */
    protected void generateEquals(ClassBuilder<String> cb) {
        cb.overridePublic("equals")
                .addArgument("Object", "o")
                .returning("boolean")
                .body(bb -> {
                    String other = generateInitialEqualsTest(cb, bb);
                    generateEqualsComparison(other, cb, bb);
                });
    }

    protected <T, R, B extends BlockBuilderBase<R, B, ?>> void generateEqualsComparison(
            String other, ClassBuilder<?> cb, B bb) {
        throw new UnsupportedOperationException("generateEqualsComparison not "
                + "implemented in " + getClass().getSimpleName());
    }

    /**
     * Generate the code.
     *
     * @param addTo A consumer of class builders (in case more than one class is
     * generated)
     */
    protected abstract void generate(Consumer<ClassBuilder<String>> addTo);

    /**
     * Generic hash code generation.
     *
     * @param cb A class builder
     */
    protected void generateHashCode(ClassBuilder<String> cb) {
        cb.overridePublic("hashCode")
                .returning("int")
                .body(bb -> {
                    generateHashCodeComputation(cb, bb);
                });
    }

    /**
     * Target method of the generic hash code generation method.
     *
     * @param <T>
     * @param <R>
     * @param cb
     * @param bb
     */
    protected <T, R> void generateHashCodeComputation(ClassBuilder<T> cb, BlockBuilder<R> bb) {
        throw new UnsupportedOperationException("generateHashCodeComputation not implemented in " + getClass()
                .getSimpleName());
    }

    protected boolean shouldApplyGeneratedAnnotation() {
        return true;
    }

    /**
     * Generate a generic class builder named for this instance's shape and in
     * the appropriate package.
     *
     * @return A class builder
     */
    protected ClassBuilder<String> classHead() {
        ClassBuilder<String> result = ClassBuilder.forPackage(names().packageOf(shape))
                .named(TypeNames.typeNameOf(shape))
                .importing(
                        "java.io.Serializable"
                )
                .implementing("Serializable");

        applyModifiers(result);

        result.field("serialVersionUid", fld -> {
            fld.withModifier(PRIVATE, STATIC, FINAL)
                    .initializedWith(1L);
        });

        if (ctx.settings().is(DEBUG)) {
            result.generateDebugLogCode();
        }

        shape.getTrait(DeprecatedTrait.class).ifPresent(dep -> {
            result.annotatedWith("Deprecated").closeAnnotation();
        });

        if (shouldApplyGeneratedAnnotation()) {
            applyGeneratedAnnotation(getClass(), result);
        }

        return result;
    }

    protected void applyModifiers(ClassBuilder<String> cb) {
        cb.withModifier(PUBLIC, FINAL);
    }

    /**
     * Import some classes, checking if it is either in the same package, or in
     * the java.lang package and ignoring it if so.
     *
     * @param cb A class builder
     * @param fqns Fully qualified class names
     */
    public static void maybeImport(ClassBuilder<?> cb, String... fqns) {
        for (String f : fqns) {
            if (f.indexOf('.') < 0 || f.startsWith("null.")) {
                continue;
            }
            importOne(cb, f);
        }
    }

    /**
     * Import a class, checking if it is either in the same package, or in the
     * java.lang package and ignoring it if so.
     *
     * @param cb A class builder
     * @param fqn A fully qualified class name
     */
    private static void importOne(ClassBuilder<?> cb, String fqn) {
        if (fqn.startsWith("java.lang.")) {
            return;
        }
        int ix = fqn.lastIndexOf('.');
        if (ix < 0) {
            return;
        }
        String sub = fqn.substring(0, ix);
        if (cb.packageName().equals(sub)) {
            return;
        }
        cb.importing(fqn);
    }

    protected <T, R> void generateNullCheck(String variable, BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on) {
        IfBuilder<?> test = bb.ifNull(variable);
        validationExceptions().createThrow(on, test, variable + " may not be null - it is required.", null);
        test.endIf();
    }

    /**
     * Both String and collection-like members can have the LengthTrait trait;
     * and they may be wrapped in a type we generated. We need to call length()
     * on strings and size() on collections; here we determine which it is.
     *
     * @param shape A shape
     * @return A method name
     */
    protected String lengthMethod(Shape shape) {
        if (shape.isMemberShape()) {
            shape = model.expectShape(shape.asMemberShape().get().getTarget());
            System.out.println("  SHAPE NOW " + shape);
        }
        switch (shape.getType()) {
            case LIST:
            case MAP:
            case SET:
            case STRUCTURE:
                return "size";
        }
        return "length";
    }

    protected String keyLengthMethod() {
        return lengthMethod(shape.getMember("key").get());
    }

    protected String valueLengthMethod() {
        return lengthMethod(shape.getMember("value").get());
    }

    protected <B extends BlockBuilderBase<T, B, ?>, T> void generateEqualityCheckOfNullable(
            String v, String compareWith, B bldr) {

        Value a = variable(v);
        Value b = variable(compareWith);

        Value condition = a.isNotNull().parenthesized().isNotEqualTo(
                b.isNotNull().parenthesized()).parenthesized()
                .logicalOrWith(
                        a.isNotNull().logicalAndNotWith(
                                invocationOf("equals")
                                        .withArgument(compareWith)
                                        .on(v))
                                .parenthesized()
                );
        bldr.iff(condition).returning(false).endIf();
    }

    protected void sanityCheckMemberConstraints(MemberShape shape) {
        Shape realShape = model.expectShape(shape.getTarget());
        Optional<RangeTrait> memberRanges = shape.getTrait(RangeTrait.class);
        Optional<RangeTrait> originalRanges = realShape.getTrait(RangeTrait.class);

        if (memberRanges.isPresent() && originalRanges.isPresent()) {
            checkRangesCompatible(realShape, originalRanges.get(),
                    shape, memberRanges.get());
        }

        Optional<LengthTrait> memberLengths = shape.getTrait(LengthTrait.class);
        Optional<LengthTrait> originalLengths = realShape.getTrait(LengthTrait.class);
        if (memberLengths.isPresent() && originalLengths.isPresent()) {
            checkLengthsCompatible(realShape, originalLengths.get(),
                    shape, memberLengths.get());
        }
    }

    protected void ensureImported(ClassBuilder<?> currentClassBuilder, Shape shape) {
        String shapeNs = shape.getId().getNamespace();
        if ("smithy.api".equals(shapeNs) || shape == this.shape) {
            switch (shape.getType()) {
                case TIMESTAMP:
                    currentClassBuilder.importing(Instant.class);
                    break;
                case BIG_DECIMAL:
                    currentClassBuilder.importing(BigDecimal.class);
                    break;
                case BIG_INTEGER:
                    currentClassBuilder.importing(BigInteger.class);
                    break;
                case MAP:
                    currentClassBuilder.importing(Map.class);
                    break;
                case LIST:
                    if (shape.getTrait(UniqueItemsTrait.class).isPresent()) {
                        currentClassBuilder.importing(Set.class);
                    } else {
                        currentClassBuilder.importing(List.class);
                    }
                    break;
                case SET:
                    currentClassBuilder.importing(Set.class);
                    break;
                // Pending: What to do for Document?
            }
            return;
        }
        String pk = names().packageOf(shape);
        String tn = TypeNames.typeNameOf(shape);
        if (!pk.equals(currentClassBuilder.packageName())) {
            currentClassBuilder.importing(pk + "." + tn);
        }
    }

    protected void sanityCheckConstraints() {
        checkRangeSane(shape);
        shape.getAllMembers().forEach((memberName, memberShape) -> {
            checkRangeSane(memberShape);
            sanityCheckMemberConstraints(memberShape);
        });
    }

    protected void checkRangeSane(Shape shape) {
        shape.getTrait(RangeTrait.class).ifPresent(rng -> checkRangeSane(shape, rng));
    }

    protected void checkRangeSane(Shape src, RangeTrait range) {
        range.getMin().ifPresent(min -> {
            range.getMax().ifPresent(max -> {
                if (max.compareTo(min) < 0) {
                    throw new ExpectationNotMetException("Maximum " + max
                            + " is less than minimum " + min
                            + " on " + src.getId(),
                            range);
                }
            });
        });
    }

    private void checkRangesCompatible(Shape orig, RangeTrait range,
            MemberShape member, RangeTrait memberRange) {
        // Original range start must be less than the member range start,
        // and must be less than the member range end
        String msgHead = "Both " + orig.getId() + " and " + member.getId()
                + " specify @range;";

        range.getMin().ifPresent(origMin -> {
            memberRange.getMin().ifPresent(memberMin -> {
                boolean memberIsLess
                        = memberMin.compareTo(origMin) < 0;
                if (memberIsLess) {
                    throw new ExpectationNotMetException(
                            msgHead + " the original minimum is "
                            + origMin + " but the member usage specifies a "
                            + "lower value " + memberMin + ".  It must be the same, "
                            + "greater, or unspecified.",
                            memberRange.getSourceLocation());
                }
            });
            memberRange.getMax().ifPresent(memberMax -> {
                boolean memberIsLess = memberMax.compareTo(origMin) < 0;
                if (memberIsLess) {
                    throw new ExpectationNotMetException(msgHead
                            + " the maximum value in the member usage, "
                            + memberMax
                            + " is less than the MINIMUM possible "
                            + "on the definition of " + orig.getId().getName()
                            + ", " + origMin
                            + ". That makes assigning " + member.getMemberName()
                            + " impossible.",
                            memberRange.getSourceLocation());
                }

            });
        });
        range.getMax().ifPresent(origMax -> {
            memberRange.getMax().ifPresent(memberMax -> {
                boolean memberIsGreater
                        = origMax.compareTo(memberMax) < 0;
                if (memberIsGreater) {
                    throw new ExpectationNotMetException(msgHead + " "
                            + "maximum in member usage is " + memberMax
                            + " which is greater than the maximum specified on "
                            + "the type " + shape.getId().getName() + ", "
                            + origMax + ".  The maximum in a member may constrain "
                            + "the maximum to a smaller value, but may not enlarge it.",
                            memberRange.getSourceLocation());
                }
            });
        });
    }

    private void checkLengthsCompatible(Shape orig, LengthTrait range, MemberShape member, LengthTrait memberRange) {
        String msgHead = "Both " + orig.getId() + " and " + member.getId()
                + " specify @length;";
        range.getMin().ifPresent(origMin -> {
            memberRange.getMin().ifPresent(memberMin -> {
                boolean memberIsLess
                        = memberMin.compareTo(origMin) < 0;
                if (memberIsLess) {
                    throw new ExpectationNotMetException(
                            msgHead + " the original minimum is "
                            + origMin + " but the member usage specifies a "
                            + "lower value " + memberMin + ".  It must be the same, "
                            + "greater, or unspecified.",
                            memberRange.getSourceLocation());
                }
            });
            memberRange.getMax().ifPresent(memberMax -> {
                boolean memberIsLess = memberMax.compareTo(origMin) < 0;
                if (memberIsLess) {
                    throw new ExpectationNotMetException(msgHead
                            + " the maximum value in the member usage "
                            + "is less than the MINIMUM possible "
                            + "on the definition of " + orig.getId().getName()
                            + ". That makes assigning " + member.getMemberName()
                            + " impossible.",
                            memberRange.getSourceLocation());
                }

            });
        });
        range.getMax().ifPresent(origMax -> {
            memberRange.getMax().ifPresent(memberMax -> {
                boolean memberIsGreater
                        = origMax.compareTo(memberMax) > 0;
                if (memberIsGreater) {
                    throw new ExpectationNotMetException(msgHead + " "
                            + "maximum in member usage is " + memberMax
                            + " which is greater than the maximum specified on "
                            + "the type " + shape.getId().getName() + ", "
                            + origMax + ".  The maximum in a member may constrain "
                            + "the maximum to a smaller value, but may not enlarge it.",
                            memberRange.getSourceLocation());
                }
            });
        });
    }

    protected static void sanityCheckRange(Shape shape, RangeTrait rng) {
        BigDecimal hardMin;
        BigDecimal hardMax;
        switch (shape.getType()) {
            case BYTE:
                hardMin = BigDecimal.valueOf(Byte.MIN_VALUE);
                hardMax = BigDecimal.valueOf(Byte.MAX_VALUE);
                break;
            case SHORT:
                hardMin = BigDecimal.valueOf(Short.MIN_VALUE);
                hardMax = BigDecimal.valueOf(Short.MAX_VALUE);
                break;
            case INTEGER:
                hardMin = BigDecimal.valueOf(Integer.MIN_VALUE);
                hardMax = BigDecimal.valueOf(Integer.MAX_VALUE);
                break;
            case LONG:
                hardMin = BigDecimal.valueOf(Long.MIN_VALUE);
                hardMax = BigDecimal.valueOf(Long.MAX_VALUE);
                break;
            case DOUBLE:
                hardMin = BigDecimal.valueOf(Double.MAX_VALUE)
                        .multiply(BigDecimal.valueOf(-1));
                hardMax = BigDecimal.valueOf(Double.MAX_VALUE);
                break;
            case FLOAT:
                hardMin = BigDecimal.valueOf(Float.MAX_VALUE)
                        .multiply(BigDecimal.valueOf(-1));
                hardMax = BigDecimal.valueOf(Float.MAX_VALUE);
                break;
            default:
                return;
        }
        rng.getMin().ifPresent(min -> {
            if (hardMin.compareTo(min) > 0) {
                throw new ExpectationNotMetException("Minimum @range value " + min
                        + " of " + shape.getId()
                        + " is less than the minimum possible value of " + shape.getType()
                        + ", " + hardMin, rng);
            }
        });
        rng.getMax().ifPresent(max -> {
            if (hardMax.compareTo(max) < 0) {
                throw new ExpectationNotMetException("Maximum @range value " + max
                        + " of " + shape.getId()
                        + " is greater than the maximum possible value of " + shape.getType()
                        + ", " + hardMax, rng);
            }
        });
    }

}
