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
package com.telenav.smithy.java.generators.base;

import com.mastfrog.function.ByteConsumer;
import com.mastfrog.function.ShortConsumer;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.forPackage;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.java.generators.builtin.struct.Namer;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.impl.Registry;
import com.telenav.smithy.validation.ValidationExceptionProvider;
import com.telenav.smithy.extensions.SamplesTrait;
import com.telenav.smithy.extensions.SpanTrait;
import com.mastfrog.util.strings.RandomStrings;
import com.telenav.smithy.names.JavaTypes;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.telenav.smithy.generators.GenerationSwitches.DEBUG;
import static com.telenav.smithy.java.generators.base.AbstractJavaTestGenerator.MemberInstantiation.memberInstantiation;

import com.telenav.smithy.java.generators.size.ObjectSizes;
import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import static com.telenav.smithy.names.JavaTypes.forShapeType;
import static com.telenav.smithy.names.TypeNames.packageOf;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import com.telenav.smithy.rex.Xeger;
import com.telenav.smithy.utils.ShapeUtils;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.currentTimeMillis;
import static java.time.Instant.EPOCH;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.shuffle;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.empty;
import static javax.lang.model.element.Modifier.*;
import static software.amazon.smithy.model.shapes.ShapeType.MEMBER;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.traits.PatternTrait;

