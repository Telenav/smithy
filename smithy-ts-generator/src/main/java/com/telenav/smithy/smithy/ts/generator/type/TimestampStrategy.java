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
package com.telenav.smithy.smithy.ts.generator.type;

import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.NewBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
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
    public <T, B extends TSBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar,
                    String instantiatedVar, boolean declare) {
        boolean prim = TypeStrategies.isNotUserType(shape);
        Assignment<B> decl = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        String targetType = prim ? "Date" : targetType();
        TsVariable rvAny = rawVar.as(TsPrimitiveTypes.ANY);
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
            nb2.withArgumentFromInvoking("parse").withArgument(arg).on("Date").ofType("Date");
        }
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        boolean prim = TypeStrategies.isNotUserType(shape);
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
    public <T, B extends TSBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        if (rawVar.optional()) {
            (declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar)).ofType("string | undefined").assignedToTernary("typeof " + rawVar.name() + "!== 'undefined'").invoke("toISOString").on(rawVar.name()).expression("undefined");
        } else {
            (declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar)).ofType("string").assignedToInvocationOf("toISOString").on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.STRING;
    }

    @Override
    public String targetType() {
        boolean prim = TypeStrategies.isNotUserType(shape);
        if (prim) {
            return "Date";
        } else {
            return strategies.tsTypeName(shape);
        }
    }

    @Override
    public <T, B extends TSBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
        if (!required) {
            bb.ifFieldDefined(fieldName).ofThis()
                    .assignLiteralRawProperty(queryParam).of("obj")
                    .assignedToInvocationOf("toISOString")
                    .onField(fieldName)
                    .ofThis()
                    .endIf();
        } else {
            bb.assignLiteralRawProperty(queryParam)
                    .of("obj")
                    .assignedToInvocationOf("toISOString")
                    .onField(fieldName)
                    .ofThis();
        }
    }

    @Override
    public <A> A populateHttpHeader(Assignment<A> assig, String fieldName) {
        return assig.assignedToInvocationOf("toUTCString")
                .onField(fieldName).ofThis();
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex) {
        Node n = def.toNode();
        switch (n.getType()) {
            case NUMBER:
                return ex.instantiate().withArgument(n.expectNumberNode().getValue().longValue())
                        .ofType("Date");
            case STRING:
                return ex.instantiate().withArgumentFromInvoking("parse")
                        .withStringLiteralArgument(def.toNode().expectStringNode().getValue())
                        .on("Date")
                        .ofType("Date");
            default:
                throw new ExpectationNotMetException("Cannot default a timestamp from a " + n.getType(), def);
        }
    }

}
