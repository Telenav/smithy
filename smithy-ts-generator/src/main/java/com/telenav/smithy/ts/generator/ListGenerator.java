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

import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.names.NumberKind;
import com.telenav.smithy.ts.generator.type.MemberStrategy;
import com.telenav.smithy.ts.generator.type.TsPrimitiveTypes;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.ANY;
import com.telenav.smithy.ts.generator.type.TsTypeUtils;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
final class ListGenerator extends AbstractTypescriptGenerator<ListShape> {

    final Shape memberTarget;
    private final boolean isSet;
    private final MemberStrategy<?> memberStrat;

    @SuppressWarnings("deprecation")
    ListGenerator(ListShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
        memberTarget = model.expectShape(shape.getMember().getTarget());
        isSet = shape.isSetShape() || shape.getTrait(UniqueItemsTrait.class).isPresent();
        memberStrat = strategies.memberStrategy(shape.getMember());
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();

        tb.declareClass(typeName(), cb -> {
            applyValidatableInterface(cb);
            importShape(memberTarget, tb);
            boolean canBePrimitive = "smithy.api".equals(memberTarget.getId().getNamespace());
            String primitiveType = typeNameOf(memberTarget, false);
            String targetTypeName = memberStrat.targetType();
            String ext = isSet ? "Set<" + targetTypeName + ">" : "Array<" + targetTypeName + ">";
            cb.extending(ext);
            cb.exported();
            boolean needStringGuard = TsTypeUtils.isNotUserType(memberTarget)
                    && memberTarget.getType() == ShapeType.STRING;

            if (isSet) {
                cb.getter("length", get -> {
                    get.returningField("length").ofField("keys").ofThis();
                });
            }

            cb.constructor(con -> {
                con.makePublic().withArgument("items").ofType(targetTypeName + "[]");
                con.body(bb -> {
                    if (!isSet) {
                        if (needStringGuard) {
                            bb.lineComment("Need special handling of the case of being ")
                                    .lineComment("called with a single string, since otherwise ")
                                    .lineComment("it will be dissected into characters.");
                            bb.invoke("super").withField("length")
                                    .of("items").inScope();
                            bb.ifTypeOf("items", "string")
                                    .invoke("push")
                                    .withArgument("items as string")
                                    .onThis()
                                    .orElse()
                                    .invoke("forEach")
                                    .withLambda()
                                    .withArgument("item").inferringType()
                                    .body(lbb -> {
                                        lbb.invoke("push")
                                                .withArgument("item")
                                                .onThis();
                                    })
                                    .onThis()
                                    .endIf();
                        } else {
                            bb.invoke("super")
                                    .withArgument("...items").inScope();
                        }
                    } else {
                        if (needStringGuard) {
                            bb.invoke("super")
                                    .withTernary("Array.isArray(items)")
                                    .expression("items")
                                    .expression("[items as string]")
                                    .inScope();

                        } else {
                            bb.invoke("super").withArgument("items").inScope();
                        }
                    }
                });
            });

            cb.method("toString", mth -> {
                mth.makePublic().returning("string");
                mth.body(bb -> {
                    bb.declareConst("strings")
                            .ofType("string[]")
                            .assignedTo("[]");

                    bb.invoke("forEach")
                            .withLambda()
                            .withArgument("item").inferringType()
                            .body(lbb -> {
                                lbb.invoke("push")
                                        .withInvocationOf("toString")
                                        .on("item")
                                        .on("strings");
                            }).onThis();

                    bb.declareConst("result")
                            .ofType("string")
                            .assignedToInvocationOf("join")
                            .withStringLiteralArgument(",")
                            .on("strings");

                    bb.invoke("log")
                            .withStringLiteralArgument("Strings are ")
                            .withArgument("strings")
                            .on("console");

                    bb.invoke("log")
                            .withArgument("result")
                            .withArgument("this")
                            .on("console");

                    bb.returningInvocationOf("join")
                            .withStringLiteralArgument(",")
                            .on("strings");
                });
            });

            cb.method(FROM_JSON, mth -> {
                mth.makeStatic()
                        .withArgument("input").ofType("any");
                mth.returning(typeName(), bb -> {

                    bb.statement("let items : " + tsTypeName(memberTarget) + "[] = []");
                    TypeStrategy<?> strat = strategies.strategy(memberTarget);

                    ConditionalClauseBuilder<TsBlockBuilder<Void>> ia = bb.iff("Array.isArray(input)");

                    ia.declare("values")
                            .ofType("any[]")
                            .assignedTo().as("any[]")
                            .expression("input");

                    ia.invoke("forEach")
                            .withLambda()
                            .withArgument("item").ofType("any")
                            .body(lbb -> {
                                strat.instantiateFromRawJsonObject(lbb,
                                        TsPrimitiveTypes.ANY.variable("item"),
                                        "converted", true, true);
                                lbb.invoke("push").withArgument("converted").on("items");
                            }).on("values");

                    ConditionalClauseBuilder<TsBlockBuilder<Void>> els = ia.orElse("typeof input === 'string'");
                    els.declare("rex").ofType("RegExp")
                            .assignedToNew().withArgument("\\s*?,\\s*")
                            .ofType("RegExp");
                    els.declare("strings").ofType("string[]")
                            .assignedToInvocationOf("split")
                            .withArgument().as("string").expression("input");
                    NumberKind nk = NumberKind.forShape(memberTarget);
                    if (nk != null) {
                        els.invoke("forEach")
                                .withLambda()
                                .withArgument("val").ofType("string")
                                .withArgument("_index").ofType("number")
                                .body(lbb -> {
                                    TsPrimitiveTypes argType = TsPrimitiveTypes.NUMBER;
                                    lbb.lineComment("Do the thing! " + NumberKind.forShape(memberTarget));
                                    lbb.declareConst("parsed")
                                            .ofType("number")
                                            .assignedToInvocationOf(nk.jsParseMethod())
                                            .withArgument("val")
                                            .inScope();
                                    strat.instantiateFromRawJsonObject(lbb, argType.variable("parsed"), "converted", true, true);
                                    lbb.invoke("push")
                                            .withArgument("converted")
                                            .on("values");
                                });
                    } else {
                        els.invoke("forEach")
                                .withLambda()
                                .withArgument("val").ofType("string")
                                .withArgument("_index").ofType("number")
                                .body(lbb -> {
                                    strat.instantiateFromRawJsonObject(lbb, ANY.variable("val"), "converted", true, true);
                                    lbb.invoke("push")
                                            .withArgument("converted")
                                            .on("values");
                                });
                    }
                    els = els.orElse("typeof input !== 'undefined'");
                    els.lineComment("Fail over - see if the conversion method can do anything with it.");
                    strat.instantiateFromRawJsonObject(els, TsPrimitiveTypes.ANY.variable(
                            "input"), "converted", true, true);
                    els.invoke("push").withArgument("converted").on("items");
                    els.endIf();
                    bb.returningNew().withArgument("items").ofType(cb.name());
                });
            });
            if (canBePrimitive && !primitiveType.equals(targetTypeName)) {
                cb.method("convert", mth -> {
                    mth.withArgument("items").ofType(typeNameOf(memberTarget, false));
                    mth.returning(targetTypeName + "[]", bb -> {
                        bb.declareConst("result")
                                .ofType(targetTypeName + "[]")
                                .assignedTo("new " + targetTypeName + "[]");
                        bb.invoke("forEach")
                                .withLambda()
                                .withArgument("item").ofType(typeNameOf(memberTarget, false))
                                .body(lbb -> {
                                    lbb.invoke("push")
                                            .withArgument("new " + targetTypeName + "(item)")
                                            .on("result");
                                })
                                .on("items");
                    });
                });

                cb.constructor(con -> {
                    con.withArgument("items").ofType(typeNameOf(memberTarget, false));
                    con.body(bb -> {
                        bb.invoke("super")
                                .withInvocationOf("convert")
                                .on(cb.name())
                                .inScope();

                    });
                });

            }
            generateToJsonString(cb);
            generateToJson(cb);
            generateAddTo(cb);
        });

        c.accept(tb);
    }