/**
 * Base class for generators for JUnit 5 tests of classes that implement model
 * shapes.
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
        String typeName = currentTypeName = typeNameOf(shape.getId());

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
        ClassBuilder<String> result = forPackage(names().packageOf(shape))
                .named(typeNameOf(shape) + "Test")
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
            Registry.applyGeneratedAnnotation(getClass(), result);
        }

        return result;
    }

    private final Map<String, Integer> usedVars = new HashMap<>();

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
        return escape(decapitalize(prefix) + capitalize(Integer.toHexString(suffix)));
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
        if (shape.getType() == MEMBER) {
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
                long millisSinceEpoch = currentTimeMillis();
                Instant when = EPOCH
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
                shuffle(entries, rnd);
                Map.Entry<String, MemberShape> memberEntry = entries.get(rnd.nextInt(entries.size()));
                bb.declare(vn)
                        .initializedFromField(escape(memberEntry.getKey()))
                        .of(typeNameOf(shape))
                        .as(typeNameOf(shape));
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
            targetSize = max(targetSize, len.get().getMin().get().intValue()
                    + 1);
        }
        if (len.isPresent() && len.get().getMax().isPresent()) {
            int max = len.get().getMax().get().intValue();
            targetSize = min(max, targetSize);
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
        String memberTypeName = typeNameOf(memberTarget.getId(), false);

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
        String destTypeName = typeNameOf(shape);
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

        String keyTypeName = typeNameOf(rawKeyShape.getId(), false);
        String valTypeName = typeNameOf(rawValShape.getId(), false);

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
        String destTypeName = typeNameOf(shape);
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
            int targetLength = min(256, max - min) + 1;
            if (targetLength < 0) {
                // min is specified, max is not:
                max = min + rnd.nextInt(12);
                targetLength = max - min;
            }
            if (targetLength <= 0) {
                targetLength = max(1, min(min, max - 1));
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
        JavaTypes jt = forShapeType(shape.getType());
        String result = jt == null ? null : jt
                .primitiveTypeName();
        if (result == null) {
            result = typeNameOf(shape.getId(), false);
        }
        return result;
    }

    protected List<String> validStringSamples() {
        return samples().map(samps -> samps.validSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)))
                .or(() -> shape.getTrait(PatternTrait.class)
                .map(pat -> new Xeger(pat.getValue()).emitChecked(rnd, 20)
                .map(Arrays::asList).orElse(emptyList())
                )).orElse(emptyList());
    }

    protected List<String> invalidStringSamples() {
        Optional<List<String>> a = samples().map(sam -> sam.invalidSamples(s -> s.asStringNode().get().getValue()));
        return a.or(() -> shape.getTrait(PatternTrait.class).map(pat -> new Xeger(pat.getValue()))
                .flatMap(xe -> xe.confound().flatMap(confounded -> {
            for (int i = 0; i < 10; i++) {
                String antiExample = confounded.emit(rnd);
                if (!xe.matches(antiExample)) {
                    System.out.println("XEGER CONFOUND SAMPLE " + i + ": '" + antiExample + "'");
                    return Optional.of(Arrays.asList(antiExample));
                }
            }
            return Optional.empty();
        }))).orElse(emptyList());
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
        Optional<List<String>> a = samples(memberShape, shape).map(samps -> samps.validSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)));
        return a.or(() -> {
            Optional<PatternTrait> pattern = memberShape == null ? shape.getTrait(PatternTrait.class)
                    : memberShape.getTrait(PatternTrait.class).or(() -> shape.getTrait(PatternTrait.class));
            return pattern.flatMap(pat -> {
                return new Xeger(pat.getValue()).emitChecked(rnd, 20)
                        .map(Arrays::asList);
            });
        }).orElse(emptyList());
    }

    protected List<String> invalidStringSamples(Shape shape) {
        MemberShape ms = null;
        if (shape.isMemberShape()) {
            ms = shape.asMemberShape().get();
            shape = model.expectShape(ms.getTarget());
        }
        return invalidStringSamples(ms, shape);
    }

    protected List<String> invalidStringSamples(Shape memberShape, Shape shape) {
        Optional<List<String>> a = samples(memberShape, shape).map(samps -> samps.invalidSamples(nd
                -> nd.asStringNode().map(StringNode::getValue).orElse(null)));

        return a.or(() -> {
            Optional<PatternTrait> pattern = memberShape == null ? shape.getTrait(PatternTrait.class)
                    : memberShape.getTrait(PatternTrait.class).or(() -> shape.getTrait(PatternTrait.class));
            return pattern.flatMap(pat -> {
                Xeger xe = new Xeger(pat.getValue());
                return xe.confound()
                        .map(confounded -> {
                            for (int i = 0; i < 10; i++) {
                                String antiExample = confounded.emit(rnd);
                                System.out.println("XEGER CONFOUND SAMPLE " + i + ": '" + antiExample + "'");
                                if (!xe.matches(antiExample)) {
                                    return Arrays.asList(antiExample);
                                }
                            }
                            return emptyList();
                        });
            });
        }).orElse(emptyList());
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

    private void ensureGreaterLesserOfMethods() {
        if (!currentClassBuilder.containsMethodNamed("lesserOf")) {
            currentClassBuilder.method("lesserOf", mth -> {
                mth.docComment("Used when instantiating random values for "
                        + "members using SpanTrait, where one must be greater than "
                        + "the other, to swap the values so that is actually the case.");
                mth.withModifier(PRIVATE, STATIC, FINAL)
                        .withTypeParam("T extends Comparable<T>")
                        .addArgument("T", "a")
                        .addArgument("T", "b")
                        .returning("T");
                mth.body(bb -> {
                    currentClassBuilder.importing(Objects.class);
                    bb.iff(invocationOf("equals").withArgument("a").withArgument("b").on("Objects"))
                            .returning("a").endIf();
                    bb.iff(invocationOf("compareTo").withArgument("b").on("a").isLessThan(number(0)))
                            .returning("a")
                            .orElse().returning("b")
                            .endIf();
                });
            });
            currentClassBuilder.method("greaterOf", mth -> {
                mth.docComment("Used when instantiating random values for "
                        + "members using SpanTrait, where one must be greater than "
                        + "the other, to swap the values so that is actually the case.");
                mth.withModifier(PRIVATE, STATIC, FINAL)
                        .withTypeParam("T extends Comparable<T>")
                        .addArgument("T", "a")
                        .addArgument("T", "b")
                        .returning("T");
                mth.body(bb -> {
                    currentClassBuilder.importing(Objects.class);
                    bb.iff(invocationOf("equals").withArgument("a").withArgument("b").on("Objects"))
                            .returning("a").endIf();
                    bb.iff(invocationOf("compareTo").withArgument("b").on("a").isLessThan(number(0)))
                            .returning("b")
                            .orElse().returning("a")
                            .endIf();
                });
            });
        }
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

        // Handle the case where we're generating a test of a structure with the
        // @span trait - we need to GUARANTEE that the member that must be greater
        // really always is.  So in this case, we compare the two values (which
        // must be of the same type) and swap which ones we use if the greater
        // is lesser
        SpanTrait span = shape.getTrait(SpanTrait.class).orElse(null);
        String lesserMemberName;
        String greaterMemberName;
        MemberInstantiation<?> lesserMemberInstantiation;
        MemberInstantiation<?> greaterMemberInstantiation;
        if (span != null) {
            lesserMemberName = span.lesser();
            greaterMemberName = span.greater();
            MemberShape lesserMember = shape.getAllMembers().get(lesserMemberName);
            MemberShape greaterMember = shape.getAllMembers().get(greaterMemberName);
            MemberInstantiation<?> origLesserMemberInstantiation = instantiateStructureMember(lesserMember,
                    shape, bb);
            MemberInstantiation<?> origGreaterMemberInstantiation = instantiateStructureMember(greaterMember,
                    shape, bb);
            ensureGreaterLesserOfMethods();
            bb.blankLine().lineComment("We have a @span trait: " + span)
                    .lineComment("If our random values for the lesser and greater members")
                    .lineComment("are reversed, we swap them here so we do not accidentally")
                    .lineComment("generate a test that will always fail.");
            bb.blankLine().lineComment("PENDING: It *is* possible to generate tests with")
                    .lineComment("spurious failures in the case that the two random values are exactly ")
                    .lineComment("equal (rarely).  For the moment, the solution is simply to ")
                    .lineComment("initialize the test generation harness with a different random ")
                    .lineComment("seed.  If this proves a real issue, it is fixable, but with some pain.");
            String lesserVar = newVarName(lesserMember.getMemberName() + "Lesser");
            String greaterVar = newVarName(greaterMember.getMemberName() + "Greater");
            bb.declare(lesserVar)
                    .initializedByInvoking("lesserOf")
                    .withArgument(origLesserMemberInstantiation.memberVar)
                    .withArgument(origGreaterMemberInstantiation.memberVar)
                    .inScope()
                    .as(origLesserMemberInstantiation.member.typeName());
            bb.declare(greaterVar)
                    .initializedByInvoking("greaterOf")
                    .withArgument(origLesserMemberInstantiation.memberVar)
                    .withArgument(origGreaterMemberInstantiation.memberVar)
                    .inScope()
                    .as(origLesserMemberInstantiation.member.typeName());
            lesserMemberInstantiation
                    = memberInstantiation(
                            origLesserMemberInstantiation.member,
                            origLesserMemberInstantiation.contentsVar, lesserVar);
            greaterMemberInstantiation
                    = memberInstantiation(
                            origGreaterMemberInstantiation.member,
                            origGreaterMemberInstantiation.contentsVar, greaterVar);
        } else {
            lesserMemberInstantiation = null;
            greaterMemberInstantiation = null;
            lesserMemberName = null;
            greaterMemberName = null;
        }

        bb.lineComment("Instantiate a " + typeNameOf(shape));
        bb.declare(name)
                .initializedWithNew(nb -> {
                    for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {

                        MemberInstantiation<?> m;
                        if (e.getKey().equals(lesserMemberName)) {
                            m = lesserMemberInstantiation;
                        } else if (e.getKey().equals(greaterMemberName)) {
                            m = greaterMemberInstantiation;
                        } else {
                            m = instantiateStructureMember(e.getValue(),
                                    shape, bb);
                        }

                        bb.lineComment("XX-Member " + typeNameOf(shape) + ": " + e.getKey() + " " + e.getValue().getId()
                                + " memberVar " + m.memberVar);

                        members.add(m);
                        Optional<JavaTypes> tp = m.member == null
                                ? empty() : m.member.javaType();
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
                    nb.ofType(typeNameOf(shape));
                }).as(typeNameOf(shape));

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

        String memberVar = newVarName(escape(member.getMemberName()));
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

        StructureInstantiation(String name, StructureShape shape, List<MemberInstantiation<?>> members) {
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

        MemberInstantiation(StructureMember<S> member, String contentsVar, String memberVar) {
            this.member = member;
            this.contentsVar = contentsVar;
            this.memberVar = memberVar;
        }

        public static <S extends Shape> MemberInstantiation<S> memberInstantiation(StructureMember<S> member, String contentsVar, String memberVar) {
            return new MemberInstantiation<>(member, contentsVar, memberVar);
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
        public ObjectSizes sizes() {
            return AbstractJavaTestGenerator.this.sizes();
        }

        @Override
        public boolean isOmitted(MemberShape shape) {
            return false;
        }

        @Override
        public Namer namer() {
            return namer;
        }

        @Override
        public <T> void generateNullCheck(String variable, BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on) {
            ValidationExceptionProvider.generateNullCheck(variable, bb, on);
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
            return validationExceptions();
        }

        @Override
        public <T, R> String generateInitialEqualsTest(ClassBuilder<R> cb, ClassBuilder.BlockBuilder<T> bb) {
            return AbstractJavaTestGenerator.this.generateInitialEqualsTest(cb, bb);
        }

        @Override
        public void maybeImport(ClassBuilder<?> cb, String... fqns) {
            List<String> fq = new ArrayList<>(asList(fqns));
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
            String[] fqns1 = fq.toArray(String[]::new);
            ShapeUtils.maybeImport(cb, fqns1);
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
        if (shape.getType() == TIMESTAMP) {
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
