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
package com.telenav.smithy.ts.generator;

import com.mastfrog.util.strings.Escaper;
import static com.mastfrog.util.strings.Strings.decapitalize;
import com.telenav.smithy.extensions.SamplesTrait;
import static com.telenav.smithy.generators.GenerationSwitches.DEBUG;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.SettingsKey;
import com.telenav.smithy.names.NumberKind;
import com.telenav.smithy.ts.generator.type.TsTypeUtils;
import com.telenav.smithy.ts.generator.type.TypeStrategies;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.NewBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import static com.telenav.smithy.ts.vogon.TypescriptSource.typescript;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbtractTsTestGenerator<S extends Shape> extends AbstractTypescriptGenerator<S> {

    private static final SettingsKey<TypescriptSource> key
            = SettingsKey.key(TypescriptSource.class, "generated-tests");
    private static final Map<TypescriptSource, TestContext> CONTEXTS
            = new WeakHashMap<>();

    protected AbtractTsTestGenerator(S shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    TestContext contextFor(TypescriptSource src, Model model) {
        return CONTEXTS.computeIfAbsent(src, s -> new TestContext(model, src, importShape -> {
            importShape(src, importShape);
        }));
    }

    protected void importShape(TypescriptSource src, Shape shape) {
        if (TsTypeUtils.isNotUserType(shape)) {
            return;
        }
        String name = tsTypeName(shape);
        src.importing(name).from(modelSourcePath());
    }

    protected String modelSourcePath() {
        Path modelRoot = ctx.destinations().sourceRootFor(GenerationTarget.MODEL, shape, ver, ctx.settings());
        Path relPath = dest.relativize(modelRoot);
        String srcFile = super.serviceSourceFile(GenerationTarget.MODEL);
        return relPath.resolve(srcFile).toString() + ".js";
    }

    @Override
    TypescriptSource src() {
        return ctx.computeIfAbsent(key, () -> {
            TypescriptSource result = typescript("generated-tests");
            if (canImplementValidating()) {
                maybeGenerateValidationInterface(result);
            }
            if (ctx.settings().is(DEBUG)) {
                result.generateDebugLogCode();
            }
            return result;
        });
    }

    protected boolean canGenerateTests() {
        return true;
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        if (canGenerateTests()) {
            TypescriptSource src = src();
            generate(src, contextFor(src, model));
            c.accept(src);
        }
    }

    protected abstract void generate(TypescriptSource src, TestContext ctx);

    static class TestContext {

        private final Random random = new Random(1290139);
        private final Map<String, Integer> varCounters = new HashMap<>();
        private final Model model;
        private final Map<ShapeId, RandomInstanceGenerator> generators = new HashMap<>();
        private final TypeStrategies strategies;
        private final TypescriptSource src;
        private final Consumer<Shape> importer;

        TestContext(Model model, TypescriptSource src, Consumer<Shape> importer) {
            this.model = model;
            this.strategies = TypeStrategies.strategies(model);
            this.src = src;
            this.importer = importer;
        }

        TypeStrategy<?> strategy(Shape shape) {
            return shape.asMemberShape().<TypeStrategy<?>>map(mem -> {
                return strategies.memberStrategy(mem, shape);
            }).orElse(strategies.strategy(shape));
        }

        public Optional<RandomInstanceGenerator<?>> generatorFor(Shape shape) {
            if (generators.containsKey(shape.getId())) {
                return Optional.of(generators.get(shape.getId()));
            }

            Shape realShape;
            Optional<MemberShape> memberShape = shape.asMemberShape();
            if (memberShape.isPresent()) {
                realShape = model.expectShape(memberShape.get().getTarget());
            } else {
                realShape = shape;
            }
            TraitFinder traits = traits(shape, model);
            RandomInstanceGenerator<?> result = null;
            switch (realShape.getType()) {
                case STRING:
                    Optional<SamplesTrait> samples = traits.find(SamplesTrait.class);
                    if (samples.isPresent()) {
                        result = new SamplesStringShape(this, realShape.asStringShape().get(), traits, samples.get());
                    } else {
                        result = new StringInstanceGenerator(this, realShape.asStringShape().get(), traits);
                        if (!result.canGenerateInvalidValues() && !result.canGenerateValidValues()) {
                            result = null;
                        }
                    }
                    break;
                case BIG_DECIMAL:
                case BIG_INTEGER:
                case BYTE:
                case SHORT:
                case FLOAT:
                case INTEGER:
                case DOUBLE:
                case LONG:
                    result = new RandomNumberInstanceGenerator(this, realShape, traits);
                    break;
                case TIMESTAMP:
                    result = new DateInstanceGenerator(this, realShape.asTimestampShape().get(), traits);
                    break;
                case INT_ENUM:
                    result = new RandomIntEnumInstanceGenerator(this, realShape.asIntEnumShape().get(), traits);
                    break;
                case ENUM:
                    EnumShape enumShape = realShape.asEnumShape().get();
                    switch (characterizeEnum(enumShape)) {
                        case HETEROGENOUS:
                        case INT_VALUED:
                        case NONE:
                        case STRING_VALUED:
                            result = new RandomNamedStringEnumInstanceGenerator(this, enumShape, traits);
                            break;
                        case STRING_VALUED_MATCHING_NAMES:
                        default:
                            result = new RandomStringEnumInstanceGenerator(this, enumShape, traits);
                            break;
                    }
                    break;
                case BOOLEAN:
                    BooleanShape boolShape = realShape.asBooleanShape().get();
                    result = new RandomBooleanInstanceGenerator(this, boolShape, traits);
                    break;
                case STRUCTURE:
                    StructureShape struct = realShape.asStructureShape().get();
                    Map<MemberShape, RandomInstanceGenerator<?>> memberGenerators = new HashMap<>();
                    RecursiveWrapperInstanceGenerator<StructureShape> recu = new RecursiveWrapperInstanceGenerator<>();
                    // So if we recurse into here, the shell generator, which will work, is found.
                    generators.put(shape.getId(), recu);
                    for (Map.Entry<String, MemberShape> e : struct.getAllMembers().entrySet()) {
                        Shape realMemberShape = model.expectShape(e.getValue().getTarget());
                        if (realMemberShape.getId().equals(realShape.getId())) {
                            memberGenerators.put(e.getValue(), recu);
                        } else {
                            Optional<RandomInstanceGenerator<?>> g = generatorFor(e.getValue());
                            if (g.isPresent()) {
                                memberGenerators.put(e.getValue(), g.get());
                            }
                            // else keep going so we can correctly enumerate the missing
                            // ones in a comment below
                        }
                    }
                    if (memberGenerators.size() == struct.getAllMembers().size()) {
                        RandomStructureInstanceGenerator structGen = new RandomStructureInstanceGenerator(
                                this, struct, traits, memberGenerators);
                        recu.set(structGen);
                        result = structGen;
                    } else {
                        src.lineComment("Missing generators for some members of " + realShape.getId() + ": ");
                        Set<MemberShape> gens = new HashSet<>(struct.getAllMembers().values());
                        memberGenerators.keySet().forEach(sh -> {
                            for (Iterator<MemberShape> it = gens.iterator(); it.hasNext();) {
                                MemberShape g = it.next();
                                if (sh.getId().equals(g.getId())) {
                                    it.remove();
                                }
                            }
                        });
                        gens.removeAll(memberGenerators.keySet());
                        for (MemberShape mem : gens) {
                            Shape sh = model.expectShape(mem.getTarget());
                            src.lineComment(" * " + sh.getType() + " " + sh.getId().getName() + " " + mem.getMemberName());
                        }
                        generators.remove(shape.getId());
                    }
            }
            if (result != null) {
                importer.accept(realShape);
                generators.put(shape.getId(), result);
            }
            return Optional.ofNullable(result);
        }

        Random rnd() {
            return random;
        }

        private int nextCounter(String what) {
            return varCounters.compute(what, (wh, old) -> {
                if (old == null) {
                    return 1;
                }
                return old + 1;
            });
        }

        public String varFor(Shape shape) {
            String typeName = escape(decapitalize(shape.getId().getName()));
            return typeName + nextCounter(typeName);
        }
    }

    static TraitFinder traits(Shape shape, Model mdl) {
        if (shape.isMemberShape()) {
            return new MemberTraitFinder(shape.asMemberShape().get(), mdl);
        }
        return new SimpleTraitFinder(shape);
    }

    static class RecursiveWrapperInstanceGenerator<S extends Shape> implements RandomInstanceGenerator<S> {

        private RandomInstanceGenerator<S> target;

        void set(RandomInstanceGenerator<S> t) {
            target = t;
        }

        @Override
        public Optional<RandomInstance<S>> valid(TestContext ctx) {
            return target.valid(ctx);
        }

        @Override
        public Optional<RandomInstance<S>> invalid(TestContext ctx) {
            return target.invalid(ctx);
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return target.canGenerateInvalidValues();
        }

        @Override
        public boolean canGenerateValidValues() {
            return target.canGenerateValidValues();
        }
    }

    interface RandomInstanceGenerator<S extends Shape> {

        Optional<RandomInstance<S>> valid(TestContext ctx);

        Optional<RandomInstance<S>> invalid(TestContext ctx);

        boolean canGenerateInvalidValues();

        boolean canGenerateValidValues();

        default boolean permutationsExhausted() {
            return true;
        }
    }

    static abstract class RandomInstance<S extends Shape> {

        final Map<ShapeId, RandomInstance<?>> inputs = new HashMap<>();
        final S shape;
        final boolean valid;

        public RandomInstance(S shape, boolean valid) {
            this.shape = shape;
            this.valid = valid;
        }

        <T> Optional<T> as(Class<T> what) {
            if (what.isInstance(this)) {
                return Optional.of(what.cast(this));
            }
            return Optional.empty();
        }

        RandomInstance shapeForMember(MemberShape ms) {
            return inputs.get(ms.getId());
        }

        abstract <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx);

        Optional<String> invalidityDescription() {
            return Optional.empty();
        }
    }

    interface Valued {

        <T> Optional<T> value(Class<T> type);
    }

    static class RandomStringInstance<S extends Shape> extends RandomInstance<S> implements Valued {

        private final String value;

        public RandomStringInstance(String value, S shape, boolean valid) {
            super(shape, valid);
            this.value = value;
        }

        @Override
        Optional<String> invalidityDescription() {
            if (valid) {
                return Optional.empty();
            }
            String result = Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(value);
            if (result.length() > 20) {
                result = result.substring(0, 20);
            }
            return Optional.of(result.isEmpty() ? "empty" : result);
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            Assignment<B> decl = bb.declareConst(varName);
            String type = ctx.strategy(shape).targetType();
            decl.ofType(type);
            if (!TsTypeUtils.isNotUserType(shape)) {
                decl.assignedToNew().withStringLiteralArgument(value).ofType(type);
            } else {
                decl.assignedToStringLiteral(value);
            }
            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 47 * hash + this.value.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomStringInstance<?> other = (RandomStringInstance<?>) obj;
            return this.value.equals(other.value)
                    && this.shape.getId().equals(other.shape.getId());
        }

    }

    static class RandomNumberInstance extends RandomInstance<Shape> implements Valued {

        private final Number value;

        RandomNumberInstance(Number number, Shape shape, boolean valid) {
            super(shape, valid);
            this.value = number;
        }

        @Override
        Optional<String> invalidityDescription() {
            if (valid) {
                return Optional.empty();
            }
            return Optional.of(Escaper.JAVA_IDENTIFIER_DELIMITED.escape(value.toString()));
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            Assignment<B> decl = bb.declareConst(varName);
            String type = ctx.strategy(shape).targetType();
            decl.ofType(type);
            if (ctx.strategy(shape).isModelDefined()) {
                decl.assignedToNew().withArgument(value).ofType(type);
            } else {
                decl.assignedTo(value);
            }
            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + value.hashCode();
            hash = 37 * hash + shape.getId().hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomNumberInstance other = (RandomNumberInstance) obj;
            return value.equals(other.value)
                    && shape.getId().equals(other.shape.getId());
        }
    }

    static class RandomTimestampInstance extends RandomInstance<TimestampShape> implements Valued {

        private final Instant when;

        RandomTimestampInstance(Instant when, TimestampShape shape, boolean valid) {
            super(shape, valid);
            this.when = when;
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type.isInstance(when) ? Optional.of(type.cast(when)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            Assignment<B> decl = bb.declareConst(varName);
            String type = ctx.strategy(shape).targetType();
            decl.ofType(type);
            decl.assignedToNew(nb -> {
                nb.withInvocationOf("parse").withStringLiteralArgument(when.toString()).on("Date")
                        .ofType(type);
            });
            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + Objects.hashCode(this.when);
            hash = 47 * hash + Long.hashCode(when.toEpochMilli());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomTimestampInstance other = (RandomTimestampInstance) obj;
            return other.shape.getId().equals(shape.getId())
                    && other.when.toEpochMilli() == when.toEpochMilli();
        }
    }

    interface TraitFinder {

        <T extends Trait> Optional<T> find(Class<T> trait);
    }

    static class SimpleTraitFinder implements TraitFinder {

        private final Shape shape;

        public SimpleTraitFinder(Shape shape) {
            this.shape = shape;
        }

        @Override
        public <T extends Trait> Optional<T> find(Class<T> trait) {
            return shape.getTrait(trait);
        }
    }

    static class MemberTraitFinder implements TraitFinder {

        private final MemberShape member;
        private final Model model;

        MemberTraitFinder(MemberShape member, Model model) {
            this.member = member;
            this.model = model;
        }

        @Override
        public <T extends Trait> Optional<T> find(Class<T> trait) {
            return member.getMemberTrait(model, trait);
        }
    }

    static abstract class AbstractInstanceGenerator<S extends Shape> implements RandomInstanceGenerator<S> {

        final TestContext ctx;
        final S shape;
        final TraitFinder traits;

        AbstractInstanceGenerator(TestContext ctx, S shape, TraitFinder traits) {
            this.ctx = ctx;
            this.shape = shape;
            this.traits = traits;
        }

        @Override
        public Optional<RandomInstance<S>> invalid(TestContext ctx) {
            if (canGenerateInvalidValues()) {
                return Optional.of(newInvalidValue(ctx));
            }
            return Optional.empty();
        }

        @Override
        public Optional<RandomInstance<S>> valid(TestContext ctx) {
            if (canGenerateValidValues()) {
                return Optional.of(newValidValue(ctx));
            }
            return Optional.empty();
        }

        protected RandomInstance<S> newInvalidValue(TestContext ctx) {
            throw new UnsupportedOperationException();
        }

        protected RandomInstance<S> newValidValue(TestContext ctx) {
            throw new UnsupportedOperationException();
        }
    }

    static class RandomNumberInstanceGenerator extends AbstractInstanceGenerator<Shape> {

        private final NumberKind kind;

        RandomNumberInstanceGenerator(TestContext ctx, Shape shape, TraitFinder traits) {
            super(ctx, shape, traits);
            Shape realShape = shape.asMemberShape().map(mem -> {
                return ctx.model.expectShape(mem.getTarget());
            }).orElse(shape);
            NumberKind kind = NumberKind.forShape(realShape);
            if (kind == null) {
                switch (realShape.getType()) {
                    case BIG_INTEGER:
                        kind = NumberKind.LONG;
                        break;
                    case BIG_DECIMAL:
                        kind = NumberKind.DOUBLE;
                        break;
                    default:
                        throw new AssertionError("Not a number shape: " + realShape.getId());
                }
            }
            this.kind = kind;
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return traits.find(RangeTrait.class).flatMap(rng -> {
                return rng.getMin().flatMap(min -> {
                    return rng.getMax().map(max -> {
                        if (kind.isFloatingPoint()) {
                            return !(max.doubleValue() == Double.MAX_VALUE && min.doubleValue() == Double.MIN_VALUE);
                        } else {
                            return !(max.longValue() == Long.MAX_VALUE && min.longValue() == Long.MIN_VALUE);
                        }
                    });
                });
            }).orElse(false);
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }

        @Override
        protected RandomInstance<Shape> newValidValue(TestContext ctx) {
            if (kind.isFloatingPoint()) {
                Double minimum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMin().map(min -> {
                        return min.doubleValue();
                    });
                }).or(() -> traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.doubleValue() - 256;
                    });
                })).orElse(0D);

                Double maximum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.doubleValue();
                    });
                }).orElse(minimum + 256);

                double range = maximum - minimum;
                double target = minimum + (ctx.rnd().nextDouble() * range);

                return new RandomNumberInstance(target, shape, true);

            } else {
                Long minimum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMin().map(min -> {
                        return min.longValue();
                    });
                }).or(() -> traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.longValue() - 256;
                    });
                })).orElse(0L);

                Long maximum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.longValue();
                    });
                }).orElse(minimum + 256);

                long range = maximum - minimum;
                long target = minimum + (ctx.rnd().nextLong(range));
                return new RandomNumberInstance(target, shape, true);
            }
        }

        @Override
        protected RandomInstance<Shape> newInvalidValue(TestContext ctx) {
            if (kind.isFloatingPoint()) {
                Double minimum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMin().map(min -> {
                        return min.doubleValue();
                    });
                }).or(() -> traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.doubleValue() - 256;
                    });
                })).orElse(0D);

                Double maximum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.doubleValue();
                    });
                }).orElse(minimum + 256);