    @Override
    protected void toJsonBody(TsBlockBuilder<Void> bb) {
        Shape target = model.expectShape(shape.getMember().getTarget());
//        String primitiveType = typeNameOf(target, false);
        String primitiveType = jsTypeOf(target);
        String typeName = tsTypeName(target);

        MemberStrategy<?> st = strategies.memberStrategy(shape.getMember(), target);

        bb.blankLine()
                .lineComment("Target " + target.getId())
                .lineComment("prim type " + primitiveType)
                .lineComment("tsTypeName of target " + typeName)
                .lineComment("jsTypeName of target " + jsTypeOf(target))
                .lineComment("jsTypeName of shape " + jsTypeOf(shape));

        bb.declare("objs")
                .ofType(st.rawVarType() + "[]")
                .assignedTo("[]");
        bb.invoke("forEach")
                .withLambda(lb -> {
                    lb.withArgument("item").ofType(typeName);
                    lb.body(lbb -> {
                        st.convertToRawJsonObject(lbb, st.shapeType().variable("item"), "rawValue", true);
                        lbb.invoke("push").withArgument("rawValue").on("objs");
                    });
                })
                .on("this");
        bb.returning("objs");
    }

    @Override
    protected <T, R> void generateValidationMethodBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        strategy.validate("path", bb, "this", false);
    }
}
