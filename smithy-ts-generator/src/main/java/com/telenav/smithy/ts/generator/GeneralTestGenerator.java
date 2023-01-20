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

import com.mastfrog.function.state.Int;
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.SettingsKey;
import com.telenav.smithy.ts.generator.type.MemberStrategy;
import com.telenav.smithy.ts.generator.type.TypeStrategies;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.DOCUMENT;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import static software.amazon.smithy.model.shapes.ShapeType.SET;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.MixinTrait;

/**
 *
 * @author Tim Boudreau
 */
final class GeneralTestGenerator<S extends Shape> extends AbtractTsTestGenerator<S> {

    private final SettingsKey<Set<Model>> WKEY
            = SettingsKey.key(Set.class, "testSupportWritten");
    private final Set<TypescriptSource> functionListDefinedFor
            = Collections.newSetFromMap(new IdentityHashMap<>());
//            = Collections.newSetFromMap(new WeakHashMap<>());

    private static final String TEST_SUPPORT_TS_FILENAME = "test-support.ts";
    private static final String TEST_SUPPORT_IMPORT_FILENAME = "./test-support.js";
    private static final String TEST_FUNCTIONS_ARRAY_NAME = "testFunctions";
    private static final String SERVICE_MODEL_REPLACEMENT_TOKEN = "$SERVICE_MODEL";

