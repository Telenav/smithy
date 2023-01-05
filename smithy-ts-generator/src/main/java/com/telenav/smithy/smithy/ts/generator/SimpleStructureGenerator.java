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
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.code.generation.common.LinesBuilder;
import com.mastfrog.function.QuadConsumer;
import com.mastfrog.function.PetaConsumer;
import com.mastfrog.function.TriFunction;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.smithy.ts.generator.type.TsVariable;
import static com.telenav.smithy.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.FunctionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.NewBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.PropertyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
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

    private final Map<String, TypeStrategy> memberStrategies = new HashMap<>();

    public SimpleStructureGenerator(StructureShape shape, Model model,
            LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
        shape.getAllMembers().forEach((name, mem) -> {
            memberStrategies.put(name, strategies.strategy(mem));
        });
    }

    protected void eachMember(QuadConsumer<String, MemberShape, Shape, TypeStrategy> c) {
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

    protected void eachMemberOptionalLast(PetaConsumer<String, MemberShape, Shape, Boolean, TypeStrategy> c) {
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
        tb.generateDebugLogCode();
        _generate(tb, c);
//        tb.disableDebugLogCode();
    }

    private void _generate(TypescriptSource tb, Consumer<TypescriptSource> c) {

        boolean isMixin = shape.getTrait(MixinTrait.class).isPresent();

        if (isMixin) {
            tb.declareInterface(typeName(), ib -> {
                ib.exported();
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
                eachMember((name, memberShape, targetShape, strategy) -> {
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
            mth.makePublic().returning(typeNameOf(target, false));
            if (memberShape == null) {
                mth.body(bb -> bb.returningThis());
            } else {
                mth.body(bb -> bb.returningField(escape(memberName)).ofThis());
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
                    String queryParam = e.getKey();
                    String fieldName = escape(e.getValue().getKey());
                    boolean required = e.getValue().getValue().getMemberTrait(model, RequiredTrait.class).isPresent();
                    TypeStrategy strat = memberStrategies.get(e.getValue().getKey());
                    assert strat != null;
                    strat.populateQueryParam(fieldName, required, bb, queryParam);
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

                    TypeStrategy<?> strategy = memberStrategies.get(e.getValue().getKey());

                    String queryParam = e.getKey();
                    String fieldName = escape(e.getValue().getKey());
                    boolean required = e.getValue().getValue().getMemberTrait(model, RequiredTrait.class).isPresent();

                    if (isNotUserType(member) && member.getType() == ShapeType.TIMESTAMP) {
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
        cb.constructor((FunctionBuilder<Void> con) -> {
            con.body((TsBlockBuilder<Void> bb) -> {
                eachMemberOptionalLast((name, memberShape, targetShape, required, strategy) -> {
                    String argName = escape(name);
                    String fieldName = argName;
                    PropertyBuilder<FunctionBuilder<Void>> tp = con.withArgument(argName);
                    String suffix = required ? "" : " | undefined";

                    boolean prim = isNotUserType(targetShape);

                    String an = argName;
                    memberShape.getMemberTrait(model, DefaultTrait.class)
                            .ifPresent(defaultTrait -> {
                                applyDefaultValue(bb, defaultTrait, an, strategy);
                            });
                    String dateField = escape(name + "AsDate");
                    if (targetShape.getType() == TIMESTAMP) {
                        suffix += " | string | number";
                        if (!required) {
                            bb.statement("let " + dateField + ": Date | undefined");
                        } else {
                            bb.statement("let " + dateField);
                        }

                        bb.lineComment("We may have an empty string which will result in an ")
                                .lineComment("invalid date, so test for that.");

                        ConditionalClauseBuilder<TsBlockBuilder<Void>> test
                                = bb.iff("typeof " + an + " === 'string' && "
                                        + an + " !== ''");

                        test.assign(dateField).assignedToNew(nb -> {
                            nb.withArgumentFromInvoking("parse")
                                    .withArgument(fieldName + " as string")
                                    .on("Date")
                                    .ofType("Date");
                        });

                        test = test.orElse("typeof " + fieldName + " === 'number'");
                        test = test.assign(dateField).assignedToNew().withArgument(
                                fieldName + " as number").ofType("Date");

                        if (required) {
                            test.orElse()
                                    .assign(dateField)
                                    .assignedToTernary("typeof " + fieldName + " === 'number'")
                                    .instantiate().withArgument(fieldName + " as number").ofType("Date")
                                    .expression(fieldName + " as Date")
                                    .endIf();
//                            test.orElse().assign(dateField).assignedTo(fieldName).endIf();
//                            test.orElse().statement(dateField + " = " + fieldName).endIf();
                        } else {
//                            test.orElse(argName)
//                                    .statement(dateField + " = " + fieldName + " as Date")
//                                    .endIf();
                            test.orElse()
                                    .assign(dateField)
                                    .assignedToTernary("typeof " + fieldName + " === 'number'")
                                    .instantiate().withArgument(fieldName + " as number").ofType("Date")
                                    .expression(fieldName + " as Date")
                                    .endIf();
                        }

                        if (!isNotUserType(targetShape)) {
                            String nue = argName + "As" + strategy.targetType();
                            bb.declare(nue).ofType(strategy.targetType())
                                    .assignedToNew().withArgument(dateField)
                                    .ofType(strategy.targetType());
                            argName = nue;
                        } else {
                            argName = dateField;
                        }
                    }
                    if (prim) {
                        tp.ofType(typeNameOf(targetShape, false) + suffix);
                    } else {
                        tp.ofType(tsTypeName(targetShape) + suffix);
                    }
                    bb.assignField(fieldName).ofThis().to(argName);
                });
            });
        });
    }

    private void applyDefaultValue(TsBlockBuilder<Void> bb, DefaultTrait defVal,
            String argName, TypeStrategy strategy) {
        bb.blankLine().lineComment("Have a default of '" + defVal.toNode() + "' with "
                + strategy.getClass().getSimpleName());

        Consumer<ConditionalClauseBuilder<Void>> c
                = iff -> {
                    strategy.applyDefault(defVal, iff.assign(argName).assignedTo());
                };
        switch (strategy.shape().getType()) {
            case BOOLEAN:
                bb.iff("typeof " + argName + " === 'undefined'", c);
                break;
            default:
                bb.ifTypeOf(argName, "undefined", c);
        }
    }

    public void generateFromJson(ClassBuilder<Void> cb) {
        cb.method("fromJson", mth -> {
            mth.makeStatic()
                    .withArgument("json").ofType("string");
            mth.returning(typeName(), bb -> {
                bb.returningInvocationOf("fromJsonObject")
                        .withArgumentFromInvoking("parse")
                        .withArgument("json")
                        .on("JSON")
                        .on(cb.name());
            });
        });

        cb.method("fromJsonObject", mth -> {
            mth.makeStatic().makePublic()
                    .withArgument("obj").ofType("any");
            mth.returning(typeName(), bb -> {
                bb.returningNew(typeName(), nb -> {
                    Set<String> requiredJsonNames = new TreeSet<>();
                    Set<String> defaultedProperties = new HashSet<>();
                    eachMemberOptionalLast((name, member, target, required, strategy) -> {
                        boolean defaulted = member.getMemberTrait(model, DefaultTrait.class).isPresent();
                        if (required) {
                            String jsonName = member.getMemberTrait(model, JsonNameTrait.class)
                                    .map(jn -> jn.getValue()).orElse(name);
                            if (!defaulted) {
                                requiredJsonNames.add(jsonName);
                            }
                        }
                        if (defaulted) {
                            defaultedProperties.add(name);
                        }
                    });
                    bb.lineComment("RequiredJsonNames: " + Strings.join(", ", requiredJsonNames));
                    bb.lineComment("DefaultedProperties: " + Strings.join(", ", defaultedProperties));
                    // Do missing property test first
                    if (!requiredJsonNames.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (String req : requiredJsonNames) {
                            if (sb.length() > 0) {
                                sb.append(" || ");
                            }
                            sb.append("typeof obj['").append(req).append("'] === 'undefined'");
                        }
                        bb.iff(sb.toString())
                                .throwing(err -> {
                                    err.withStringLiteralArgument("Object is missing some of "
                                            + "properties " + Strings.join(", ", requiredJsonNames));
                                }).endIf();
                    }
                    eachMemberOptionalLast((name, member, target, required, strategy) -> {
                        String jsonName = member.getMemberTrait(model, JsonNameTrait.class)
                                .map(jn -> jn.getValue()).orElse(name);
                        String fieldName = escape(name);

                        boolean needOptional = target.getType() == ShapeType.UNION;

                        TsVariable v = strategy.shapeType()
                                .optional(needOptional)
                                .variable("obj['" + jsonName + "']");
                        bb.blankLine().lineComment("MEMBER: " + name + " strategy "
                                + target.getType() + " "
                                + strategy.getClass().getSimpleName()
                                + " required " + required);
                        strategy.instantiateFromRawJsonObject(bb, v, fieldName, true);

                        String arg = required ? fieldName + " as " + strategy.targetType() : fieldName;
                        nb.withArgument(arg);
                    });
                });
            });
        });
    }

    private void addMemberFor(TypescriptSource tb, String key, MemberShape value, Shape target, InterfaceBuilder<Void> ib, TypeStrategy strategy) {
        ib.property(escape(key), pb -> {
            value.getMemberTrait(model, DocumentationTrait.class).ifPresent(dox -> {
                pb.docComment(dox.getValue());
            });
            boolean canBeAbsent = !value.getMemberTrait(model, DefaultTrait.class)
                    .isPresent() && !value.getMemberTrait(model, RequiredTrait.class).isPresent();
            if (canBeAbsent) {
                pb.optional();
            }
            pb.readonly();
            applyType(tb, key, value, target, canBeAbsent, strategy, pb);
        });
    }

    private void addMemberFor(TypescriptSource tb, String key, MemberShape value, Shape target, ClassBuilder<Void> ib, TypeStrategy strategy) {
        ib.property(escape(key), pb -> {
            boolean canBeAbsent = !value.getMemberTrait(model, DefaultTrait.class)
                    .isPresent() && !value.getMemberTrait(model, RequiredTrait.class).isPresent();
            value.getMemberTrait(model, DocumentationTrait.class).ifPresent(dox -> {
                pb.docComment(dox.getValue());
            });
            if (canBeAbsent) {
                pb.optional();
            }
            pb.readonly();
            pb.setPublic();
            applyType(tb, key, value, target, canBeAbsent, strategy, pb);
        });
    }

    private void applyType(TypescriptSource tb, String key, MemberShape value,
            Shape target, boolean canBeAbsent, TypeStrategy strategy,
            PropertyBuilder<Void> pb) {
        boolean isModelDefined = !"smithy.api".equals(target.getId().getNamespace());
        if (isModelDefined) {
            importShape(target, tb);
            pb.ofType(tsTypeName(target));
        } else {
            pb.ofType(typeNameOf(target, false));
        }
    }

    @Override
    protected void toJsonBody(TypescriptSource.TsBlockBuilder<Void> bb) {
        bb.declareConst("result").ofType("object").assignedTo("{}");
        eachMemberOptionalLast((name, memberShape, target, required, strategy) -> {
            String fieldName = escape(name);
            if (!required) {
                ConditionalClauseBuilder<TsBlockBuilder<Void>> test
                        = bb.ifFieldDefined(escape(name)).ofThis();
//                        = bb.iff("typeof this." + escape(name) + " !== \"undefined\"");
                generateObjectFieldAssignment(target, memberShape, test, fieldName, strategy, required);
                test.endIf();
            } else {
                generateObjectFieldAssignment(target, memberShape, bb, fieldName, strategy, required);
            }
        });
        bb.returning("result");
    }

    private String jsonName(String name, MemberShape memberShape) {
        return memberShape.getMemberTrait(model, JsonNameTrait.class)
                .map(jn -> jn.getValue()).orElse(name);
    }

    public <C, B extends TSBlockBuilderBase<C, B>> void generateObjectFieldAssignment(Shape target, MemberShape member,
            B bb, String name, TypeStrategy strategy, boolean required) {
        String jn = jsonName(name, member);
        String nm = escape("__" + jn);
        strategy.convertToRawJsonObject(bb, strategy.shapeType().optional(!required).variable("this." + name),
                nm, true);
        bb.assignLiteralRawProperty(jn).of("result").assignedTo(nm);
    }

    private boolean defaultGenerated;

    private void generateDefaultInstance(ClassBuilder<Void> cb) {
        if (defaultGenerated) {
            return;
        }
        defaultGenerated = true;
        cb.property("DEFAULT", p -> {
            p.readonly().makeStatic().setPublic();

            Map<String, DefaultTrait> defaults = defaultTraits();

            p.initializedWithNew(nb -> {
                eachMemberOptionalLast((name, member, targetShape, required, strategy) -> {
                    DefaultTrait def = defaults.get(name);
                    TypeStrategy strat = memberStrategies.get(name);
                    ExpressionBuilder<NewBuilder<Void>> ex = nb.withArgument();
                    strat.applyDefault(def, ex);
                });
                nb.ofType(cb.name());
            });

//            p.ofType(cb.name());
        });
    }
}
