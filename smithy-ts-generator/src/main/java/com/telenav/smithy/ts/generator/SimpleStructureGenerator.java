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
package com.telenav.smithy.ts.generator;

import com.mastfrog.function.QuadConsumer;
import com.mastfrog.function.PetaConsumer;
import com.mastfrog.function.TriFunction;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.ts.generator.type.MemberStrategy;
import com.telenav.smithy.ts.generator.type.TsPrimitiveTypes;
import com.telenav.smithy.ts.generator.type.TsVariable;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.generator.type.TypeStrategies;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConstructorBodyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConstructorBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.NewBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import com.telenav.smithy.utils.ConstraintsChecker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.UNION;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 *
 * @author Tim Boudreau
 */
public class SimpleStructureGenerator extends AbstractTypescriptGenerator<StructureShape> {

    private final Map<String, MemberStrategy> memberStrategies = new HashMap<>();

    public SimpleStructureGenerator(StructureShape shape, Model model,
            LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
        shape.getAllMembers().forEach((name, mem) -> {
            memberStrategies.put(name, strategies.memberStrategy(mem));
        });
    }

    protected void eachMember(QuadConsumer<String, MemberShape, Shape, MemberStrategy<?>> c) {
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            Shape tgt = model.expectShape(e.getValue().getTarget());
            c.accept(e.getKey(), e.getValue(), tgt, memberStrategies.get(e.getKey()));
        }
    }

    private Map<String, DefaultTrait> defaultTraits() {
        Map<String, DefaultTrait> result = new HashMap<>();
        shape.getAllMembers().forEach((name, member) -> {
            member.getMemberTrait(model, DefaultTrait.class).ifPresent(dt -> result.put(name, dt));
        });
        return result;
    }

    private boolean allMembersHaveDefaultTraits() {
        return shape.getAllMembers().size() == defaultTraits().size();
    }

    protected void eachMemberOptionalLast(PetaConsumer<String, MemberShape, Shape, Boolean, MemberStrategy<?>> c) {
        // Typescript needs optional arguments last, like varargs
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
        for (Map.Entry<String, MemberShape> e : sorted) {
            Shape target = model.expectShape(e.getValue().getTarget());
            c.accept(e.getKey(), e.getValue(), target,
                    e.getValue().getTrait(RequiredTrait.class).isPresent(),
                    memberStrategies.get(e.getKey()));
        }
    }

    private String implTypeName() {
        return typeName() + "Impl";
    }

    private Map<String, Map.Entry<String, MemberShape>> httpQueryItems() {
        return httpQueryItems(model, shape);
    }

    static Map<String, Map.Entry<String, MemberShape>> httpQueryItems(Model model, StructureShape shape) {
        Map<String, Map.Entry<String, MemberShape>> result = new HashMap<>();
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            e.getValue().getTrait(HttpQueryTrait.class)
                    .ifPresent(q -> {
                        result.put(q.getValue(), e);
                    });
        }
        return result;
    }

    private Map<String, Map.Entry<String, MemberShape>> httpHeaderItems() {
        return httpHeaderItems(model, shape);
    }

    static Map<String, Map.Entry<String, MemberShape>> httpHeaderItems(Model model, StructureShape shape) {
        Map<String, Map.Entry<String, MemberShape>> result = new HashMap<>();
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            e.getValue().getTrait(HttpHeaderTrait.class)
                    .ifPresent(q -> {
                        result.put(q.getValue(), e);
                    });
        }
        return result;
    }

    private <T> Optional<T> withHttpPayloadItem(TriFunction<String, MemberShape, Shape, T> sh) {
        return withHttpPayloadItem(model, shape, sh);
    }

    private static final List<Class<? extends Trait>> HTTP_TRAITS
            = Arrays.asList(HttpQueryTrait.class, HttpPayloadTrait.class, HttpLabelTrait.class,
                    HttpHeaderTrait.class);

    static <T> Optional<T> withHttpPayloadItem(Model model, StructureShape shape,
            TriFunction<String, MemberShape, Shape, T> sh) {
        boolean httpTraitsSeen = false;
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            if (e.getValue().getMemberTrait(model, HttpPayloadTrait.class).isPresent()) {
                return Optional.ofNullable(sh.apply(e.getKey(), e.getValue(),
                        model.expectShape(e.getValue().getTarget())));
            }
            for (Class<? extends Trait> c : HTTP_TRAITS) {
                httpTraitsSeen |= e.getValue().getMemberTrait(model, c).isPresent();
            }
        }
        if (!httpTraitsSeen) {
            return Optional.ofNullable(sh.apply(null, null, shape));
        }
        return Optional.empty();
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();
        _generate(tb, c);
    }

    private void _generate(TypescriptSource tb, Consumer<TypescriptSource> c) {

        boolean isMixin = shape.getTrait(MixinTrait.class).isPresent();

        if (isMixin) {
            tb.declareInterface(typeName(), ib -> {
                ib.exported();
                applyValidatableInterface(ib);
                eachMember((name, memberShape, targetShape, strategy) -> {
                    addMemberFor(tb, name, memberShape, targetShape, ib, strategy);
                });

                shape.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                    ib.docComment(dox.getValue());
                });

                shape.getMixins().forEach(mixin -> {
                    Shape mixinShape = model.expectShape(mixin);
                    importShape(mixinShape, tb);
                    ib.extending(super.typeNameOf(mixinShape, false));
                });
            });
        } else {
            tb.declareClass(typeName(), cb -> {
                cb.exported();
                applyValidatableInterface(cb);
                generateValidationConstants(cb);
                eachMember((name, memberShape, targetShape, strategy) -> {
                    ConstraintsChecker.check(model, memberShape);
                    addMemberFor(tb, name, memberShape, targetShape, cb, strategy);
                });
                shape.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                    cb.docComment(dox.getValue());
                });
                shape.getMixins().forEach(mixinId -> {
                    cb.implementing(tsTypeName(model.expectShape(mixinId)));
                });
                generateConstructor(cb);
                generateToJson(cb);
                generateAddTo(cb);
                generateToJsonString(cb);
                cb.method("toString").makePublic()
                        .returning("string", bb -> {
                            bb.returningInvocationOf("toJsonString").onThis();
                        });
                generateFromJson(cb);

                Map<String, Map.Entry<String, MemberShape>> queryItems = httpQueryItems();
                if (!queryItems.isEmpty()) {
                    generateHttpQueryPopulatorMethod(queryItems, cb);
                }
                Map<String, Map.Entry<String, MemberShape>> headerItems = httpHeaderItems();
                if (!headerItems.isEmpty()) {
                    generateHttpHeaderPopulatorMethod(headerItems, cb);
                }
                withHttpPayloadItem((memberName, memberShape, target) -> {
                    generateHttpPayloadMethod(memberName, memberShape, target, cb);
                    return true;
                });
                if (allMembersHaveDefaultTraits()) {
                    generateDefaultInstance(cb);
                }
            });
        }
        c.accept(tb);
    }

    public void generateHttpPayloadMethod(String memberName, MemberShape memberShape,
            Shape target, ClassBuilder<Void> cb) {
        cb.method("httpPayload", mth -> {
//            mth.makePublic().returning(typeNameOf(target, false));
            mth.makePublic().returning("any");
            if (memberShape == null) {
                mth.body(bb -> bb.returningInvocationOf("toJSON").onThis());
            } else {
                MemberStrategy<?> strat = memberStrategies.get(memberName);
                assert strat != null : "No strategy for " + memberName;
                mth.body(bb -> {
                    TsVariable v = TsPrimitiveTypes.ANY.variable("this." + strat.structureFieldName());
                    if (!strat.required()) {
                        v = v.asOptional();
                    }
                    strat.convertToRawJsonObject(bb, v, "result", true);
                    bb.returning("result");
                });
            }
        });
    }

    public void generateHttpQueryPopulatorMethod(
            Map<String, Map.Entry<String, MemberShape>> queryItems,
            ClassBuilder<Void> cb) {
        cb.method("populateHttpQuery", mth -> {
            mth.makePublic().withArgument("obj").ofType("object");
            mth.body(bb -> {
                for (Map.Entry<String, Map.Entry<String, MemberShape>> e : queryItems.entrySet()) {
                    MemberStrategy<?> strat = memberStrategies.get(e.getValue().getKey());
                    assert strat != null;
                    String queryParam = e.getKey();
                    strat.populateQueryParam(strat.structureFieldName(), strat.required(), bb, queryParam);
                }
            });
        });
    }

    public void generateHttpHeaderPopulatorMethod(Map<String, Map.Entry<String, MemberShape>> queryItems, ClassBuilder<Void> cb) {
        cb.method("populateHttpHeaders", mth -> {
            mth.makePublic().withArgument("obj").ofType("object");
            mth.body(bb -> {
                for (Map.Entry<String, Map.Entry<String, MemberShape>> e : queryItems.entrySet()) {
                    Shape member = model.expectShape(e.getValue().getValue().getTarget());

                    MemberStrategy<?> strategy = memberStrategies.get(e.getValue().getKey());

                    String queryParam = e.getKey();
                    String fieldName = strategy.structureFieldName();
                    boolean required = e.getValue().getValue().getMemberTrait(model, RequiredTrait.class).isPresent();

                    if (TypeStrategies.isNotUserType(member) && member.getType() == ShapeType.TIMESTAMP) {
                        // Need to refactor populateHttpHeader not to take an assignment,
                        // since we can wind up with the string "undefined" if we have to
                        // assign to *something*
                        ConditionalClauseBuilder<TsBlockBuilder<Void>> testit = bb.iff((required ? "" : "typeof this."
                                + fieldName + "!== 'undefined' && ") + "!isNaN(this." + fieldName + ".getTime())");

                        Assignment<ConditionalClauseBuilder<TsBlockBuilder<Void>>> assig
                                = testit.assignLiteralRawProperty(queryParam).of("obj");

                        strategy.populateHttpHeader(assig, fieldName);
                        testit.endIf();
                    } else {
                        if (!required) {
                            Assignment<ConditionalClauseBuilder<TsBlockBuilder<Void>>> assig
                                    = bb.ifFieldDefined(fieldName).ofThis()
                                            .assignLiteralRawProperty(queryParam)
                                            .of("obj");

                            strategy.populateHttpHeader(assig, fieldName).endIf();
                        } else {
                            Assignment<TsBlockBuilder<Void>> assig
                                    = bb.assignLiteralRawProperty(queryParam).of("obj");

                            strategy.populateHttpHeader(assig, fieldName);
                        }
                    }
                }
            });
        });
    }

    public void generateConstructor(ClassBuilder<Void> cb) {
        cb.constructor((ConstructorBuilder<Void> con) -> {
            con.makePublic().body((ConstructorBodyBuilder<Void> bb) -> {
                eachMemberOptionalLast((name, memberShape, targetShape, required, strategy) -> {
                    strategy.addConstructorArgument(con);
                    strategy.generateConstructorFieldAssignment(bb);
                });
            });
        });
    }

    public void generateFromJson(ClassBuilder<Void> cb) {
        cb.method("fromJson", mth -> {
            mth.makeStatic()
                    .withArgument("json").ofType("string");
            mth.returning(typeName(), bb -> {
                bb.returningInvocationOf(FROM_JSON)
                        .withInvocationOf("parse")
                        .withArgument("json")
                        .on("JSON")
                        .on(cb.name());
            });
        });

        cb.method(FROM_JSON, mth -> {
            mth.makeStatic().makePublic()
                    .withArgument("obj").ofType("any");
            mth.returning(typeName(), bb -> {
                bb.returningNew(typeName(), nb -> {
                    Map<String, MemberStrategy<?>> requiredJsonNames = new TreeMap<>();
                    eachMemberOptionalLast((name, member, target, required, strategy) -> {
                        if (required) {
                            requiredJsonNames.put(strategy.jsonName(), strategy);
                        }
                    });
                    generateJsonAbsentRequiredPropertiesCheck(requiredJsonNames, bb, cb);
                    eachMemberOptionalLast((name, member, target, required, strategy) -> {
                        String jsonName = member.getMemberTrait(model, JsonNameTrait.class)
                                .map(jn -> jn.getValue()).orElse(name);
                        String fieldName = strategy.structureFieldName();

                        // PENDING: Union is a special case because of the return value
                        // of the differentiator method, which contains `| undefined`.
                        boolean needOptional = target.getType() == UNION;
                        TsVariable v = strategy.shapeType()
                                .variable("obj['" + jsonName + "']")
                                .optional(needOptional || !strategy.required());

                        strategy.instantiateFromRawJsonObject(bb, v, fieldName, true, true);

                        String arg = required ? fieldName + " as "
                                + strategy.targetType() : fieldName;
                        nb.withArgument(arg);
                    });
                });
            });
        });
    }

    private void generateJsonAbsentRequiredPropertiesCheck(Map<String, MemberStrategy<?>> requiredJsonNames, TsBlockBuilder<Void> bb, ClassBuilder<Void> cb) {
        ConditionalClauseBuilder<TsBlockBuilder<Void>> test = null;
        // Do missing property test first
        if (!requiredJsonNames.isEmpty()) {
            bb.lineComment("Ensure that required properties are at least present.");
        }
        for (Map.Entry<String, MemberStrategy<?>> e : requiredJsonNames.entrySet()) {
            if (test == null) {
                test = bb.iff("typeof obj['" + e.getValue().jsonName() + "'] === 'undefined'");
            } else {
                test = test.orElse("typeof obj['" + e.getValue().jsonName() + "'] === 'undefined'");
            }
            test.throwing(err -> {
                // Generated a meaningful, informative error message:
                String jn = e.getValue().jsonName();
                if (!jn.equals(e.getValue().member().getMemberName())) {
                    jn = "'" + jn + "' (for member " + e.getValue().member().getMemberName() + ")";
                } else {
                    jn = "'" + jn + "'";
                }
                err.withStringConcatenation().append("Property " + jn + " of "
                        + cb.name() + " absent in ")
                        .appendInvocation("stringify").withArgument("obj")
                        .on("JSON").endConcatenation();
            });
        }
        if (test != null) {
            test.endIf();
        }
    }

    private <S extends Shape, B> void addMemberFor(TypescriptSource tb, String key, MemberShape value, S target, InterfaceBuilder<B> ib, MemberStrategy<?> strategy) {
        MemberStrategy<S> mem = strategies.memberStrategy(value, target);
        mem.generateField(ib);
        if (!isNotUserType(mem.shape())) {
            importShape(target, tb);
        }
    }

    private <S extends Shape, B> void addMemberFor(TypescriptSource tb, String key, MemberShape value, S target, ClassBuilder<B> ib, MemberStrategy<?> strategy) {
        MemberStrategy<S> mem = strategies.memberStrategy(value, target);
        mem.generateField(ib);
        if (!isNotUserType(mem.shape())) {
            importShape(target, tb);
        }
    }

    @Override
    protected void toJsonBody(TypescriptSource.TsBlockBuilder<Void> bb) {
        bb.declareConst("result").ofType("object").assignedTo("{}");
        eachMemberOptionalLast((name, memberShape, target, required, strategy) -> {
            String fieldName = strategy.structureFieldName();
            if (!required) {
                ConditionalClauseBuilder<TsBlockBuilder<Void>> test;

                if (strategy.valuesCanEvaluateToFalse()) {
                    test = bb.iff("typeof this." + strategy.structureFieldName() + " !== 'undefined'");
                } else {
                    test = bb.ifFieldDefined(strategy.structureFieldName()).ofThis();
                }
                generateObjectFieldAssignment(target, memberShape, test,
                        fieldName, strategy, required);
                test.endIf();
            } else {
                generateObjectFieldAssignment(target, memberShape, bb,
                        fieldName, strategy, required);
            }
        });
        bb.returning("result");
    }

    private String jsonName(String name, MemberShape memberShape) {
        return memberShape.getMemberTrait(model, JsonNameTrait.class)
                .map(jn -> jn.getValue()).orElse(name);
    }

    public <C, B extends TsBlockBuilderBase<C, B>> void generateObjectFieldAssignment(Shape target, MemberShape member,
            B bb, String name, MemberStrategy<?> strategy, boolean required) {
        String jn = jsonName(member.getMemberName(), member);
        String nm = strategy.structureFieldName() + "Loc";
        strategy.convertToRawJsonObject(bb,
                strategy.shapeType().optional(!required).variable("this." + name),
                nm, true);
        bb.assignLiteralRawProperty(jn).of("result").assignedTo(nm);
    }

    private void generateDefaultInstance(ClassBuilder<Void> cb) {
        cb.property("DEFAULT", p -> {
            p.readonly().setStatic().setPublic();

            Map<String, DefaultTrait> defaults = defaultTraits();

            p.initializedWithNew(nb -> {
                eachMemberOptionalLast((name, member, targetShape, required, strategy) -> {
                    DefaultTrait def = defaults.get(name);
                    TypeStrategy<?> strat = memberStrategies.get(name);
                    ExpressionBuilder<NewBuilder<Void>> ex = nb.withArgument();
                    strat.applyDefault(def, ex);
                });
                nb.ofType(cb.name());
            });
//            p.ofType(cb.name());
        });
    }

    @Override
    protected <T, R> void generateValidationMethodBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        strategy.validate("path", bb, "this", false);
    }

    private void generateValidationConstants(ClassBuilder<Void> cb) {
        memberStrategies.values().forEach(strat -> {
            strat.declareValidationConstants(cb);
        });
    }
}
