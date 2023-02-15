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

import com.mastfrog.function.state.Obj;
import com.mastfrog.util.strings.Escaper;
import static com.mastfrog.util.strings.Strings.decapitalize;
import com.telenav.smithy.extensions.SamplesTrait;
import static com.telenav.smithy.generators.GenerationSwitches.DEBUG;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.SettingsKey;
import com.telenav.smithy.names.NumberKind;
import com.telenav.smithy.rex.Xeger;
import com.telenav.smithy.ts.generator.AbstractTsTestGenerator.AbstractInstanceGenerator;
import com.telenav.smithy.ts.generator.AbstractTsTestGenerator.RandomInstance;
import com.telenav.smithy.ts.generator.AbstractTsTestGenerator.TestContext;
import com.telenav.smithy.ts.generator.AbstractTsTestGenerator.TraitFinder;
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
import static java.lang.Integer.min;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collections;
import static java.util.Collections.emptyList;
import static java.util.Collections.shuffle;
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
import java.util.function.Function;
import java.util.function.Supplier;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractTsTestGenerator<S extends Shape> extends AbstractTypescriptGenerator<S> {

    private static final SettingsKey<TypescriptSource> key
            = SettingsKey.key(TypescriptSource.class, "generated-tests");
    private static final Map<TypescriptSource, TestContext> CONTEXTS
            = new WeakHashMap<>();

    protected AbstractTsTestGenerator(S shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    TestContext contextFor(TypescriptSource src, Model model) {
//        long seed = ctx.settings().getLong("tsTestSeed").orElse(1290139L);
        long seed = ctx.settings().getLong("tsTestSeed").orElse(6129039L);
        return CONTEXTS.computeIfAbsent(src, s -> new TestContext(model, src, seed, importShape -> {
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
        Path modelRoot = ctx.destinations().sourceRootFor(GenerationTarget.MODEL, shape, ver, ctx.settings()).path();
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

        private final Random random;
        private final Map<String, Integer> varCounters = new HashMap<>();
        private final Model model;
        private final Map<ShapeId, RandomInstanceGenerator> generators = new HashMap<>();
        private final TypeStrategies strategies;
        private final TypescriptSource src;
        private final Consumer<Shape> importer;

        TestContext(Model model, TypescriptSource src, long seed, Consumer<Shape> importer) {
            this.model = model;
            this.random = new Random(seed);
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
            Shape realShape = locateRealShape(shape.asMemberShape(), shape);
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
                case LIST:
                case SET:
                    ListShape ls = realShape.asListShape().get();
                    Optional<RandomInstanceGenerator<?>> memberGenerator = generatorFor(ls.getMember());
                    if (memberGenerator.isPresent()) {
                        result = new ListAndSetGenerator(this, ls, traits, memberGenerator.get());
                    }
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
                            src.lineComment(" * " + sh.getType() + " " + sh.getId().getName()
                                    + " " + mem.getMemberName());
                        }
                        generators.remove(shape.getId());
                    }
                    break;
                case UNION:
                    List<RandomInstanceGenerator<?>> unionMembers = new ArrayList<>();
                    realShape.getAllMembers().forEach((nm, mem)
                            -> generatorFor(mem).ifPresent(unionMembers::add));
                    if (!unionMembers.isEmpty()) {
                        result = new UnionGenerator(this, realShape, traits, unionMembers);
                    }
                    break;
                case MAP:
                    MapShape mapShape = realShape.asMapShape().get();
                    Optional<RandomInstanceGenerator<?>> keyGen = generatorFor(mapShape.getKey());
                    Optional<RandomInstanceGenerator<?>> valGen = generatorFor(mapShape.getValue());
                    if (keyGen.isPresent() && valGen.isPresent()) {
                        result = new RandomMapGenerator(this, mapShape, traits, keyGen.get(),
                                valGen.get());
                    } else {
                        if (!keyGen.isPresent()) {
                            src.lineComment("No key generator for " + realShape.getId().getName());
                        }
                        if (!valGen.isPresent()) {
                            src.lineComment("No value generator for " + realShape.getId().getName());
                        }
                    }
                    break;
            }
            if (result != null) {
                importer.accept(realShape);
                generators.put(shape.getId(), result);
            } else {
                src.lineComment("NO GENERATOR FOR " + realShape.getType()
                        + " " + realShape.getId().getName() + " for " + shape.getId());
            }
            return Optional.ofNullable(result);
        }

        private Shape locateRealShape(Optional<MemberShape> memberShape, Shape originalShape) {
            Shape realShape;
            if (memberShape.isPresent()) {
                realShape = model.expectShape(memberShape.get().getTarget());
            } else {
                realShape = originalShape;
            }
            // If we have a Mixin type, we need to reify that into some concrete implementation
            // of it.  While we don't generate tests for interfaces (since there's nothing to
            // test), they may appear as members of other types
            if (realShape.getTrait(MixinTrait.class).isPresent()) {
                List<Shape> candidates = new ArrayList<>();
                model.getShapeIds().forEach(sid -> {
                    Shape s = model.expectShape(sid);
                    if (s.getType() == STRUCTURE) {
                        if (s.getMixins().contains(originalShape.getId())
                                && !s.getTrait(MixinTrait.class).isPresent()) {
                            candidates.add(s);
                        }
                    }
                });
                if (!candidates.isEmpty()) {
                    return candidates.get(random.nextInt(candidates.size() + 1));
                } else {
                    throw new ExpectationNotMetException("Test needs an implementation of "
                            + realShape.getId() + " but none can be found in the model.", realShape);
                }
            }
            return realShape;
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

        default boolean invalidPermutationsExhausted() {
            return true;
        }

        default boolean validPermutationsExhausted() {
            return true;
        }
    }

    static abstract class RandomInstance<S extends Shape> {

        final Map<ShapeId, RandomInstance<?>> inputs = new HashMap<>();
        final S shape;
        final boolean valid;
        String lastVarName;
        Exception created = new Exception();

        RandomInstance(S shape, boolean valid) {
            this.shape = shape;
            this.valid = valid;
        }

        String lastVarName() {
            return lastVarName;
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

        String lastVarName(String name) {
            this.lastVarName = name;
            return name;
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
                if (shape.isEnumShape()) {
                    decl.assignedToStringLiteral(value);
                } else {
                    decl.assignedToNew().withStringLiteralArgument(value).ofType(type);
                }
            } else {
                decl.assignedToStringLiteral(value);
            }
            return lastVarName(varName);
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
        private final String invalidity;

        RandomNumberInstance(Number number, Shape shape, String invalidity) {
            super(shape, invalidity == null);
            this.value = number;
            this.invalidity = invalidity;
        }

        @Override
        Optional<String> invalidityDescription() {
            if (valid) {
                return Optional.empty();
            }
            return Optional.of(Escaper.JAVA_IDENTIFIER_DELIMITED.escape(value.toString() + "_" + invalidity));
        }

        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String varName = ctx.varFor(shape);
            if (!valid) {
                bb.lineComment("Invalid. " + invalidity);
            } else {
                bb.lineComment("Valid.");
            }
            Assignment<B> decl = bb.declareConst(varName);
            String type = ctx.strategy(shape).targetType();
            decl.ofType(type);
            if (ctx.strategy(shape).isModelDefined()) {
                decl.assignedToNew().withArgument(value).ofType(type);
            } else {
                decl.assignedTo(value);
            }
            return lastVarName(varName);
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
            return lastVarName(varName);
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

    interface NamedInstanceFetcher {

        List<RandomInstance<?>> get(TestContext ctx);

        String name();
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
                return Optional.ofNullable(newInvalidValue(ctx));
            }
            return Optional.empty();
        }

        @Override
        public Optional<RandomInstance<S>> valid(TestContext ctx) {
            if (canGenerateValidValues()) {
                return Optional.ofNullable(newValidValue(ctx));
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

                return new RandomNumberInstance(target, shape, null);

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
                return new RandomNumberInstance(target, shape, null);
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

                return new RandomNumberInstance(target, shape, "outside_range");

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
                return new RandomNumberInstance(target, shape, "outside_range");
            }
        }
    }

    static abstract class AbstractStringInstanceGenerator<S extends Shape>
            extends AbstractInstanceGenerator<S> {

        private Optional<Xeger> xeger;
        private Optional<Xeger> confounded;

        AbstractStringInstanceGenerator(TestContext ctx, S shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        boolean hasPatternTrait() {
            return traits.find(PatternTrait.class).isPresent();
        }

        Optional<Xeger> xeger() {
            if (xeger == null) {
                if (!hasPatternTrait()) {
                    return xeger = Optional.empty();
                }
                String pattern = traits.find(PatternTrait.class).get().getValue();
                return xeger = Optional.of(new Xeger(pattern));
            }
            return xeger;
        }

        Optional<Xeger> confounded() {
            if (confounded != null) {
                return confounded;
            }
            return confounded == null
                    ? confounded = xeger().flatMap(x -> x.confound())
                    : confounded;
        }

        Optional<String> xegerInvalidSample(TestContext ctx) {
            return confounded().flatMap(xeger -> {
                for (int i = 0; i < 10; i++) {
                    Optional<String> str = xeger.emitChecked(ctx.rnd(), 100);
                    if (str.isPresent()) {
                        return str;
                    }
                }
                return Optional.empty();
            });
        }

        Optional<String> xegerValidSample(TestContext ctx) {
            return xeger().flatMap(xeger -> {
                for (int i = 0; i < 10; i++) {
                    Optional<String> str = xeger.emitChecked(ctx.rnd(), 100);
                    if (str.isPresent()) {
                        Optional<LengthTrait> len = traits.find(LengthTrait.class);
                        if (len.isPresent()) {
                            int min = len.get().getMin().map(m -> m.intValue())
                                    .orElse(0);
                            int max = len.get().getMax().map(m -> m.intValue())
                                    .orElse(Integer.MAX_VALUE);
                            String result = str.get();
                            if (result.length() >= min && result.length() <= max) {
                                return Optional.of(result);
                            }
                        }
                    }
                }
                return Optional.empty();
            });
        }
    }

    static class SamplesStringShape extends AbstractStringInstanceGenerator<StringShape> {

        private final SamplesTrait samples;
        private final List<String> valids;
        private final List<String> invalids;
        private int invalidCursor;
        private int validCursor;

        SamplesStringShape(TestContext ctx, StringShape shape, TraitFinder traits, SamplesTrait samples) {
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
                Supplier<String> fromSamples = ()
                        -> invalids.get((invalidCursor++) % invalids.size());
                if (invalidCursor >= invalids.size()) {
                    invalidCursor++;
                    result = xegerInvalidSample(ctx)
                            .orElseGet(fromSamples);
                } else {
                    result = fromSamples.get();
                }
            } else {
                Supplier<String> fromSamples = ()
                        -> valids.get((validCursor++) % valids.size());
                if (validCursor >= valids.size()) {
                    validCursor++;
                    result = xegerValidSample(ctx)
                            .orElseGet(fromSamples);
                } else {
                    result = fromSamples.get();
                }
            }
            return result;
        }

        @Override
        public boolean invalidPermutationsExhausted() {
            return invalidCursor >= invalids.size();
        }

        @Override
        public boolean validPermutationsExhausted() {
            return validCursor >= valids.size() + 1;
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return !invalids.isEmpty() || hasPatternTrait();
        }

        @Override
        public boolean canGenerateValidValues() {
            return !valids.isEmpty() || hasPatternTrait();
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

    static class StringInstanceGenerator extends AbstractStringInstanceGenerator<StringShape> {

        static final char[] ALPHANUM
                = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

        StringInstanceGenerator(TestContext ctx, StringShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
        }

        @Override
        protected RandomInstance<StringShape> newInvalidValue(TestContext ctx) {
            return randomInvalidString(ctx).map(str
                    -> new RandomStringInstance<>(str, shape, false)
            ).orElse(null);
        }

        @Override
        protected RandomInstance<StringShape> newValidValue(TestContext ctx) {
            return randomString(ctx).map(str
                    -> new RandomStringInstance<>(str, shape, true)
            ).orElse(null);
        }

        Optional<String> randomInvalidString(TestContext ctx) {
            if (hasPatternTrait()) {
                return xegerInvalidSample(ctx);
            }
            int min = minLength();
            int max = maxLength();
            if (min == 1 && max > 256) {
                return Optional.of("");
            }
            int target;
            if (min > 1) {
                target = ctx.rnd().nextInt(min);
            } else {
                int base = max + 1;
                target = base + ctx.rnd().nextInt(12);
            }
            return Optional.of(randomString(target));
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

        Optional<String> randomString(TestContext ctx) {
            if (hasPatternTrait()) {
                return xegerValidSample(ctx);
            }
            if (!traits.find(LengthTrait.class).isPresent()) {
                int minLength;
                if (traits.find(RequiredTrait.class).isPresent()) {
                    minLength = 0;
                } else {
                    // The empty string can be deserialized as undefined,
                    // making deserialization tests fail because the property
                    // is absent, so always generate at least one character
                    // of output for these
                    minLength = 1;
                }
                int len = ctx.rnd().nextInt(7) + minLength;
                return Optional.of(randomString(len));
            }
            int min = minLength();
            int max = maxLength();
            if (min == max) {
                return Optional.of(randomString(max));
            }
            // Don't really try to create an Integer.MAX_VALUE length string
            int realMax = Math.min(max, min + 128);
            int targetLength = min + ctx.rnd().nextInt(realMax - min);
            return Optional.of(randomString(targetLength));
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

        @Override
        public boolean canGenerateInvalidValues() {
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

        private final List<String> all;
        private int cursor;

        RandomStringEnumInstanceGenerator(TestContext ctx, EnumShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
            all = new ArrayList<>(shape.getEnumValues().values());
            shuffle(all, ctx.rnd());
        }

        @Override
        protected RandomInstance<EnumShape> newValidValue(TestContext ctx) {
            String target = all.get((cursor++ % all.size()));
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

        private final ArrayList<String> all;
        private int cursor;

        RandomNamedStringEnumInstanceGenerator(TestContext ctx, EnumShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
            all = new ArrayList<>(shape.getEnumValues().keySet());
            shuffle(all, ctx.rnd());
        }

        @Override
        protected RandomInstance<EnumShape> newValidValue(TestContext ctx) {
            String target = all.get((cursor++ % all.size()));
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

        private final List<Integer> all;
        int cursor;

        RandomIntEnumInstanceGenerator(TestContext ctx, IntEnumShape shape, TraitFinder traits) {
            super(ctx, shape, traits);
            all = new ArrayList<>(shape.getEnumValues().values());
            shuffle(all, ctx.rnd());
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
            int index = all.get(cursor++ % all.size());
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
            return lastVarName(varName);
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
            return lastVarName(varName);
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
            return lastVarName(varName);
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

    static class ValueBoundsInfo {

        private final int maxValues;
        private final boolean isSufficient;

        ValueBoundsInfo(int maxValues, boolean isSufficient) {
            this.maxValues = maxValues;
            this.isSufficient = isSufficient;
        }

        int constrain(int targetAmount) {
            return Math.min(maxValues, targetAmount);
        }
    }

    interface FiniteInvalidInstanceGenerator {

        int totalValidValues();

        int totalInvalidValues();

        default ValueBoundsInfo canCreateSufficientValidValues(LengthTrait t) {
            int tot = totalValidValues();
            if (tot == Integer.MAX_VALUE) {
                return new ValueBoundsInfo(tot, true);
            }
            return t.getMin().map(m -> new ValueBoundsInfo(m.intValue(), m.intValue() > tot))
                    .orElse(new ValueBoundsInfo(-1, false));
        }

        default ValueBoundsInfo canCreateSufficientInvalidValues(LengthTrait t) {
            return null; // FIXME
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
                RandomInstance<?> ri = randomInstanceFrom(e.getKey(), e.getValue(), true);
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
                    ri = randomInstanceFrom(e.getKey(), bad.getValue(), false);
                } else {
                    ri = randomInstanceFrom(e.getKey(), e.getValue(), true);
                }
                members.put(e.getKey(), ri);
            }
            return new RandomStructureInstance(shape, false, members, bad.getKey());
        }

        private <T extends Shape> RandomInstance<T> randomInstanceFrom(MemberShape shape, RandomInstanceGenerator<T> r, boolean valid) {

            Optional<RandomInstance<T>> result = valid ? r.valid(ctx) : r.invalid(ctx);

            if (!result.isPresent()) {
                throw new IllegalStateException("Could not get a "
                        + (valid ? "valid" : "invalid") + " instance from a "
                        + r.getClass().getSimpleName()
                        + " for " + shape.getId()
                );
            }

            return result.get();
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
        public boolean invalidPermutationsExhausted() {
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

        @Override
        public boolean canGenerateInvalidValues() {
            if (!canGenerateValidValues()) {
                return false;
            }
            return !invalidValueCapable().isEmpty();
        }

        @Override
        public boolean canGenerateValidValues() {
            for (Map.Entry<MemberShape, RandomInstanceGenerator<?>> g : memberGenerators.entrySet()) {
                if (!g.getValue().canGenerateValidValues()) {
                    return false;
                }
            }
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
            List<MemberShape> result = new ArrayList<>();
            for (Map.Entry<String, MemberShape> e : SimpleStructureGenerator.membersOptionalLast(shape)) {
                result.add(e.getValue());
            }
            return result;
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String type = ctx.strategy(shape).targetType();
            bb.blankLine().lineComment(getClass().getSimpleName() + " for " + type);

            if (!valid) {
                bb.lineComment("Invalid: " + this.invalidityDescription().orElse("")
                        + " " + type + " via "
                        + invalidItem.getId().getMember().map(m -> "." + m).orElse(""));
            }

            String varName = ctx.varFor(shape);

            List<String> varNames = new ArrayList<>();
            for (MemberShape mem : eachMemberOptionalLast()) {
                RandomInstance<?> ri = members.get(mem);
                if (mem.equals(invalidItem)) {
                    bb.lineComment("Deliberately invalid: " + mem.getId().getName()
                            + mem.getId().getMember().map(m -> "." + m).orElse(""));
                }
                varNames.add(ri.instantiate(bb, ctx));
            }
            NewBuilder<B> nue = bb.declareConst(varName).ofType(type)
                    .assignedToNew();
            for (String v : varNames) {
                nue.withArgument(v);
            }
            nue.ofType(type);

            return lastVarName(varName);
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

    static class ListAndSetGenerator extends AbstractInstanceGenerator<ListShape> {

        private final RandomInstanceGenerator<?> memberGenerator;
        private final List<NamedInstanceFetcher> fetchers = new ArrayList<>();
        private int fetcherCursor;

        ListAndSetGenerator(TestContext ctx, ListShape shape, TraitFinder traits,
                RandomInstanceGenerator<?> memberGenerator) {
            super(ctx, shape, traits);
            this.memberGenerator = memberGenerator;
            populateFetchers();
        }

        private void populateFetchers() {
            if (!canGenerateInvalidValues()) {
                return;
            }
            int min = min();
            if (min > 0 && min < 256) {
                fetchers.add(invalidTooSmall());
            }
            int max = max();
            if (max < 256) {
                fetchers.add(invalidTooLarge());
            }
            if (memberGenerator.canGenerateInvalidValues()) {
                fetchers.add(invalidListMember());
            }
        }

        private int min() {
            long res = traits.find(LengthTrait.class)
                    .flatMap(len -> len.getMin()).orElse(0L);
            return (int) res;
        }

        private int max() {
            long res = traits.find(LengthTrait.class)
                    .flatMap(len -> len.getMax()).orElse((long) Integer.MAX_VALUE);
            return (int) res;
        }

        NamedInstanceFetcher invalidListMember() {
            return new NamedInstanceFetcher() {
                @Override
                public List<RandomInstance<?>> get(TestContext ctx) {
                    return ListAndSetGenerator.this.invalidListMember(ctx);
                }

                @Override
                public String name() {
                    return "member";
                }
            };
        }

        NamedInstanceFetcher invalidTooSmall() {
            return new NamedInstanceFetcher() {
                @Override
                public List<RandomInstance<?>> get(TestContext ctx) {
                    return ListAndSetGenerator.this.invalidItemsTooSmall(ctx);
                }

                @Override
                public String name() {
                    return "too_small";
                }
            };
        }

        NamedInstanceFetcher invalidTooLarge() {
            return new NamedInstanceFetcher() {
                @Override
                public List<RandomInstance<?>> get(TestContext ctx) {
                    return ListAndSetGenerator.this.invalidItemsTooLarge(ctx);
                }

                @Override
                public String name() {
                    return "too_small";
                }
            };
        }

        List<RandomInstance<?>> invalidListMember(TestContext ctx) {
            List<RandomInstance<?>> result = new ArrayList<>();

            int min = min();
            int max = max();
            if (max == 1) {
                return asList(memberGenerator.invalid(ctx).get());
            }
            int count;
            if (max == min) {
                count = max;
            } else if (max >= Integer.MAX_VALUE / 2) {
                int workingRange = ctx.rnd().nextInt(12) + 4;
                count = min + workingRange;
            } else {
                int workingRange = Math.min(12, max - min);
                count = min + ctx.rnd().nextInt(workingRange);
            }
            int invalidOne = ctx.rnd().nextInt(count);
            for (int i = 0; i < count; i++) {
                if (i == invalidOne) {
                    result.add(memberGenerator.invalid(ctx).get());
                } else {
                    result.add(memberGenerator.valid(ctx).get());
                }
            }
            return result;
        }

        List<RandomInstance<?>> invalidItemsTooSmall(TestContext ctx) {
            List<RandomInstance<?>> result = new ArrayList<>();
            int min = min();
            if (min == 1) {
                return emptyList();
            }
            int count = Math.min(Math.min(min, 16), ctx.rnd().nextInt(min));
            for (int i = 0; i < count; i++) {
                result.add(memberGenerator.valid(ctx).get());
            }
            return result;
        }

        List<RandomInstance<?>> invalidItemsTooLarge(TestContext ctx) {
            List<RandomInstance<?>> result = new ArrayList<>();
            int max = max();
            int count = max + 1 + ctx.rnd().nextInt(24);
            for (int i = 0; i < count; i++) {
                result.add(memberGenerator.valid(ctx).get());
            }
            return result;
        }

        List<RandomInstance<?>> invalidItems(TestContext ctx) {
            return fetchers.get((fetcherCursor++) % fetchers.size()).get(ctx);
        }

        NamedInstanceFetcher currentFetcher() {
            return fetchers.get(fetcherCursor % fetchers.size());
        }

        List<RandomInstance<?>> validItems(TestContext ctx, Obj<String> comment) {
            List<RandomInstance<?>> result = new ArrayList<>();
            int min = min();
            int max = max();
            if (max == min) {
                comment.set("Max exactly equals min of " + min);
                List<RandomInstance<?>> l = new ArrayList<>();
                for (int i = 0; i < max; i++) {
                    l.add(memberGenerator.valid(ctx).get());
                }
                return l;
            }
            int count;
            if (max == Integer.MAX_VALUE && min == 0) {
                // (max - min) + 1 will equal zero
                count = ctx.rnd().nextInt(8);
            } else {
                int range = Math.max(0, Math.min(8, (max - min) + 1));
                if (range == 0) {
                    throw new Error("Max: " + max + " / Min: " + min + " got range of 0");
                }
                count = min + ctx.rnd().nextInt(range);
            }
            // deleteme - debug
            comment.set("Valid items: max = " + max + " min = " + min + " targetting " + count);
            for (int i = 0; i < count; i++) {
                result.add(memberGenerator.valid(ctx).get());
            }
            return result;
        }

        @Override
        public boolean invalidPermutationsExhausted() {
            return fetcherCursor >= fetchers.size();
        }

        @Override
        public boolean canGenerateInvalidValues() {
            boolean result = memberGenerator.canGenerateInvalidValues();
            if (!result) {
                result = traits.find(LengthTrait.class).map(tr -> {
                    long min = tr.getMin().orElse(0L);
                    long max = tr.getMax().orElse((long) Integer.MAX_VALUE);
                    boolean res = (min > 0 && min < 256) || max < 256;
                    return res;
                }).orElse(false);
            }
            return result;
        }

        private boolean checkCanGenerateEnoughValues(LengthTrait len) {
            return true;
        }

        @Override
        public boolean canGenerateValidValues() {
            return memberGenerator.canGenerateValidValues();
        }

        @Override
        protected RandomInstance<ListShape> newValidValue(TestContext ctx) {
            Obj<String> cmt = Obj.create();
            List<RandomInstance<?>> items = validItems(ctx, cmt);
            return new RandomListInstance(shape, items, null, cmt.get());
        }

        @Override
        protected RandomInstance<ListShape> newInvalidValue(TestContext ctx) {
            String invalidity = currentFetcher().name();
            if (invalidity == null) {
                throw new Error("Invalidity should not be null.");
            }
            return new RandomListInstance(shape, invalidItems(ctx), invalidity, invalidity);
        }
    }

    static class RandomListInstance extends RandomInstance<ListShape> {

        private final String invalidity;
        private final List<RandomInstance<?>> members;
        private final boolean isSet;
        private final String comment;

        @SuppressWarnings("deprecation")
        public RandomListInstance(ListShape shape, List<RandomInstance<?>> members,
                String invalidity, String comment) {
            super(shape, invalidity == null);
            this.members = members;
            this.invalidity = invalidity;
            isSet = shape.isSetShape() || shape.getTrait(UniqueItemsTrait.class).isPresent();
            this.comment = comment;
        }

        @Override
        Optional<String> invalidityDescription() {
            return Optional.ofNullable(invalidity);
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            if (!valid) {
                bb.lineComment("Should be invalid: " + invalidity);
            } else {
                bb.lineComment("Valid list instance");
            }
            List<String> vars = new ArrayList<>();
            members.forEach(mem -> vars.add(mem.instantiate(bb, ctx)));
            TypeStrategy<?> strat = ctx.strategy(shape);
            String varName = ctx.varFor(shape);
            if (comment != null) {
                bb.blankLine().lineComment(comment);
            }
            bb.declare(varName).ofType(strat.targetType()).assignedToNew(nb -> {
                nb.withArrayLiteral(ab -> {
                    vars.forEach(v -> ab.expression(v));
                }).ofType(strat.targetType());
            });
            return lastVarName(varName);
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

    static class UnionGenerator extends AbstractInstanceGenerator<Shape> {

        private final List<RandomInstanceGenerator<?>> validCapable = new ArrayList<>();
        private int validCursor;
        private final List<RandomInstanceGenerator<?>> invalidCapable = new ArrayList<>();
        private int invalidCursor;

        UnionGenerator(TestContext ctx, Shape shape, TraitFinder traits,
                List<RandomInstanceGenerator<?>> members) {
            super(ctx, shape, traits);
            for (RandomInstanceGenerator<?> g : members) {
                if (g.canGenerateValidValues()) {
                    validCapable.add(g);
                }
                if (g.canGenerateInvalidValues()) {
                    invalidCapable.add(g);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected RandomInstance<Shape> newValidValue(TestContext ctx) {
            RandomInstanceGenerator<?> gen = validCapable.get(
                    (validCursor++) % validCapable.size());
            RandomInstance<?> result = gen.valid(ctx).get();
            return (RandomInstance<Shape>) result;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected RandomInstance<Shape> newInvalidValue(TestContext ctx) {
            RandomInstanceGenerator<?> gen = invalidCapable.get(
                    (invalidCursor++) % invalidCapable.size());
            RandomInstance<?> result = gen.invalid(ctx).get();
            return (RandomInstance<Shape>) result;
        }

        @Override
        public boolean canGenerateInvalidValues() {
            return !invalidCapable.isEmpty();
        }

        @Override
        public boolean canGenerateValidValues() {
            return !validCapable.isEmpty();
        }

        @Override
        public boolean invalidPermutationsExhausted() {
            return invalidCursor >= invalidCapable.size();
        }

        @Override
        public boolean validPermutationsExhausted() {
            return validCursor >= validCapable.size();
        }
    }

    static class MapPair {

        final RandomInstance<?> key;
        final RandomInstance<?> value;

        MapPair(RandomInstance<?> key, RandomInstance<?> value) {
            this.key = key;
            this.value = value;
        }
    }

    static final class RandomMapInstance extends RandomInstance<MapShape> {

        private final List<MapPair> pairs;
        private final String invalidity;

        RandomMapInstance(MapShape shape,
                List<MapPair> pairs, String invalidity) {
            super(shape, invalidity == null);
            this.pairs = pairs;
            this.invalidity = invalidity;
        }

        @Override
        Optional<String> invalidityDescription() {
            return Optional.ofNullable(invalidity);
        }

        @Override
        <B extends TsBlockBuilderBase<T, B>, T> String instantiate(B bb, TestContext ctx) {
            String vn = ctx.varFor(shape);
            TypeStrategy<?> strat = ctx.strategy(shape);
            if (invalidity != null) {
                bb.blankLine().lineComment("Invalid via " + invalidity + " with " + pairs.size());
            } else {
                bb.lineComment("Valid map with " + pairs.size());
            }
            bb.declare(vn).ofType(strat.targetType())
                    .assignedToNew().ofType(strat.targetType());

            pairs.forEach(pair -> {
                String k = pair.key.instantiate(bb, ctx);
                String v = pair.value.instantiate(bb, ctx);
                bb.invoke("set").withArgument(k).withArgument(v).on(vn);
            });

            return vn;
        }
    }

    interface NamedFunction<T, R> extends Function<T, R> {

        String name();
    }

    static <T, R> NamedFunction<T, R> namedFunction(String name, Function<? super T, ? extends R> f) {
        return new NamedWrapper<>(name, f);
    }

    private static final class NamedWrapper<T, R> implements NamedFunction<T, R> {

        private final String name;
        private final Function<? super T, ? extends R> func;

        public NamedWrapper(String name, Function<? super T, ? extends R> func) {
            this.name = name;
            this.func = func;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public R apply(T t) {
            return func.apply(t);
        }

    }

    static final class RandomMapGenerator extends AbstractInstanceGenerator<MapShape> {

        private final RandomInstanceGenerator<?> keys;
        private final RandomInstanceGenerator<?> values;
        private final List<NamedFunction<TestContext, RandomInstance<MapShape>>> invalidGenerators
                = new ArrayList<>();
        private int invalidGeneratorsCursor;

        public RandomMapGenerator(TestContext ctx, MapShape shape, TraitFinder traits,
                RandomInstanceGenerator<?> keys, RandomInstanceGenerator<?> values) {
            super(ctx, shape, traits);
            this.keys = keys;
            this.values = values;
            if (keys.canGenerateInvalidValues()) {
                invalidGenerators.add(namedFunction("with_invalid_value", this::invalidKeyMap));
            }
            if (keys.canGenerateInvalidValues()) {
                invalidGenerators.add(namedFunction("with_invalid_key", this::invalidValueMap));
            }
            if (canCreateInvalidLengthLow()) {
                invalidGenerators.add(namedFunction("too_small", this::invalidValueLengthLow));
            }
            if (canCreateInvalidLengthHigh()) {
                invalidGenerators.add(namedFunction("too_large", this::invalidValueLengthHigh));
            }
        }

        @Override
        public boolean invalidPermutationsExhausted() {
            return invalidGeneratorsCursor >= invalidGenerators.size();
        }

        NamedFunction<TestContext, RandomInstance<MapShape>> nextInvalidGenerator() {
            return invalidGenerators.get((invalidGeneratorsCursor++) % invalidGenerators.size());
        }

        int minSize() {
            return traits.find(LengthTrait.class).flatMap(len -> len.getMin().map(l -> l.intValue())).orElse(0);
        }

        int maxSize() {
            return traits.find(LengthTrait.class).flatMap(len -> len.getMin().map(l -> l.intValue())).orElse(Integer.MAX_VALUE);
        }

        private int validTargetSize() {
            int min = minSize();
            int max = maxSize();
            if (max == 0) {
                return 0;
            }
            int target;
            if (min == max) {
                target = min;
            } else if (max > Integer.MAX_VALUE / 2) {
                target = Math.max(0, min) + +ctx.rnd().nextInt(12);
            } else {
                int range = (max - min) + 1;
                if (range > 8) {
                    range = 8;
                }
                if (range <= 0) {
                    throw new IllegalStateException("Invalid range " + range
                            + " for " + min + "," + max);
                }
                target = min + Math.max(1, ctx.rnd().nextInt(range));
            }
            return target;
        }

        @Override
        protected RandomInstance<MapShape> newValidValue(TestContext ctx) {
            int sz = validTargetSize();
            return createFromValidPairs(sz, ctx, null);
        }

        private RandomInstance<MapShape> createFromValidPairs(int sz, TestContext ctx1, String inv) {
            List<MapPair> pairs = new ArrayList<>(sz);
            for (int i = 0; i < sz; i++) {
                pairs.add(validPair(ctx1));
            }
            return new RandomMapInstance(shape, pairs, inv);
        }

        @Override
        protected RandomInstance<MapShape> newInvalidValue(TestContext ctx) {
            return nextInvalidGenerator().apply(ctx);
        }

        private RandomInstance<MapShape> invalidKeyMap(TestContext ctx) {
            
            int sz = validTargetSize();
            int culprit = sz == 1 ? 0 : ctx.rnd().nextInt(sz);
            List<MapPair> pairs = new ArrayList<>(sz);
            for (int i = 0; i < sz; i++) {
                if (i == culprit) {
                    pairs.add(invalidKeyPair(ctx));
                } else {
                    pairs.add(validPair(ctx));
                }
            }
            return new RandomMapInstance(shape, pairs, "invalid_key");
        }

        private RandomInstance<MapShape> invalidValueMap(TestContext ctx) {
            int sz = validTargetSize();
            int culprit = sz == 1 ? 0 : ctx.rnd().nextInt(sz);
            List<MapPair> pairs = new ArrayList<>(sz);
            for (int i = 0; i < sz; i++) {
                if (i == culprit) {
                    pairs.add(invalidValuePair(ctx));
                } else {
                    pairs.add(validPair(ctx));
                }
            }
            return new RandomMapInstance(shape, pairs, "invalid_value");
        }

        private RandomInstance<MapShape> invalidValueLengthLow(TestContext ctx) {
            int min = minSize();
            assert min > 0;
            int target;
            if (min > 1) {
                target = ctx.rnd().nextInt(min - 1);
            } else {
                target = 0;
            }
            return createFromValidPairs(target, ctx, "below_minimum_length");
        }

        private RandomInstance<MapShape> invalidValueLengthHigh(TestContext ctx) {
            int max = maxSize();
            assert max < Integer.MAX_VALUE;
            int start = max + 1;
            int target = ctx.rnd().nextInt(8) + start;
            return createFromValidPairs(target, ctx, "above_maximum_length");
        }

        boolean canCreateInvalidLengthHigh() {
            traits.find(LengthTrait.class).flatMap(len -> {
                return len.getMin().map(min -> {
                    int max = len.getMax().map(mx -> mx.intValue()).orElse(Integer.MAX_VALUE);
                    if (min >= 256 || max > 256) {
                        return false;
                    }
                    return true;
                });
            }).orElse(false);
            return false;
        }

        boolean canCreateInvalidLengthLow() {
            return traits.find(LengthTrait.class).flatMap(len -> {
                return len.getMin().map(min -> {
                    return min > 0;
                });
            }).orElse(false);
        }

        private MapPair validPair(TestContext ctx) {
            return new MapPair(keys.valid(ctx).get(), values.valid(ctx).get());
        }

        private MapPair invalidKeyPair(TestContext ctx) {
            return new MapPair(keys.invalid(ctx).get(), values.valid(ctx).get());
        }

        private MapPair invalidValuePair(TestContext ctx) {
            return new MapPair(keys.valid(ctx).get(), values.invalid(ctx).get());
        }

        @Override
        public boolean canGenerateInvalidValues() {
            if (!canGenerateValidValues()) {
                return false;
            }
            if (keys.canGenerateInvalidValues() || values.canGenerateInvalidValues()) {
                return true;
            }
            return false;
        }

        @Override
        public boolean canGenerateValidValues() {
            return !invalidGenerators.isEmpty();
        }

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

    static int invalidCountForMinMax(int min, int max, Random rnd) {
        if (min > 2) {
            int range = min - 1;
            return rnd.nextInt(range - 1) + 1;
        } else if (max < 512 && max > 0) {
            return max + 1 + rnd.nextInt(12);
        } else if (min == 2) {
            return 1;
        }
        throw new IllegalArgumentException("Cannot create an invalid size for "
                + min + " thru " + max);
    }

    static int countForMinMax(int min, int max, Random rnd) {
        int count;
        if (max == min && max > 0) {
            return min + 1;
        } else if (min == -1 && max == -1) {
            count = 1;
        } else if (min > 0 && (max == -1 || max > Integer.MAX_VALUE / 2)) {
            count = min + rnd.nextInt(12);
        } else if (min > 0 && max > 0) {
            if (min == max) {
                count = min;
            } else {
                int range = min(12, max - min);
                count = min + rnd.nextInt(range);
            }
        } else {
            count = 1;
        }
        return count;
    }

}
