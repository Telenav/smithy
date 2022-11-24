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

import com.mastfrog.function.ByteConsumer;
import com.mastfrog.function.ShortConsumer;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import static com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator.decapitalize;
import com.mastfrog.smithy.java.generators.builtin.ValidationExceptionProvider;
import com.mastfrog.smithy.java.generators.builtin.struct.Namer;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.mastfrog.smithy.java.generators.util.JavaSymbolProvider;
import static com.mastfrog.smithy.java.generators.util.JavaSymbolProvider.escape;
import com.mastfrog.smithy.java.generators.util.JavaTypes;
import static com.mastfrog.smithy.java.generators.util.JavaTypes.packageOf;
import com.mastfrog.smithy.java.generators.util.TypeNames;
import static com.mastfrog.smithy.java.generators.util.TypeNames.typeNameOf;
import com.mastfrog.smithy.simple.extensions.SamplesTrait;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.RandomStrings;
import com.mastfrog.util.strings.Strings;
import static java.lang.Math.ceil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractJavaTestGenerator<S extends Shape> extends AbstractJavaGenerator<S> {

    protected int varCounter;
    protected final Random rnd;
    protected ClassBuilder<String> currentClassBuilder;
    protected String currentTypeName;

    public AbstractJavaTestGenerator(S shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        rnd = new Random(shape.getId().hashCode());
    }

    protected void onBeforeGenerate() {

    }

    @Override
    protected final void generate(Consumer<ClassBuilder<String>> addTo) {
        onBeforeGenerate();
        ClassBuilder<String> cb = currentClassBuilder = testClassHead();
        String typeName = currentTypeName = TypeNames.typeNameOf(shape.getId());

        generate(cb, typeName);

        addTo.accept(cb);
    }

    protected abstract void generate(ClassBuilder<String> cb, String typeName);

    protected void onCreateTestMethod(String name, MethodBuilder<?> bldr) {

    }

    protected void testMethod(String what, ClassBuilder<String> cb, Consumer<ClassBuilder.BlockBuilder<?>> c) {
        cb.method("test" + what, mth -> {
            onCreateTestMethod(what, mth);
            mth.withModifier(PUBLIC, FINAL)
                    .throwing("Exception")
                    .annotatedWith("Test").closeAnnotation()
                    .body(c);
        });
    }

    protected ClassBuilder<String> testClassHead() {
        ClassBuilder<String> result = ClassBuilder.forPackage(names().packageOf(shape))
                .named(TypeNames.typeNameOf(shape) + "Test")
                .importing(
                        "com.fasterxml.jackson.databind.ObjectMapper",
                        "org.junit.jupiter.api.Test",
                        "static org.junit.jupiter.api.Assertions.*"
                )
                .withModifier(PUBLIC, FINAL);

        result.field("serialVersionUid", fld -> {
            fld.withModifier(PRIVATE, STATIC, FINAL)
                    .initializedWith(1L);
        });

        if (ctx().settings().is(DEBUG)) {
            result.generateDebugLogCode();
        }

        shape.getTrait(DeprecatedTrait.class).ifPresent(dep -> {
            result.annotatedWith("SuppressWarnings")
                    .addArgument("value", "deprecation")
                    .closeAnnotation();
        });

        if (shouldApplyGeneratedAnnotation()) {
            applyGeneratedAnnotation(getClass(), result);
        }

        return result;
    }

    private Map<String, Integer> usedVars = new HashMap<>();

//    protected String newVarName() {
//        return newVarName("testValue");
//    }
    protected String newVarName(String prefix) {
        int suffix = usedVars.compute(prefix, (key, old) -> {
            if (old == null) {
                return 0;
            }
            return old + 1;
        });
        if (suffix == 0) {
            return escape(decapitalize(prefix));
        }
        return escape(decapitalize(prefix) + Strings.capitalize(Integer.toHexString(suffix)));
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void assertEquals(String exp, String got, String msg, B bb) {
        bb.invoke("assertEquals")
                .withArgument(exp)
                .withArgument(got)
                .withStringLiteral(msg)
                .inScope();
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> InstanceInfo
            declareInstance(B bb) {
        return declareInstance(newVarName(typeNameOf(shape)), bb);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> InstanceInfo
            declareInstance(String name, B bb) {
        return declareInstance(name, shape, bb);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> InstanceInfo
            declareInstance(String name, Shape shape, B bb) {
        return declareInstance(name, shape, bb, currentTypeName);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> InstanceInfo
            declareInstance(String name, Shape shape, B bb, String currentTypeName) {
        if (shape.isMemberShape()) {
            Shape nue = model.expectShape(shape.asMemberShape().get().getTarget());
            return declareInstance(name, nue, bb, currentTypeName, shape.asMemberShape().get());
        }
        return declareInstance(name, shape, bb, currentTypeName, null);
    }

    protected void ensureImported(Shape shape) {
        ensureImported(currentClassBuilder, shape);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> InstanceInfo
            declareInstance(String name, Shape shape, B bb,
                    String currentTypeName, MemberShape memberShape) {

        bb.lineComment("declare instance " + shape.getId() + " " + shape.getType());

        if ("smithy.api".equals(shape.getId().getNamespace())) {
            return new InstanceInfo(null, declarePrimitive(name, shape, bb));
        } else {
            ensureImported(shape);
        }

        String val = newVarName(name);
        switch (shape.getType()) {
            case ENUM:
                return new InstanceInfo(null, declarePrimitive(name, shape, bb));
            case STRUCTURE:
                StructureInstantiation struct = instantiateStructure(val,
                        shape.asStructureShape().get(), bb);
                return new InstanceInfo(null, val);
            case LIST:
            case SET:
                instantiateListOrSet(val, shape.asListShape().get(), bb, memberShape);
                return new InstanceInfo(null, val);
            case MAP:
                instantiateMap(val, shape.asMapShape().get(), bb, memberShape);
                return new InstanceInfo(null, val);
            case BOOLEAN:
                String src = declarePrimitive(name + "Contents", shape, bb);
                bb.declare(val)
                        .initializedTo()
                        .ternary()
                        .booleanExpression(src)
                        .expression(currentTypeName + ".TRUE")
                        .expression(currentTypeName + ".FALSE")
                        .as(currentTypeName);
                return new InstanceInfo(src, val);
            case INT_ENUM:
                IntEnumShape ienum = shape.asIntEnumShape().get();
                List<Integer> vals = new ArrayList<>(ienum.getEnumValues().values());
                if (!vals.isEmpty()) { // possible?
                    int value = vals.get(rnd.nextInt(vals.size()));
                    bb.declare(val)
                            .initializedByInvoking(decapitalize(typeNameOf(shape)))
                            .withArgument(value)
                            .on(typeNameOf(shape))
                            .as(typeNameOf(shape));
                }
                return new InstanceInfo(null, val);
            case UNION:
                UnionShape union = shape.asUnionShape().get();
                List<MemberShape> members = new ArrayList<>(union.members());
                int ix = rnd.nextInt(members.size());
                MemberShape target = members.get(ix);
                Shape theTarget = model.expectShape(target.getTarget());
                InstanceInfo member = declareInstance(name + "_unionMember", theTarget, bb, typeNameOf(theTarget), target);
                bb.lineComment("Randomly pick member " + ix + " of type " + typeNameOf(theTarget));
                String unionSubtype = currentTypeName + "With" + typeNameOf(theTarget);
                bb.declare(val)
                        .initializedWithNew(nb -> {
                            nb.withArgument(member.instanceVar).ofType(currentTypeName + "." + unionSubtype);
                        }).as(typeNameOf(union));
                return new InstanceInfo(member.instanceVar, val);

        }

        String src = declarePrimitiveFor(name + "Contents", bb, shape, memberShape);
        bb.declare(val).initializedWithNew(nb -> {
            nb.withArgument(src)
                    .ofType(currentTypeName);
        }).as(currentTypeName);
        return new InstanceInfo(src, val);
    }

    protected static class InstanceInfo {

        public final String contents;
        public final String instanceVar;

        public InstanceInfo(String contents, String instanceVar) {
            this.contents = contents;
            this.instanceVar = instanceVar;
        }

    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> String declarePrimitive(B bb) {
        return declarePrimitive(shape, bb);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> String declarePrimitive(Shape shape, B bb) {
        return declarePrimitive("testValue", bb);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T>
            String declarePrimitive(String name, B bb) {
        return declarePrimitive(name, shape, bb);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T>
            String declarePrimitive(String name, Shape shape, B bb) {
        String vn = newVarName(name);
        if (shape.getType() == ShapeType.MEMBER) {
            Shape nue = model.expectShape(shape.asMemberShape().get().getTarget());
            return declarePrimitiveFor(vn, bb, nue, shape);
        }
        return declarePrimitiveFor(vn, bb, shape);
    }

    protected int boundedRandomInt() {
        return boundedRandomInt(shape, null);
    }

    protected Optional<SamplesTrait> samples() {
        return samples(shape);
    }

    protected Optional<SamplesTrait> samples(Shape shape) {
        if (shape.isMemberShape()) {
            return samples(shape,
                    model.expectShape(shape.asMemberShape().get().getTarget()));
        }
        return samples(null, shape);
    }

    protected static Optional<SamplesTrait> samples(Shape memberShape, Shape shape) {
        notNull("shape", shape);
        if (memberShape != null) {
            return memberShape.getTrait(SamplesTrait.class)
                    .or(() -> shape.getTrait(SamplesTrait.class));
        }
        return shape.getTrait(SamplesTrait.class);
    }

    protected Optional<RangeTrait> range() {
        return range(shape);
    }

    protected Optional<RangeTrait> range(Shape shape) {
        if (shape.isMemberShape()) {
            return range(shape, model.expectShape(shape.asMemberShape().get().getTarget()));
        }
        return range(null, shape);
    }

    protected static Optional<RangeTrait> range(Shape memberShape, Shape shape) {
        if (memberShape != null) {
            return memberShape.getTrait(RangeTrait.class)
                    .or(() -> shape.getTrait(RangeTrait.class));
        }
        return shape.getTrait(RangeTrait.class);
    }

    protected Optional<LengthTrait> length() {
        return length(shape);
    }

    protected Optional<LengthTrait> length(Shape shape) {
        if (shape.isMemberShape()) {
            return length(shape, model.expectShape(shape.asMemberShape().get().getTarget()));
        }
        return length(null, shape);
    }

    protected static Optional<LengthTrait> length(Shape memberShape, Shape shape) {
        if (memberShape != null) {
            return memberShape.getTrait(LengthTrait.class)
                    .or(() -> shape.getTrait(LengthTrait.class));
        }
        return shape.getTrait(LengthTrait.class);
    }

    protected int boundedRandomInt(Shape shape) {
        if (shape.isMemberShape()) {
            return boundedRandomInt(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return boundedRandomInt(shape, null);
    }

    protected short boundedRandomShort(Shape shape, Shape memberShape) {
        Optional<RangeTrait> rng = range(memberShape, shape);
        if (!rng.isPresent()) {
            int val = rnd.nextInt(Short.MAX_VALUE - Short.MIN_VALUE);
            return (short) (Short.MIN_VALUE + val);
        }
        Optional<BigDecimal> minOpt = rng.get().getMin();
        Optional<BigDecimal> maxOpt = rng.get().getMax();

        int min = minOpt.isPresent() ? minOpt.get().intValue()
                : Short.MIN_VALUE;
        int max = maxOpt.isPresent() ? maxOpt.get().intValue()
                : Short.MAX_VALUE;

        if (min == Short.MIN_VALUE && max == Short.MAX_VALUE) {
            return (short) (rnd.nextInt(Short.MAX_VALUE - Short.MIN_VALUE) + Short.MIN_VALUE);
        }

        int range = max - min;
        if (range < 0) {
            range *= -1;
        }
        short v = (short) (rnd.nextInt(range));
        return (short) (min + v);
    }

    protected int boundedRandomInt(Shape shape, Shape memberShape) {
        Optional<RangeTrait> rng = range(memberShape, shape);
        if (!rng.isPresent()) {
            return rnd.nextInt();
        }
        Optional<BigDecimal> minOpt = rng.get().getMin();
        Optional<BigDecimal> maxOpt = rng.get().getMax();

        int min = minOpt.isPresent() ? minOpt.get().intValue()
                : Integer.MIN_VALUE;
        int max = maxOpt.isPresent() ? maxOpt.get().intValue()
                : Integer.MAX_VALUE;

        if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE) {
            return rnd.nextInt();
        }

        int range = max - min;
        if (range < 0) {
            range *= -1;
        }
        return min + rnd.nextInt(range);
    }

    protected long boundedRandomLong() {
        if (shape.isMemberShape()) {
            return boundedRandomLong(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return boundedRandomLong(shape, null);
    }

    protected long boundedRandomLong(Shape shape, Shape memberShape) {
        Optional<RangeTrait> rng = range(memberShape, shape);
        if (!rng.isPresent()) {
            return rnd.nextInt();
        }
        Optional<BigDecimal> minOpt = rng.get().getMin();
        Optional<BigDecimal> maxOpt = rng.get().getMax();

        long min = minOpt.isPresent() ? minOpt.get().longValue()
                : Long.MIN_VALUE;
        long max = maxOpt.isPresent() ? maxOpt.get().longValue()
                : Long.MAX_VALUE;

        if (min == Long.MIN_VALUE && max == Long.MAX_VALUE) {
            return rnd.nextLong();
        }

        long range = max - min;
        if (range < 0) {
            range *= -1;
        }
        return min + ((long) (rnd.nextDouble() * range));
    }

    protected short boundedRandomShort() {
        return boundedRandomShort(shape);
    }

    protected short boundedRandomShort(Shape shape) {
        if (shape.isMemberShape()) {
            return (short) boundedRandomInt(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return (short) boundedRandomShort(shape, null);
    }

    protected byte boundedRandomByte() {
        if (shape.isMemberShape()) {
            return (byte) boundedRandomInt(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return (byte) boundedRandomInt(shape, null);
    }

    protected byte boundedRandomByte(Shape shape) {
        if (shape.isMemberShape()) {
            return (byte) boundedRandomInt(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return (byte) boundedRandomInt(shape, null);
    }

    protected byte boundedRandomByte(Shape shape, Shape memberShape) {
        return (byte) boundedRandomInt(shape, memberShape);
    }

    protected double boundedRandomDouble() {
        if (shape.isMemberShape()) {
            return boundedRandomDouble(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return boundedRandomDouble(shape, null);
    }

    protected float boundedRandomFloat() {
        return boundedRandomFloat(shape);
    }

    protected float boundedRandomFloat(Shape shape) {
        if (shape.isMemberShape()) {
            return (float) boundedRandomDouble(model.expectShape(shape.asMemberShape().get().getTarget()), shape);
        }
        return (float) boundedRandomDouble(shape, null);
    }

    protected float boundedRandomFloat(Shape shape, Shape memberShape) {
        return (float) boundedRandomDouble(shape, memberShape);
    }

    protected double boundedRandomDouble(Shape shape, Shape memberShape) {
        Optional<RangeTrait> rng = range(memberShape, shape);
        if (!rng.isPresent()) {
            return rnd.nextDouble();
        }
        Optional<BigDecimal> minOpt = rng.get().getMin();
        Optional<BigDecimal> maxOpt = rng.get().getMax();

        double min = minOpt.isPresent() ? minOpt.get().longValue()
                : Double.MIN_VALUE;
        double max = maxOpt.isPresent() ? maxOpt.get().intValue()
                : Double.MAX_VALUE;

        if (min == Double.MIN_VALUE && max == Double.MAX_VALUE) {
            return rnd.nextDouble();
        }

        double range = max - min;
        if (range < 0) {
            range *= -1;
        }
        return min + (rnd.nextDouble() * range);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T>
            String declarePrimitiveFor(String name, B bb, Shape shape) {
        if (shape.isMemberShape()) {
            return declarePrimitiveFor(name, bb, model.expectShape(shape.asMemberShape().get().getTarget()),
                    shape);
        }
        return declarePrimitiveFor(name, bb, shape, null);
    }

    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T>
            String declarePrimitiveFor(String name, B bb,
                    Shape shape, Shape memberShape) {

        if (shape.isMemberShape()) {
            Shape nue = model.expectShape(shape.asMemberShape().get().getTarget());
            return declarePrimitiveFor(name, bb, nue, shape);
        }
        bb.lineComment("Shape " + shape);
        bb.lineComment("Member " + memberShape);
        bb.lineComment("Range " + range(memberShape, shape));
        range(memberShape, shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                bb.lineComment("Range min: " + min);
            });
            rng.getMax().ifPresent(max -> {
                bb.lineComment("Range max: " + max);
            });
        });

        String vn = newVarName(name);
        switch (shape.getType()) {
            case BOOLEAN:
                bb.declare(vn).initializedTo(rnd.nextBoolean());
                break;
            case INTEGER:
                bb.declare(vn).initializedTo(boundedRandomInt(shape, memberShape));
                break;
            case LONG:
                bb.declare(vn).initializedTo(boundedRandomLong(shape, memberShape));
                break;
            case BYTE:
                bb.declare(vn).initializedTo(boundedRandomByte(shape, memberShape));
                break;
            case SHORT:
                bb.declare(vn).initializedTo(boundedRandomShort(shape, memberShape));
                break;
            case FLOAT:
                bb.declare(vn).initializedTo(boundedRandomFloat(shape, memberShape));
                break;
            case DOUBLE:
                bb.declare(vn).initializedTo(boundedRandomDouble(shape, memberShape));
                break;
            case BIG_DECIMAL:
                currentClassBuilder.importing(BigDecimal.class);
                bb.declare(vn).initializedWithNew(nb -> {
                    nb.withStringLiteral(Double.toString(boundedRandomDouble(shape, memberShape)))
                            .ofType("BigDecimal");
                }).as("BigDecimal");
                break;
            case BIG_INTEGER:
                currentClassBuilder.importing(BigInteger.class);
                bb.declare(vn).initializedWithNew(nb -> {
                    nb.withStringLiteral(Long.toString(boundedRandomLong(shape, memberShape)))
                            .ofType("BigInteger");
                }).as("BigInteger");
                break;
            case TIMESTAMP:
                long millisSinceEpoch = System.currentTimeMillis();
                Instant when = Instant.EPOCH
                        .plus(Duration.ofMillis((long) ceil(rnd.nextDouble() * millisSinceEpoch)));
                currentClassBuilder.importing(Instant.class);
                bb.declare(vn).initializedByInvoking("parse")
                        .withStringLiteral(when.toString())
                        .on("Instant")
                        .as("Instant");
                break;
            case STRING:
                bb.lineComment("Shape " + shape.getId());
                bb.lineComment("MemberShape " + memberShape);
                bb.declare(vn).initializedWithStringLiteral(boundedRandomString(shape, memberShape)).as("String");
                break;
            case LIST:
            case SET:
                instantiateListOrSet(vn, shape.asListShape().get(), bb, memberShape);
                break;
            case MAP:
                instantiateMap(vn, shape.asMapShape().get(), bb, memberShape);
                break;
            case ENUM:
                List<Map.Entry<String, MemberShape>> entries = new ArrayList<>(shape.asEnumShape().get().getAllMembers().entrySet());
                Collections.shuffle(entries, rnd);
                Map.Entry<String, MemberShape> memberEntry = entries.get(rnd.nextInt(entries.size()));
                bb.declare(vn)
                        .initializedFromField(JavaSymbolProvider.escape(memberEntry.getKey()))
                        .of(TypeNames.typeNameOf(shape))
                        .as(TypeNames.typeNameOf(shape));
                break;
            case INT_ENUM:
                IntEnumShape ienum = shape.asIntEnumShape().get();
                List<Integer> vals = new ArrayList<>(ienum.getEnumValues().values());
                if (!vals.isEmpty()) { // possible?
                    int value = vals.get(rnd.nextInt(vals.size()));
                    bb.declare(vn).initializedTo(value);
//                    bb.declare(vn)
//                            .initializedByInvoking(decapitalize(typeNameOf(shape)))
//                            .withArgument(value)
//                            .on(typeNameOf(shape))
//                            .as(typeNameOf(shape));
                }
                break;
            default:
                throw new AssertionError("Cannot generate a random value for "
                        + shape.getId() + " for " + this.shape.getId()
                        + " of type " + shape.getType());
        }
        return vn;
    }

    private int targetSizeForCollection(Shape shape, Shape memberShape) {
        int targetSize = rnd.nextInt(5) + 3;
        Optional<LengthTrait> len = length(memberShape, shape);
        if (len.isPresent() && len.get().getMin().isPresent()) {
            targetSize = Math.max(targetSize, len.get().getMin().get().intValue()
                    + 1);
        }
        if (len.isPresent() && len.get().getMax().isPresent()) {
            int max = len.get().getMax().get().intValue();
            targetSize = Math.min(max, targetSize);
        }
        return targetSize;
    }

    private <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void
            instantiateListOrSet(String vn, ListShape shape, B bb,
                    Shape memberShape) {
        boolean isSet = shape.getTrait(UniqueItemsTrait.class)
                .isPresent() || (memberShape != null
                && memberShape.getTrait(UniqueItemsTrait.class).isPresent());

        int targetSize = targetSizeForCollection(shape, memberShape);
        bb.lineComment("List or set " + shape.getId());
        bb.lineComment("Target size " + targetSize);
        bb.lineComment("Shape " + shape);
        bb.lineComment("Member shape " + memberShape);
        length(memberShape, shape).ifPresent(len -> {
            len.getMin().ifPresent(min -> {
                bb.lineComment("Min length " + min);
            });
            len.getMax().ifPresent(max -> {
                bb.lineComment("Max length " + max);
            });
        });

        String base = memberShape == null ? shape.getId().getName()
                : memberShape.asMemberShape().get().getMemberName();
        MemberShape shapeMember = shape.getMember();
        Shape memberTarget = model.expectShape(shapeMember.getTarget());
        ensureImported(memberTarget);
        String memberTypeName = TypeNames.typeNameOf(memberTarget.getId(), false);

        String inputCollection = newVarName(shape.getId().getName() + "_input");
        String collectionType = isSet ? "Set<" + memberTypeName + ">"
                : "List<" + memberTypeName + ">";
        String collectionConcreteType = isSet ? "HashSet<>"
                : "ArrayList<>";
        if (isSet) {
            currentClassBuilder.importing(Set.class, HashSet.class);
        } else {
            currentClassBuilder.importing(List.class, ArrayList.class);
        }

        int sz = targetSize;
        bb.declare(inputCollection)
                .initializedWithNew(nb -> {
                    nb.withArgument(sz)
                            .ofType(collectionConcreteType);
                }).as(collectionType);

        for (int i = 0; i < targetSize; i++) {
            String item = newVarName(base);
            String v = declareInstance(item, shapeMember, bb, memberTypeName).instanceVar;
            bb.invoke("add")
                    .withArgument(v)
                    .on(inputCollection);
        }
        String destTypeName = TypeNames.typeNameOf(shape);
        bb.declare(vn)
                .initializedWithNew(nb -> {
                    nb.withArgument(inputCollection)
                            .ofType(destTypeName);
                }).as(destTypeName);
    }

    private <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void
            instantiateMap(String vn, MapShape shape, B bb,
                    Shape memberShape) {

        MemberShape keyShape = shape.getKey();
        MemberShape valShape = shape.getValue();

        Shape rawKeyShape = model.expectShape(keyShape.getTarget());
        Shape rawValShape = model.expectShape(valShape.getTarget());

        ensureImported(rawKeyShape);
        ensureImported(rawValShape);

        int targetSize = targetSizeForCollection(shape, memberShape);

        String keyTypeName = TypeNames.typeNameOf(rawKeyShape.getId(), false);
        String valTypeName = TypeNames.typeNameOf(rawValShape.getId(), false);

        currentClassBuilder.importing(Map.class, LinkedHashMap.class);
        String mapType = "Map<" + keyTypeName + ", " + valTypeName + ">";
        String contents = vn + "Contents";
        currentClassBuilder.importing(HashMap.class, Map.class);
        bb.declare(contents)
                .initializedWithNew(nb -> {
                    nb.withArgument(targetSize)
                            .ofType("HashMap<>");
                }).as(mapType);
        String keyBase = vn + "Key";
        String valBase = vn + "Val";
        for (int i = 0; i < targetSize; i++) {
            String k = declareInstance(keyBase + i, keyShape, bb, keyTypeName).instanceVar;
            String v = declareInstance(valBase + i, valShape, bb, valTypeName).instanceVar;
            bb.invoke("put")
                    .withArgument(k).withArgument(v)
                    .on(contents);
        }
        String destTypeName = TypeNames.typeNameOf(shape);
        bb.declare(vn)
                .initializedWithNew(nb -> {
                    nb.withArgument(contents)
                            .ofType(destTypeName);
                }).as(destTypeName);
    }

    protected String boundedRandomString() {
        return boundedRandomString(shape);
    }

    protected String boundedRandomString(Shape shape) {
        return boundedRandomString(shape, null);
    }

    protected String boundedRandomString(Shape shape, Shape memberShape) {
        List<String> validSamples = validStringSamples(memberShape, shape);
        if (!validSamples.isEmpty()) {
            return validSamples.get(rnd.nextInt(validSamples.size()));
        }

        RandomStrings rs = new RandomStrings(rnd);
        Optional<LengthTrait> len = length(memberShape, shape);
        if (len.isPresent()) {
            int min = len.get().getMin().orElse(0L).intValue();
            int max = len.get().getMax().orElse(0L).intValue();
            int targetLength = Math.min(256, max - min) + 1;
            if (targetLength < 0) {
                // min is specified, max is not:
                max = min + rnd.nextInt(12);
                targetLength = max - min;
            }
            if (targetLength <= 0) {
                targetLength = Math.max(1, Math.min(min, max - 1));
            }
            return rs.get(min + rnd.nextInt(targetLength))
                    .toLowerCase();
        }
        return rs.get(12).toLowerCase().replaceAll("-", "a");
    }

    protected String underlyingType(Shape shape) {
        if (shape.isMemberShape()) {
            shape = model.expectShape(shape.asMemberShape().get().getTarget());
        }
        JavaTypes jt = JavaTypes.forShapeType(shape.getType());
        String result = jt == null ? null : jt
                .primitiveTypeName();
        if (result == null) {
            result = TypeNames.typeNameOf(shape.getId(), false);
        }
        return result;
    }

    protected List<String> validStringSamples() {
        return samples().map(samps -> samps.validSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)))
                .orElse(emptyList());
    }

    protected List<String> invalidStringSamples() {
        return samples().map(samps -> samps.invalidSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)))
                .orElse(emptyList());
    }

    protected List<String> validStringSamples(Shape shape) {
        MemberShape ms = null;
        if (shape.isMemberShape()) {
            ms = shape.asMemberShape().get();
            shape = model.expectShape(ms.getTarget());
            return validStringSamples(ms, shape);
        }
        return validStringSamples(null, shape);
    }

    protected List<String> validStringSamples(Shape memberShape, Shape shape) {
        return samples(memberShape, shape).map(samps -> samps.validSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)))
                .orElse(emptyList());
    }

    protected List<String> invalidStringSamples(Shape shape) {
        MemberShape ms = null;
        if (shape.isMemberShape()) {
            ms = shape.asMemberShape().get();
            shape = model.expectShape(ms.getTarget());
        }
        return invalidStringSamples(ms, shape);
    }

    protected List<String> invalidStringSamples(Shape ms, Shape shape) {
        return samples(ms, shape).map(samps -> samps.invalidSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)))
                .orElse(emptyList());
    }

    protected void withOutOfRangeInts(Shape shape, IntConsumer c) {
        range(shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                if (min.intValue() > Integer.MIN_VALUE + 17) {
                    c.accept(min.intValue() - 17);
                }
                if (min.intValue() > Integer.MIN_VALUE) {
                    c.accept(min.intValue() - 1);
                }
            });
            rng.getMax().ifPresent(max -> {
                if (max.intValue() > Integer.MAX_VALUE - 17) {
                    c.accept(max.intValue() + 17);
                }
                if (max.intValue() < Integer.MAX_VALUE) {
                    c.accept(max.intValue() + 1);
                }
            });
        });
    }

    protected void withOutOfRangeLongs(Shape shape, LongConsumer c) {
        range(shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                if (min.longValue() > Long.MIN_VALUE + 17) {
                    c.accept(min.longValue() - 17);
                }
                if (min.longValue() > Integer.MIN_VALUE) {
                    c.accept(min.longValue() - 1);
                }
            });
            rng.getMax().ifPresent(max -> {
                if (max.longValue() > Long.MAX_VALUE - 17) {
                    c.accept(max.longValue() + 17);
                }
                if (max.longValue() < Long.MAX_VALUE) {
                    c.accept(max.longValue() + 1);
                }
            });
        });
    }

    protected void withOutOfRangeShorts(Shape shape, ShortConsumer c) {
        range(shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                if (min.shortValue() > Short.MIN_VALUE + 17) {
                    c.accept((short) (min.shortValue() - 17));
                }
                if (min.shortValue() > Short.MIN_VALUE) {
                    c.accept((short) (min.shortValue() - 1));
                }
            });
            rng.getMax().ifPresent(max -> {
                if (max.shortValue() > Short.MAX_VALUE - 17) {
                    c.accept((short) (max.shortValue() + 17));
                }
                if (max.shortValue() < Long.MAX_VALUE) {
                    c.accept((short) (max.shortValue() + 1));
                }
            });
        });
    }

    protected void withOutOfRangeBytes(Shape shape, ByteConsumer c) {
        range(shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                if (min.byteValue() > Byte.MIN_VALUE + 17) {
                    c.accept((byte) (min.byteValue() - 17));
                }
                if (min.byteValue() > Byte.MIN_VALUE) {
                    c.accept((byte) (min.byteValue() - 1));
                }
            });
            rng.getMax().ifPresent(max -> {
                if (max.byteValue() > Byte.MAX_VALUE - 17) {
                    c.accept((byte) (max.byteValue() + 17));
                }
                if (max.byteValue() < Byte.MAX_VALUE) {
                    c.accept((byte) (max.byteValue() + 1));
                }
            });
        });
    }

    protected void withOutOfRangeDoubles(Shape shape, DoubleConsumer c) {
        range(shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                if (min.doubleValue() > Double.MIN_VALUE + 17) {
                    c.accept((double) (min.doubleValue() - 17));
                }
                if (min.doubleValue() > Double.MIN_VALUE) {
                    c.accept((double) (min.doubleValue() - 0.512391));
                }
            });
            rng.getMax().ifPresent(max -> {
                if (max.doubleValue() > Double.MAX_VALUE - 17) {
                    c.accept((double) (max.doubleValue() + 17));
                }
                if (max.doubleValue() < Double.MAX_VALUE - 1) {
                    c.accept((double) (max.doubleValue() + 0.732398424));
                }
            });
        });
    }

    protected void withOutOfRangeFloats(Shape shape, DoubleConsumer c) {
        range(shape).ifPresent(rng -> {
            rng.getMin().ifPresent(min -> {
                if (min.floatValue() > Float.MIN_VALUE + 17) {
                    c.accept((double) (min.floatValue() - 17));
                }
                if (min.floatValue() > Float.MIN_VALUE) {
                    c.accept((double) (min.floatValue() - 0.512391));
                }
            });
            rng.getMax().ifPresent(max -> {
                if (max.floatValue() > Float.MAX_VALUE - 17) {
                    c.accept((float) (max.floatValue() + 17));
                }
                if (max.floatValue() < Float.MAX_VALUE - 1) {
                    c.accept((float) (max.floatValue() + 0.732398424));
                }
            });
        });
    }

    protected <B extends BlockBuilderBase<T, B, ?>, T>
            StructureInstantiation instantiateStructure(String name,
                    StructureShape shape, B bb) {
        if (name == null) {
            name = newVarName(typeNameOf(shape));
        }
        ensureImported(shape);
        List<MemberInstantiation<?>> members = new ArrayList<>();
        StructureInstantiation result
                = new StructureInstantiation(name, shape, members);

        bb.lineComment("Instantiate a " + typeNameOf(shape));
        bb.declare(name)
                .initializedWithNew(nb -> {
                    for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {

                        MemberInstantiation<?> m = instantiateStructureMember(e.getValue(),
                                shape, bb);

                        bb.lineComment("XX-Member " + typeNameOf(shape) + ": " + e.getKey() + " " + e.getValue().getId()
                                + " memberVar " + m.memberVar);

                        members.add(m);
                        Optional<JavaTypes> tp = m.member == null
                                ? Optional.empty() : m.member.javaType();
                        nb.withArgument(m.memberVar);
                        /*
                        tp.ifPresentOrElse(jt -> {
                            if (jt.isPrimitiveCapable()) {
                                nb.withArgument("(" + jt.javaTypeName()
                                        + ") " + m.memberVar);
                            } else {
                                nb.withArgument(m.memberVar);
                            }
                        },
                                () -> {
                                    nb.withArgument(m.memberVar);
                                });
                         */

                    }
                    nb.ofType(TypeNames.typeNameOf(shape));
                }).as(TypeNames.typeNameOf(shape));

        return result;
    }

    protected <B extends BlockBuilderBase<T, B, ?>, T>
            MemberInstantiation<?> instantiateStructureMember(MemberShape member,
                    StructureShape shape, B bb) {
        ShapeId tgt = member.getTarget();
        Shape target = model.expectShape(tgt);

        ensureImported(target);
        ensureImported(shape);

        ShapeType targetType = target.getType();
        StructureMember<?> m = StructureMember.create(member, target, helper(shape));
        String qtn = m.qualifiedTypeName();
        if (qtn.indexOf('.') > 0 && !qtn.startsWith("java.lang.")) {
            currentClassBuilder.importing(qtn);
        }

        String memberVar = newVarName(JavaSymbolProvider.escape(member.getMemberName()));
        String contentsVar = null;
        if (target.isStructureShape()) {
            bb.lineComment("instantiate " + m.typeName() + " for " + m.member().getId());
            StructureInstantiation inst = instantiateStructure(memberVar, target.asStructureShape().get(), bb);
            return new MemberInstantiation<>(m, null, inst.name);
        } else if (!m.isSmithyApiDefinedType()) {
            bb.lineComment("instantiate member " + m.typeName() + " for " + m.member().getId());
            InstanceInfo info = declareInstance(m.field(), target, bb, m.typeName(), member);
            return new MemberInstantiation<>(m, info.contents, info.instanceVar);
        } else {
            bb.lineComment("instantiate prim member " + m.typeName() + " for " + m.member().getId());
            String v = declarePrimitiveFor(member.getMemberName(), bb, target, member);
            return new MemberInstantiation<>(m, null, v);
        }
    }

    protected static class StructureInstantiation {

        public final StructureShape shape;
        public final List<MemberInstantiation<?>> members;
        public final String name;

        public StructureInstantiation(String name, StructureShape shape, List<MemberInstantiation<?>> members) {
            this.shape = shape;
            this.members = members;
            this.name = name;
        }

        public MemberInstantiation<?> find(MemberShape mem) {
            for (MemberInstantiation<?> in : members) {
                if (in.member.member().getId().equals(mem.getId())) {
                    return in;
                }
            }
            return null;
        }

    }

    protected static class MemberInstantiation<S extends Shape> {

        public final String contentsVar;
        public final String memberVar;
        public final StructureMember<S> member;

        public MemberInstantiation(StructureMember<S> member, String contentsVar, String memberVar) {
            this.member = member;
            this.contentsVar = contentsVar;
            this.memberVar = memberVar;
        }

    }

    private final Map<StructureShape, HelperImpl> helperForStructure = new HashMap<>();

    private HelperImpl helper(StructureShape shape) {
        return helperForStructure.computeIfAbsent(shape, shp
                -> new HelperImpl(shp));
    }

    class HelperImpl implements StructureGenerationHelper {

        private final List<StructureMember<?>> members = new ArrayList<>();
        private final Namer namer = Namer.getDefault();
        private final StructureShape shape;

        HelperImpl(StructureShape shape) {
            this.shape = shape;
            shape.members().forEach(mem -> {
                Shape target = model.expectShape(mem.getTarget());
                members.add(StructureMember.create(mem, target, this));
            });
        }

        @Override
        public boolean isOmitted(MemberShape shape) {
            return false;
        }

        public Namer namer() {
            return namer;
        }

        @Override
        public <T> void generateNullCheck(String variable, BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on) {
            AbstractJavaTestGenerator.this.generateNullCheck(variable, bb, on);
        }

        @Override
        public <B extends BlockBuilderBase<T, B, ?>, T> void generateEqualityCheckOfNullable(String v, String compareWith, B bldr) {
            AbstractJavaTestGenerator.this.generateEqualityCheckOfNullable(v, compareWith, bldr);
        }

        @Override
        public Model model() {
            return AbstractJavaTestGenerator.this.model;
        }

        @Override
        public StructureShape structure() {
            return shape;
        }

        @Override
        public SmithyGenerationContext context() {
            return AbstractJavaTestGenerator.this.ctx();
        }

        @Override
        public List<StructureMember<?>> members() {
            return unmodifiableList(members);
        }

        @Override
        public ValidationExceptionProvider validation() {
            return AbstractJavaTestGenerator.this.validationExceptions();
        }

        @Override
        public <T, R> String generateInitialEqualsTest(ClassBuilder<R> cb, ClassBuilder.BlockBuilder<T> bb) {
            return AbstractJavaTestGenerator.this.generateInitialEqualsTest(cb, bb);
        }

        @Override
        public void maybeImport(ClassBuilder<?> cb, String... fqns) {
            List<String> fq = new ArrayList<>(Arrays.asList(fqns));
            // Prune imports from the same package
            for (Iterator<String> it = fq.iterator(); it.hasNext();) {
                String s = it.next();
                String pk = packageOf(s);
                if (shape.getId().getNamespace().equals(pk)) {
                    it.remove();
                }
            }
            if (fq.isEmpty()) {
                return;
            }
            AbstractJavaGenerator.maybeImport(cb, fq.toArray(String[]::new));
        }
    }

    protected boolean hasTimestampInClosure(Shape shape) {
        return hasTimestampInClosure(shape, model, new HashSet<>());
    }

    private static boolean hasTimestampInClosure(Shape shape, Model mdl, Set<ShapeId> seen) {
        if (seen.contains(shape.getId())) {
            return false;
        }
        seen.add(shape.getId());
        if (shape.getType() == ShapeType.TIMESTAMP) {
            return true;
        }
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            if (hasTimestampInClosure(mdl.expectShape(e.getValue().getTarget()), mdl, seen)) {
                return true;
            }
        }
        return false;
    }

}
