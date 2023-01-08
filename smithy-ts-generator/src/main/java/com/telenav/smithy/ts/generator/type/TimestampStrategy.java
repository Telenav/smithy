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
package com.telenav.smithy.ts.generator.type;

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.ANY;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.STRING;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.NewBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
class TimestampStrategy extends AbstractTypeStrategy<TimestampShape> {

    TimestampStrategy(TimestampShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar,
                    String instantiatedVar, boolean declare) {
        boolean prim = isNotUserType(shape);
        Assignment<B> decl = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        String targetType = prim ? "Date" : targetType();
        TsVariable rvAny = rawVar.as(ANY);
        if (rawVar.optional()) {
            decl.ofType(targetType + " | undefined");
            String ternaryTest = rawVar.isAnyType() ? "typeof " + rawVar.name() + " !== 'string'" : "typeof " + rawVar.name() + " !== 'undefined'";
            ExpressionBuilder<B> rightSide = decl.assignedToTernary(ternaryTest).expression("undefined");
            rightSide.instantiate(nb -> {
                if (prim) {
                    instantiateDateFromString(nb, rvAny);
                } else {
                    nb.withNew(nb2 -> {
                        instantiateDateFromString(nb2, rvAny);
                    }).ofType(targetType());
                }
            });
        } else {
            decl.ofType(targetType);
            if (prim) {
                decl.assignedToNew(nb -> {
                    instantiateDateFromString(nb, rvAny);
                });
            } else {
                decl.assignedToNew(nb -> {
                    nb.withNew(nb2 -> {
                        instantiateDateFromString(nb2, rvAny);
                    }).ofType(targetType);
                });
            }
        }
    }

    private <T> void instantiateDateFromString(NewBuilder<Void> nb2, TsVariable rawVar) {
        String arg;
        if (rawVar.isAnyType()) {
            arg = "(" + rawVar.name() + " as any) as string";
        } else {
            arg = rawVar.name();
        }
        if (rawVar.optional()) {
            nb2.withTernary(rawVar.name() + " === ''")
                    .expression("undefined")
                    .invoke("parse").withArgument(arg).on("Date").ofType("Date");
        } else {
            nb2.withInvocationOf("parse").withArgument(arg).on("Date").ofType("Date");
        }
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        boolean prim = isNotUserType(shape);
        if (rawVar.optional()) {
            ExpressionBuilder<B> rightSide;
            if (rawVar.isAnyType()) {
                rightSide = inv.withUndefinedIfUndefinedOr(rawVar.name());
            } else {
                rightSide = inv.typeNot(rawVar.name(), "string").expression("undefined");
            }
            if (prim) {
                rightSide.instantiate(nb -> {
                    instantiateDateFromString(nb, rawVar);
                });
            } else {
                rightSide.instantiate(nb -> {
                    nb.withNew(nb2 -> {
                        instantiateDateFromString(nb2, rawVar);
                    }).ofType(targetType());
                });
            }
        } else {
            inv.withNew(nb -> {
                if (prim) {
                    instantiateDateFromString(nb, rawVar);
                } else {
                    nb.withNew(nb2 -> {
                        instantiateDateFromString(nb2, rawVar);
                    }).ofType(targetType());
                }
            });
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        if (rawVar.optional()) {
            bb.statement("let " + instantiatedVar + " : string | undefined");
            bb.trying(tri -> {
                tri.assign(instantiatedVar)
                        .assignedToTernary("typeof " + rawVar.name() + "!== 'undefined'"
                                + " && !isNaN(" + rawVar.name() + ".getTime())")
                        .invoke("toISOString").on(rawVar.name()).expression("undefined");
                tri.catching("err").lineComment("Can be an empty string - ignore").endBlock();
            });
        } else {
            (declare
                    ? bb.declareConst(instantiatedVar)
                    : bb.assign(instantiatedVar))
                    .ofType("string")
                    .assignedToInvocationOf("toISOString")
                    .on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return STRING;
    }

    @Override
    public String targetType() {
        boolean prim = isNotUserType(shape);
        if (prim) {
            return "Date";
        } else {
            return strategies.tsTypeName(shape);
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {

        bb.trying(tri -> {
            if (!required) {
                tri.ifFieldDefined(fieldName).ofThis()
                        .assignLiteralRawProperty(queryParam).of("obj")
                        .assignedToInvocationOf("toISOString")
                        .onField(fieldName)
                        .ofThis()
                        .endIf();
            } else {
                tri.assignLiteralRawProperty(queryParam)
                        .of("obj")
                        .assignedToInvocationOf("toISOString")
                        .onField(fieldName)
                        .ofThis();
            }
            tri.catching("err").lineComment("Can simply be an empty string - ignore.").endBlock();
        });
    }

    @Override
    public <A> A populateHttpHeader(Assignment<A> assig, String fieldName) {
        return assig.assignedToSelfExecutingFunction(f -> {
            f.iff("!isNaN(this." + fieldName + ".getTime())")
                    .returningInvocationOf("toUTCString")
                    .onField(fieldName).ofThis();
        });
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex) {
        Node n = def.toNode();
        switch (n.getType()) {
            case NUMBER:
                return ex.instantiate().withArgument(n.expectNumberNode().getValue().longValue())
                        .ofType("Date");
            case STRING:
                return ex.instantiate().withInvocationOf("parse")
                        .withStringLiteralArgument(def.toNode().expectStringNode().getValue())
                        .on("Date")
                        .ofType("Date");
            default:
                throw new ExpectationNotMetException("Cannot default a timestamp from a " + n.getType(), def);
        }
    }
}
