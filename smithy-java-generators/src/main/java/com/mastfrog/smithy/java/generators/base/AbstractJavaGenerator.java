package com.mastfrog.smithy.java.generators.base;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.FieldBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.ModelElementGenerator;
import com.mastfrog.smithy.generators.SettingsKey;
import static com.mastfrog.smithy.generators.SettingsKey.key;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import static com.mastfrog.smithy.java.generators.builtin.SmithyJavaGenerators.TYPE_NAMES;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.mastfrog.smithy.java.generators.size.ObjectSizes;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static java.lang.Math.abs;
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

    static final SettingsKey<ObjectSizes> OBJECT_SIZE_KEY = key(ObjectSizes.class, "sizes");
    public SmithyGenerationContext ctx;
    protected SmithyGenerationLogger log;
    protected final S shape;
    protected final Model model;
    protected final Path destSourceRoot;
    protected final GenerationTarget target;
    protected final LanguageWithVersion language;
    // An intentionally non-sequential, shuffled array of primes for use
    // in hash-code generation
    private static final int[] PRIMES = {
        83_299, 60_223, 95_621, 57_587, 93_949, 22_907, 5_233, 50_891, 34_127, 71_971, 76_667, 55_291, 7_993, 80_141, 64_877, 8_863, 3_181, 41_491, 75_367, 42_227, 98_533, 26_119, 19_867, 26_861, 49_477, 19_541, 97_499, 103_889, 103_903, 14_407, 72_911, 21_377, 103_067, 18_313, 53_051, 9_619, 58_337, 94_111, 30_911, 43_261, 46_199, 102_871, 53_657, 14_057,
        929, 67_369, 70_199, 78_721, 10_993, 101_929, 46_477, 91_513, 16_231, 4_733, 79_697, 71_597, 65_957, 99_733, 48_313, 33_343, 17_257, 61_603, 33_377, 72_959, 45_763, 77_141, 5_659, 7_283, 14_843, 87_427, 503, 7_001, 7_867, 77_489, 103_231, 42_727, 100_103, 30_803, 58_031, 36_559, 51_719, 78_877, 25_457, 99_103, 18_743, 64_567, 66_629, 8_807, 2_633, 84_229, 59_009, 45_631, 1_777, 44_207, 42_487, 74_149, 103_867, 81_203, 39_047, 63_103, 89_449, 76_963, 30_187, 104_033, 44_543, 42_331, 21_943, 48_109, 60_271, 2_381, 67_129, 5_881, 52_289, 79_579, 95_441, 86_923, 8_647, 58_151, 59_417, 48_481, 50_111, 28_643, 76_163, 53_441, 78_893, 10_601, 38_653, 76_673, 79_537, 57_073, 58_111, 74_521, 77_951, 90_527, 17_623, 91_939, 80_963, 101, 82_021, 46_061, 62_801, 191, 92_801, 19_813, 47_807, 22_481, 74_381, 75_997, 20_611, 29_191, 98_123, 10_531, 82_171, 5_563, 16_063, 85_247, 22_027, 21_319, 37_307, 37_039, 43_969, 84_239, 87_149, 87_433, 35_449, 100_151, 10_861, 89_137, 41_201, 41_953, 46_399, 59_069, 89_867, 56_239, 55_469, 47_459, 73_141, 24_421, 25_943, 7_741, 100_207, 22_853, 33_199, 37_273, 30_631, 86_201, 83_273, 41_897, 63_487, 71_861, 29_599, 82_241, 27_271, 36_919, 77_171, 12_641, 59_359, 29_641, 86_711, 57_089, 30_491, 43_411, 61_379, 74_489, 87_509, 90_373, 19_289, 85_237, 103, 70_111, 29_443, 98_387, 12_203, 1_153, 40_849, 80_621, 14_107, 98_443, 53_939, 86_069};

    protected AbstractJavaGenerator(S shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language) {
        this.shape = shape;
        this.model = model;
        this.destSourceRoot = destSourceRoot;
        this.target = target;
        this.language = language;
    }

    protected ObjectSizes sizes() {
        return SmithyGenerationContext.get().computeIfAbsent(OBJECT_SIZE_KEY, () -> new ObjectSizes(model));
    }

    public S shape() {
        return shape;
    }

    public Model model() {
        return model;
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
        return PRIMES[abs(Objects.toString(what).hashCode()) % PRIMES.length];
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
            GeneratedJavaCode gen = new GeneratedJavaCode(destSourceRoot, cb, log);
            log.debug(() -> " " + getClass().getSimpleName() + " -> " + cb.fqn()
                    + " -> " + gen.destination());
            result.add(gen);
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
                .named(typeNameOf(shape))
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
        String tn = typeNameOf(shape);
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