    GeneralTestGenerator(S shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    private boolean shouldAddTestSupport() {
        Set<Model> modelsGeneratedFor = ctx.computeIfAbsent(WKEY, () -> {
            return newSetFromMap(synchronizedMap(new WeakHashMap<>()));
        });
        return modelsGeneratedFor.add(model);
    }

    @Override
    protected void generateAdditional(Consumer<GeneratedCode> c) {
        if (shouldAddTestSupport()) {
            c.accept(resource(TEST_SUPPORT_TS_FILENAME, TEST_SUPPORT_TS_FILENAME,
                    literalSubstitution(SERVICE_MODEL_REPLACEMENT_TOKEN, modelSourcePath())));
        }
    }

    @Override
    protected boolean canGenerateTests() {
        if (isNotUserType(shape)) {
            return false;
        }
        switch (shape.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case DOUBLE:
            case INTEGER:
            case FLOAT:
            case LONG:
            case BYTE:
            case STRING:
            case TIMESTAMP:
            case BOOLEAN:
            case LIST:
            case MAP:
            case DOCUMENT:
            case SHORT:
            case SET:
                return true;
            case STRUCTURE:
                // We don't test interfaces, just implementations
                return !shape.getTrait(MixinTrait.class).isPresent();
            default:
                return false;
        }
    }

    private void maybeDefineFunctionList(TypescriptSource src) {
        if (src.mark(TEST_FUNCTIONS_ARRAY_NAME)) {
            src.statement("type TestFunction = (suite: TestSuite) => void;");
            src.declareConst(TEST_FUNCTIONS_ARRAY_NAME).ofComplexType("Array").withTypeParameter("TestFunction").endType()
                    .assignedTo("[]");
            src.function("createTests", func -> {
                func.exported();
                func.docComment("Add all generated tests to the passed suite.");
                func.withArgument("suite").ofType("TestSuite");
                func.returning("TestSuite");
                func.body(bb -> {
                    bb.invoke("forEach")
                            .withLambda(lb -> {
                                lb.withArgument("populator").inferringType();
                                lb.body(lbb -> {
                                    lbb.invoke("populator").withArgument("suite")
                                            .inScope();
                                });
                            }).on(TEST_FUNCTIONS_ARRAY_NAME);
                    bb.returning("suite");
                });
            });
        }
    }

    private void addTestFunction(String testFunction, TypescriptSource src) {
        src.invoke("push").withArgument(testFunction).on(TEST_FUNCTIONS_ARRAY_NAME);
    }

    @Override
    @SuppressWarnings({"rawtype", "unchecked"})
    protected void generate(TypescriptSource src, TestContext testContext) {
        if (shape.isUnionShape()) {
            // We do not need direct tests of union shapes - they will be covered
            // by the tests of their members - they don't really exist in typescript code
            return;
        }
        testContext.generatorFor(shape).ifPresent(gen -> {
            if (gen instanceof UnionGenerator) {
                return;
            }
            String typeName = tsTypeName(shape);
            Optional<RandomInstance<?>> v = (Optional) gen.valid(testContext);

            src.importing("TestSuite", "expectValid", "expectInvalid", "expectToJsonKeys",
                    "expectEqual", "jsonConvertible", "map", "TestAdder", "TestChain",
                    "InputWithDescription", "TestCollection", "Test")
                    .from(TEST_SUPPORT_IMPORT_FILENAME);

            generateValidInstanceTestFunctions(gen, typeName, src, testContext);
            generateInvalidInstanceTestFunctions(gen, typeName, src, testContext);
        });
    }

    private void generateInvalidInstanceTestFunctions(RandomInstanceGenerator<?> gen, String typeName, TypescriptSource src, TestContext testContext) {
        if (gen.canGenerateInvalidValues()) {
            boolean multiplePermutations = !gen.invalidPermutationsExhausted();
            Int invalidCounter = Int.of(1);
            Consumer<RandomInstance<?>> invalidPermutationGenerator = instance -> {
                String suffix = instance.invalidityDescription().map(desc -> {
                    return "_invalid_" + desc + "_" + invalidCounter.increment();
                }).orElse(multiplePermutations ? "_" + invalidCounter.increment() : "");

                String populateFunction = escape("addInvalid" + typeName + "Tests" + suffix);
                src.function(populateFunction, f -> {
                    f.withArgument("suite").ofType("TestSuite");
                    f.body(bb -> {
                        instance.invalidityDescription().ifPresent(desc -> {
                            bb.lineComment("Invalid via " + desc);
                        });
                        bb.lineComment(gen.getClass().getSimpleName());
                        generateInvalidInstanceTest(instance, bb, testContext);
                    });
                });
                maybeDefineFunctionList(src);
                addTestFunction(populateFunction, src);
            };
            do {
                Optional<RandomInstance<?>> i = (Optional) gen.invalid(testContext);
                i.ifPresent(invalidPermutationGenerator);
            } while (!gen.invalidPermutationsExhausted());
        }
    }

    private void generateValidInstanceTestFunctions(RandomInstanceGenerator<?> gen, String typeName, TypescriptSource src, TestContext testContext) {
        if (gen.canGenerateValidValues()) {
            boolean multiplePermutations = !gen.validPermutationsExhausted();
            Int counter = Int.of(1);
            Consumer<RandomInstance<?>> validPermutationGenerator = instance -> {
                String suffix = multiplePermutations ? "_" + counter.increment() : "";
                String populateFunction = escape("addValid" + typeName + "Tests" + suffix);
                src.function(populateFunction, f -> {
                    f.withArgument("suite").ofType("TestSuite");
                    f.docComment("Tests the shape " + shape.getId().getName()
                            + " - fields, JSON seriialization, etc.");
                    f.body(bb -> {
                        bb.lineComment(gen.getClass().getSimpleName());
                        generateValidInstanceTest(instance, bb, testContext);
                    });
                });
                maybeDefineFunctionList(src);
                addTestFunction(populateFunction, src);
            };
            do {
                Optional<RandomInstance<?>> i = (Optional) gen.valid(testContext);
                i.ifPresent(validPermutationGenerator);
            } while (!gen.validPermutationsExhausted());
        }
    }

    private <B extends TsBlockBuilderBase<T, B>, T> void generateValidInstanceTest(RandomInstance<?> instance,
            B bb, TestContext testContext) {
        String varName = instance.instantiate(bb, testContext);
        bb.declare("processor")
                .assignedToInvocationOf("chain")
                .withLambda(lb -> {
                    lb.withArgument("chain").ofType("TestChain", pt -> pt.withTypeParameter(tsTypeName(shape)));
                    lb.body(lbb -> {
                        withTestImplGenerators(instance, gen -> {
                            lbb.lineComment(gen.getClass().getSimpleName() + " test generator");
                            gen.generate(shape, "chain", true, varName, lbb, testContext, instance, strategies);
                        });
                    });
                }).withStringLiteralArgument(tsTypeName(shape)).on("suite");
        bb.invoke("processor").withArrayLiteral(al -> {
            al.literal(shape.getId().getName() + "-valid");
            al.expression(varName);
        }).inScope();
    }

    private <B extends TsBlockBuilderBase<T, B>, T> void generateInvalidInstanceTest(RandomInstance<?> instance,
            B bb, TestContext testContext) {
        if (instance.valid) {
            throw new IllegalArgumentException("Should be generating an invalid instance, "
                    + "but was passed a VALID instance of " + instance.getClass().getSimpleName(),
                    instance.created);
        }
        String varName = instance.instantiate(bb, testContext);
        String info = instance.invalidityDescription().map(desc -> "-" + desc).orElse("");
        bb.declare("processor")
                .assignedToInvocationOf("chain")
                .withLambda(lb -> {
                    lb.withArgument("chain").ofType("TestChain", pt -> pt.withTypeParameter(tsTypeName(shape)));
                    lb.body(lbb -> {
                        withTestImplGenerators(instance, gen -> {
                            lbb.lineComment(gen.getClass().getSimpleName() + " test generator");
                            gen.generate(shape, "chain", false, varName, lbb, testContext, instance, strategies);
                        });
                    });
                }).withStringLiteralArgument(tsTypeName(shape) + info).on("suite");
        bb.invoke("processor").withArrayLiteral(al -> {

            al.literal(shape.getId().getName() + "-invalid" + info);
            al.expression(varName);
        }).inScope();
    }

    interface TestImplGenerator {

        <B extends TsBlockBuilderBase<T, B>, T> void generate(Shape shape, String suiteVar,
                boolean valid, String varName, B bb, TestContext testContext, RandomInstance<?> r,
                TypeStrategies strategies);
    }

    static class ValidateGenerator implements TestImplGenerator {

        @Override
        public <B extends TsBlockBuilderBase<T, B>, T> void generate(Shape shape, String collVar, boolean valid,
                String varName, B bb, TestContext testContext, RandomInstance<?> r, TypeStrategies strategies) {

            bb.invoke("add").withInvocationOf(valid ? "expectValid" : "expectInvalid").inScope().on(collVar);
            if (valid) {
                bb.invoke("add").withInvocationOf("jsonConvertible")
                        .withField("fromJSON").of(strategies.strategy(shape).targetType()).inScope().on(collVar);
            }
        }
    }

    static class MemberFieldCheckGenerator implements TestImplGenerator {

        @Override
        public <B extends TsBlockBuilderBase<T, B>, T> void generate(Shape shape, String chainVar, boolean valid, String varName, B bb, TestContext testContext, RandomInstance<?> r, TypeStrategies strategies) {
            StructureShape struct = shape.asStructureShape().get();
            String shapeType = strategies.strategy(shape).targetType();
            if (struct.getAllMembers().isEmpty()) {
                bb.lineComment("Struct has no members - omit members test");
                return;
            }
            Map<MemberShape, String> varNameForMember = new HashMap<>();
            struct.getAllMembers().forEach((name, mem) -> {
                RandomInstance gen = r.shapeForMember(mem);
                if (gen != null) {
                    String vn = gen.lastVarName();
                    if (vn != null) {
                        varNameForMember.put(mem, vn);
                    }
                }
            });
            if (varNameForMember.size() != shape.getAllMembers().size()) {
                bb.lineComment("Member mismatch - skip members test");
                return;
            }
            varNameForMember.forEach((mem, memberVar) -> {
                MemberStrategy<?> memStrat = strategies.memberStrategy(mem);
                String typeName = strategies.strategy(mem).targetType();
                bb.invoke("add", ib -> {
                    ib.withInvocationOf("expectEqual")
                            .withStringLiteralArgument("Value of field '" + mem.getMemberName() + " equals input")
                            .withArgument(memberVar)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(shapeType)
                                        .body().returningField(memStrat.structureFieldName()).of("v");
                            })
                            .inScope();
                    ib.on(chainVar);
                });
            });
        }
    }

    static class ToJsonKeysGenerator implements TestImplGenerator {

        @Override
        public <B extends TsBlockBuilderBase<T, B>, T> void generate(Shape shape, String suiteVar,
                boolean valid, String varName, B bb, TestContext testContext,
                RandomInstance<?> r, TypeStrategies strategies) {
            assert r.valid;
            assert r.shape.isStructureShape();
            Set<String> jsonKeys = new TreeSet<>();
            r.shape.asStructureShape().get().getAllMembers()
                    .forEach((name, mem) -> {
                        MemberStrategy<?> memStrat = strategies.memberStrategy(mem);
                        jsonKeys.add(memStrat.jsonName());
                    });
            if (jsonKeys.isEmpty()) {
                return;
            }
            String msg = "JSON Keys of " + strategies.strategy(shape).targetType() + " are "
                    + Strings.join(",", jsonKeys);
            String typeName;
            InvocationBuilder<InvocationBuilder<B>> inv = bb.invoke("add")
                    .withInvocationOf("expectToJsonKeys")
                    .withStringLiteralArgument(msg);
            for (String s : jsonKeys) {
                inv = inv.withStringLiteralArgument(s);
            }
            inv.inScope().on(suiteVar);
        }

    }

    static class ValueFieldGenerator implements TestImplGenerator {

        @Override
        public <B extends TsBlockBuilderBase<T, B>, T> void generate(Shape shape, String collVar, boolean valid,
                String varName, B bb, TestContext testContext, RandomInstance<?> r, TypeStrategies strategies) {
            System.out.println("RUN VALUE FIELD GENERATOR FOR " + shape);
            String typeName = strategies.strategy(shape).targetType();
            r.as(Valued.class).ifPresent(v -> {
                System.out.println("  have number");
                v.value(Number.class).ifPresent(num -> {
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value")
                            .withArgument(num)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningField("value").of("v");
                            })
                            .inScope()
                            .on(collVar);
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value when accessed as property")
                            .withArgument(num)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returning("v['value']");
                            })
                            .inScope()
                            .on(collVar);

                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("toString of instance matches toString of value")
                            .withStringLiteralArgument(num.toString())
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningInvocationOf("toString").on("v");
                            })
                            .inScope()
                            .on(collVar);
                });
                v.value(Instant.class).ifPresent(when -> {
                    System.out.println("  have instant");
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value")
                            .withArgument(when.toEpochMilli())
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningField("value").of("v.getTime()");
                            })
                            .inScope()
                            .on(collVar);
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value when accessed as property")
                            .withArgument(when.toEpochMilli())
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returning("v['value']");
                            })
                            .inScope()
                            .on(collVar);

                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("toString of instance matches toString of value")
                            .withStringLiteralArgument(when.toString())
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningInvocationOf("toString").on("v");
                            })
                            .inScope()
                            .on(collVar);
                });
                v.value(String.class).ifPresent(str -> {
                    System.out.println("  have string");
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value")
                            .withStringLiteralArgument(str)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningField("value").of("v");
                            })
                            .inScope()
                            .on(collVar);
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value when accessed as property")
                            .withStringLiteralArgument(str)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returning("v['value']");
                            })
                            .inScope()
                            .on(collVar);
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("toString of instance matches toString of value")
                            .withStringLiteralArgument(str)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningInvocationOf("toString").on("v");
                            })
                            .inScope()
                            .on(collVar);
                });
                v.value(Boolean.class).ifPresent(bool -> {
                    System.out.println("  have boolean");
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value")
                            .withArgument(bool)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningField("value").of("v");
                            })
                            .inScope()
                            .on(collVar);
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("value field is expected value when accessed as property")
                            .withArgument(bool)
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returning("v['value']");
                            })
                            .inScope()
                            .on(collVar);
                    bb.invoke("add")
                            .withInvocationOf("expectEqual")
                            .withStringLiteralArgument("toString of instance matches toString of value")
                            .withStringLiteralArgument(bool.toString())
                            .withLambda(lb -> {
                                lb.withArgument("v").ofType(typeName)
                                        .body().returningInvocationOf("toString").on("v");
                            })
                            .inScope()
                            .on(collVar);
                });
            });
        }
    }

    void withTestImplGenerators(RandomInstance<?> instance, Consumer<TestImplGenerator> c) {
        if (implementsValidatable()) {
            c.accept(new ValidateGenerator());
            if (instance.valid && instance.shape.isStructureShape()) {
                c.accept(new ToJsonKeysGenerator());
                c.accept(new MemberFieldCheckGenerator());
            }
        }
        if (hasValueField() && instance instanceof Valued) {
            c.accept(new ValueFieldGenerator());
        }
    }

    private boolean hasValueField() {
        if (isNotUserType(shape)) {
            return false;
        }
        switch (shape.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case BOOLEAN:
            case DOUBLE:
            case FLOAT:
            case BYTE:
            case INTEGER:
            case LONG:
            case SHORT:
            case STRING:
                return true;
            default:
                return false;
        }
    }

    private boolean implementsValidatable() {
        if (isNotUserType(shape)) {
            return false;
        }
        switch (shape.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case DOCUMENT:
            case DOUBLE:
            case FLOAT:
            case BYTE:
            case INTEGER:
            case LIST:
            case LONG:
            case SET:
            case STRUCTURE:
            case MAP:
            case SHORT:
            case STRING:
                return true;
            default:
                return false;
        }
    }

}
