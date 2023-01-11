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

import com.telenav.smithy.names.NumberKind;
import static com.telenav.smithy.names.NumberKind.forShape;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveNumberStrategy<S extends NumberShape> extends AbstractTypeStrategy<S> {

    PrimitiveNumberStrategy(S shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb,
            TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(targetType() + " | undefined");
        } else {
            assig.ofType(targetType());
        }
        ExpressionBuilder<B> exp = assig.assignedTo();
        if (rawVar.optional()) {
            applyRawVarExpression(exp.ternary("typeof " + rawVar.name() + " !== 'undefined'"), rawVar)
                    .expression("undefined");
        } else {
            applyRawVarExpression(exp, rawVar);
        }
//        assig.assignedTo(rawVar.name());
    }

    private <B> B applyRawVarExpression(ExpressionBuilder<B> exp, TsVariable rawVar) {
        NumberKind nk = forShape(shape);
        String parseMethod;
        if (nk == null && shape.getType() == ShapeType.BIG_DECIMAL) {
            parseMethod = "parseFloat";
        } else if (nk == null && shape.getType() == ShapeType.BIG_INTEGER) {
            parseMethod = "parseInt";
        } else {
            parseMethod = nk.jsParseMethod();
        }
        exp = exp.ternary("typeof " + rawVar.name() + " === 'string'")
                .invoke(parseMethod)
                .withArgument(rawVar.name()).inScope()
                .ternary("typeof " + rawVar.name() + " === 'number'")
                .expression(rawVar.name());
        return exp.invoke(parseMethod)
                .withInvocationOf("toString").on(rawVar.name()).inScope();
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void
            instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            applyRawVarExpression(inv.withArgument()
                    .ternary("typeof " + rawVar.name() + " === 'undefined'")
                    .expression("undefined"), rawVar);
        } else {
            applyRawVarExpression(inv.withArgument(), rawVar);
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb,
            TsVariable rawVar, String instantiatedVar, boolean declare) {
        (declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar))
                .ofType(rawVar.optional() ? rawVarType().asOptional().returnTypeSignature() : rawVarType().typeName()).assignedTo(rawVar.name());
    }

    @Override
    public TsSimpleType rawVarType() {
        return bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return rawVarType().typeName();
    }

}