//                double range = maximum - minimum;
//                double target = minimum + (ctx.rnd().nextDouble() * range);
                double target;
                if (minimum > Long.MIN_VALUE + 256) {
                    target = minimum - (1 + ctx.rnd().nextDouble() * 256D);
                } else {
                    target = maximum - (1 + ctx.rnd().nextDouble() * 256D);
                }

                return new RandomNumberInstance(target, shape, true);

            } else {
                Long minimum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMin().map(min -> {
                        return min.longValue();
                    });
                }).or(() -> traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.longValue() - 256;
                    });
                })).orElse(0L);

                Long maximum = traits.find(RangeTrait.class).flatMap(rng -> {
                    return rng.getMax().map(max -> {
                        return max.longValue();
                    });
                }).orElse(minimum + 256);

                long target;
                if (minimum > Long.MIN_VALUE + 256) {
                    target = minimum - (1 + ctx.rnd().nextInt(256));
                } else {
                    target = maximum + 1 + ctx.rnd().nextInt(256);
                }
                return new RandomNumberInstance(target, shape, true);
            }
        }

    }

    static class SamplesStringShape extends AbstractInstanceGenerator<StringShape> {

        private final SamplesTrait samples;
        private final List<String> valids;
        private final List<String> invalids;
        private int invalidCursor;
        private int validCursor;

        public SamplesStringShape(TestContext ctx, StringShape shape, TraitFinder traits, SamplesTrait samples) {
            super(ctx, shape, traits);
            this.samples = samples;
            valids = samples.validSamples(nd -> {
                if (!nd.isStringNode()) {
                    throw new ExpectationNotMetException("Valid sample items for " + shape.getId()
                            + " should all be strings, but one is a " + nd.getType(),
                            nd);
                }
                return nd.asStringNode().get().getValue();
            });
            invalids = samples.invalidSamples(nd -> {
                if (!nd.isStringNode()) {
                    throw new ExpectationNotMetException("Invalld sample items for " + shape.getId()
                            + " should all be strings, but one is a " + nd.getType(),
                            nd);
                }
                return nd.asStringNode().get().getValue();
            });
            Collections.shuffle(valids, ctx.rnd());
            Collections.shuffle(invalids, ctx.rnd());
        }

        String value(TestContext ctx, boolean invalid) {
            String result;
            if (invalid) {
                result = invalids.get((invalidCursor++) % invalids.size());
            } else {
                result = valids.get((invalidCursor++) % invalids.size());
            }
            return result;
        }

        @Override
        public boolean permutationsExhausted() {
            return invalidCursor >= invalids.size();
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return !invalids.isEmpty();
        }

        @Override
        public boolean canGenerateValidValues() {
            return !valids.isEmpty();
        }

        @Override
        protected RandomInstance<StringShape> newInvalidValue(TestContext ctx) {
            return new RandomStringInstance<>(value(ctx, true), shape, false);
        }

        @Override
        protected RandomInstance<StringShape> newValidValue(TestContext ctx) {
            return new RandomStringInstance<>(value(ctx, false), shape, true);
        }
    }

    static class StringInstanceGenerator extends AbstractInstanceGenerator<StringShape> {

        static final char[] ALPHANUM
                = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

        StringInstanceGenerator(TestContext ctx, StringShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        protected RandomInstance<StringShape> newInvalidValue(TestContext ctx) {
            return new RandomStringInstance<>(randomString(), shape, false);
        }

        @Override
        protected RandomInstance<StringShape> newValidValue(TestContext ctx) {
            return new RandomStringInstance<>(randomString(), shape, true);
        }

        String randomInvalidString() {
            int min = minLength();
            int max = maxLength();
            if (min == 0 && max > 256) {
                return "";
            }
            int target;
            if (min > 1) {
                target = ctx.rnd().nextInt(min - 1) + 1;

            } else {
                int base = max + 1;
                target = base + ctx.rnd().nextInt(32);
            }
            return randomString(target);
        }

        private char randomChar() {
            return ALPHANUM[ctx.rnd().nextInt(ALPHANUM.length)];
        }

        String randomString(int length) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(randomChar());
            }
            return sb.toString();
        }

        String randomString() {
            int min = minLength();
            int max = maxLength();
            if (min == max) {
                return randomString(max);
            }
            // Don't really try to create an Integer.MAX_VALUE length string
            int realMax = Math.min(max, min + 128);
            int targetLength = min + ctx.rnd().nextInt(realMax - min);
            return randomString(targetLength);
        }

        int minLength() {
            Long result = traits.find(LengthTrait.class).map(lt -> {
                return lt.getMin().orElse(0L);
            }).orElse(0L);
            return result.intValue();
        }

        int maxLength() {
            Long result = traits.find(LengthTrait.class).map(lt -> {
                return lt.getMax().orElse(0L);
            }).orElse((long) Integer.MAX_VALUE);
            return result.intValue();
        }

        private boolean hasPatternTrait() {
            return traits.find(PatternTrait.class).isPresent();
        }

        @Override
        public boolean canGenerateInvalidValues() {
            if (hasPatternTrait()) {
                return false;
            }
            int min = minLength();
            int max = maxLength();
            if (min == 0 && max > 128) {
                return false;
            }
            if (min > 256) {
                return false;
            }
            return true;
        }

        @Override
        public boolean canGenerateValidValues() {
            if (hasPatternTrait()) {
                return false;
            }
            int min = minLength();
            int max = maxLength();
            if (min > 256 || max == 0) {
                return false;
            }
            return true;
        }
    }

    static class DateInstanceGenerator extends AbstractInstanceGenerator<TimestampShape> {

        static Duration range = Duration.ofDays(365 * 26);
        static Instant epoch = Instant.parse("1997-01-22T06:34:42Z");

        DateInstanceGenerator(TestContext ctx, TimestampShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        protected RandomInstance<TimestampShape> newValidValue(TestContext ctx) {
            Duration dur = Duration.ofMillis(ctx.rnd().nextLong(range.toMillis()));
            Instant target = epoch.plus(dur);
            return new RandomTimestampInstance(target, shape, true);
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return false;
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }
    }

    static class RandomStringEnumInstanceGenerator extends AbstractInstanceGenerator<EnumShape> {

        RandomStringEnumInstanceGenerator(TestContext ctx, EnumShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        protected RandomInstance<EnumShape> newValidValue(TestContext ctx) {
            List<String> all = new ArrayList<>(shape.getEnumValues().values());
            String target = all.get(ctx.rnd().nextInt(all.size()));

            return new RandomStringInstance<>(target, shape, true);
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return false;
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }
    }

    static class RandomNamedStringEnumInstanceGenerator extends AbstractInstanceGenerator<EnumShape> {

        RandomNamedStringEnumInstanceGenerator(TestContext ctx, EnumShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        protected RandomInstance<EnumShape> newValidValue(TestContext ctx) {
            List<String> all = new ArrayList<>(shape.getEnumValues().keySet());
            String target = all.get(ctx.rnd().nextInt(all.size()));
            return new RandomNamedEnumInstance(target, shape, true, ctx.strategy(shape).targetType());
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return false;
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }
    }

    static class RandomIntEnumInstanceGenerator extends AbstractInstanceGenerator<IntEnumShape> {

        RandomIntEnumInstanceGenerator(TestContext ctx, IntEnumShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return false;
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }

        @Override
        protected RandomInstance<IntEnumShape> newValidValue(TestContext ctx) {
            List<Integer> all = new ArrayList<>(shape.getEnumValues().values());
            int index = all.get(ctx.rnd().nextInt(all.size()));
            return new RandomIntEnumInstance(shape, index);
        }
    }

    static class RandomIntEnumInstance extends RandomInstance<IntEnumShape> implements Valued {

        private final int index;

        RandomIntEnumInstance(IntEnumShape shape, int index) {
            super(shape, true);
            this.index = index;
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type.isInstance(index) ? Optional.of(type.cast(index)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            bb.declareConst(varName).ofType(ctx.strategy(shape).targetType())
                    .assignedTo(index);
            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + this.index;
            hash = 53 * hash + shape.getId().hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomIntEnumInstance other = (RandomIntEnumInstance) obj;
            return this.index == other.index && shape.getId().equals(other.shape.getId());
        }
    }

    static class RandomNamedEnumInstance extends RandomInstance<EnumShape> implements Valued {

        private final String enumConstant;
        private final String typeName;

        RandomNamedEnumInstance(String enumConstant, EnumShape shape, boolean valid, String typeName) {
            super(shape, valid);
            this.enumConstant = enumConstant;
            this.typeName = typeName;
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            String output = typeName + "." + enumConstant;
            return type.isInstance(output) ? Optional.of(type.cast(output)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            String typeName = ctx.strategy(shape).targetType();
            bb.declareConst(varName).ofType(typeName)
                    .assignedToField(enumConstant).of(typeName);
            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 83 * hash + enumConstant.hashCode();
            hash = 83 * hash + shape.getId().hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomNamedEnumInstance other = (RandomNamedEnumInstance) obj;
            return Objects.equals(this.enumConstant, other.enumConstant);
        }

    }

    static class RandomBooleanInstance extends RandomInstance<BooleanShape> implements Valued {

        private final boolean val;

        RandomBooleanInstance(BooleanShape shape, boolean val) {
            super(shape, true);
            this.val = val;
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type.isInstance(val) ? Optional.of(type.cast(val)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            String type = ctx.strategy(shape).targetType();
            Assignment<B> decl = bb.declareConst(varName).ofType(type);
            if (!TsTypeUtils.isNotUserType(shape)) {
                decl.assignedToNew(nb -> {
                    nb.withArgument(val).ofType(type);
                });
            } else {
                decl.assignedTo(val);
            }
            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + (this.val ? 1 : 0);
            hash = 29 * hash + shape.getId().hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomBooleanInstance other = (RandomBooleanInstance) obj;
            return this.val == other.val && shape.getId().equals(other.shape.getId());
        }

    }

    static class RandomBooleanInstanceGenerator extends AbstractInstanceGenerator<BooleanShape> {

        RandomBooleanInstanceGenerator(TestContext ctx, BooleanShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        protected RandomInstance<BooleanShape> newValidValue(TestContext ctx) {
            boolean val = ctx.rnd().nextBoolean();
            return new RandomBooleanInstance(shape, val);
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return false;
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }
    }

    static class RandomStructureInstanceGenerator extends AbstractInstanceGenerator<StructureShape> {

        private final Map<MemberShape, RandomInstanceGenerator<?>> memberGenerators;
        private int invalidValueCursor = 0;

        RandomStructureInstanceGenerator(TestContext ctx, StructureShape shape, TraitFinder traits,
                Map<MemberShape, RandomInstanceGenerator<?>> memberGenerators) {
            super(ctx, shape, traits);
            this.memberGenerators = memberGenerators;
        }

        @Override
        protected RandomInstance<StructureShape> newValidValue(TestContext ctx) {
            Map<MemberShape, RandomInstance<?>> members = new HashMap<>();
            for (Map.Entry<MemberShape, RandomInstanceGenerator<?>> e : memberGenerators.entrySet()) {
                RandomInstance<?> ri = randomInstanceFrom(e.getValue(), true);
                members.put(e.getKey(), ri);
            }
            return new RandomStructureInstance(shape, true, members, null);
        }

        @Override
        protected RandomInstance<StructureShape> newInvalidValue(TestContext ctx) {
            Map<MemberShape, RandomInstance<?>> members = new HashMap<>();
            Map.Entry<MemberShape, RandomInstanceGenerator<?>> bad
                    = randomInvalidValueMember(ctx);
            for (Map.Entry<MemberShape, RandomInstanceGenerator<?>> e : memberGenerators.entrySet()) {
                RandomInstance<?> ri;
                if (e.getKey().getId().equals(bad.getKey().getId())) {
                    ri = randomInstanceFrom(bad.getValue(), false);
                } else {
                    ri = randomInstanceFrom(e.getValue(), true);
                }
                members.put(e.getKey(), ri);
            }
            return new RandomStructureInstance(shape, false, members, bad.getKey());
        }

        private <T extends Shape> RandomInstance<T> randomInstanceFrom(RandomInstanceGenerator<T> r, boolean valid) {
            return valid ? r.valid(ctx).get() : r.invalid(ctx).get();
        }

        private List<Map.Entry<MemberShape, RandomInstanceGenerator<?>>> candidates;

        private Map.Entry<MemberShape, RandomInstanceGenerator<?>> randomInvalidValueMember(TestContext ctx) {
            List<Map.Entry<MemberShape, RandomInstanceGenerator<?>>> candidatesLoc = invalidValueCapable();
            if (candidatesLoc.isEmpty()) {
                throw new AssertionError("Not invalid value capable: " + shape.getId());
            }
            return candidatesLoc.get((invalidValueCursor++) % candidatesLoc.size());
        }

        @Override
        public boolean permutationsExhausted() {
            return invalidValueCursor >= invalidValueCapable().size();
        }

        private List<Map.Entry<MemberShape, RandomInstanceGenerator<?>>> invalidValueCapable() {
            if (candidates != null) {
                return candidates;
            }
            List<Map.Entry<MemberShape, RandomInstanceGenerator<?>>> l = new ArrayList<>();
            for (Map.Entry<MemberShape, RandomInstanceGenerator<?>> e : memberGenerators.entrySet()) {
                if (e.getValue().canGenerateInvalidValues()) {
                    l.add(e);
                }
            }
            return l;
        }

        private List<MemberShape> invalidValueMembers() {
            List<MemberShape> result = new ArrayList<>();
            for (Map.Entry<MemberShape, RandomInstanceGenerator<?>> e : invalidValueCapable()) {
                result.add(e.getKey());
            }
            return result;
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return !invalidValueCapable().isEmpty();
        }

        @Override
        public boolean canGenerateValidValues() {
            return true;
        }
    }

    static class RandomStructureInstance extends RandomInstance<StructureShape> {

        private final Map<MemberShape, RandomInstance<?>> members;
        private final MemberShape invalidItem;

        RandomStructureInstance(StructureShape shape, boolean valid, Map<MemberShape, RandomInstance<?>> members, MemberShape invalidItem) {
            super(shape, valid);
            this.members = members;
            members.forEach((mem, in) -> {
                super.inputs.put(mem.getId(), in);
            });
            this.invalidItem = invalidItem;
        }

        @Override
        Optional<String> invalidityDescription() {
            if (valid || invalidItem == null) {
                return Optional.empty();
            }
            return Optional.of(invalidItem.getMemberName());
        }

        List<MemberShape> eachMemberOptionalLast() {
            // Produces the same order as SimpleStructureGenerator
            List<Map.Entry<String, MemberShape>> sorted
                    = new ArrayList<>(shape.getAllMembers().entrySet());
            sorted.sort((a, b) -> {
                int va  = a.getValue().getTrait(RequiredTrait.class)
                        .isPresent() ? 0 : 1;
                int vb = b.getValue().getTrait(RequiredTrait.class)
                        .isPresent() ? 0 : 1;
                int result = Integer.compare(va, vb);
                if (result == 0) {
                    result = a.getKey().compareTo(b.getKey());
                }
                return result;
            });
            List<MemberShape> result = new ArrayList<>();
            for (Map.Entry<String, MemberShape> e : sorted) {
                result.add(e.getValue());
            }
            return result;
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);

            String type = ctx.strategy(shape).targetType();

            List<String> varNames = new ArrayList<>();
            for (MemberShape mem : eachMemberOptionalLast()) {
                RandomInstance<?> ri = members.get(mem);
                if (mem.equals(invalidItem)) {
                    bb.lineComment("Deliberately invalid: " + mem.getId().getName());
                }
                varNames.add(ri.instantiate(bb, ctx));
            }
            NewBuilder<B> nue = bb.declareConst(varName).ofType(type)
                    .assignedToNew();
            for (String v : varNames) {
                nue.withArgument(v);
            }
            nue.ofType(type);

            return varName;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 71 * hash + this.members.hashCode();
            hash = 71 * hash + shape.getId().hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RandomStructureInstance other = (RandomStructureInstance) obj;
            return Objects.equals(this.members, other.members)
                    && shape.getId().equals(other.shape.getId());
        }
    }

    @Override
    protected <T, R> void generateValidationMethodBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        // do nothing
    }

    @Override
    protected <T, R> void generateValidationMethodHeadAndBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        super.generateValidationMethodHeadAndBody(bb, cb);
    }

    @Override
    protected boolean hasValidatableValues() {
        return false;
    }

    @Override
    protected boolean canImplementValidating() {
        return false;
    }

    @Override
    protected <T> void applyValidatableInterface(ClassBuilder<T> cb) {
        // do nothing
    }

    @Override
    protected <T> void applyValidatableInterface(InterfaceBuilder<T> ib) {
        // do nothing
    }

    @Override
    protected void maybeGenerateValidationInterface(TypescriptSource ts) {
        // do nothing
    }
}
