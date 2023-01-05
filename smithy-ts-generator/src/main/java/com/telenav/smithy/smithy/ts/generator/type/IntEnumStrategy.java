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

import static com.telenav.smithy.smithy.ts.generator.IntEnumGenerator.validationFunctionName;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
final class IntEnumStrategy extends AbstractTypeStrategy<IntEnumShape> {

    IntEnumStrategy(IntEnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar,
                    String instantiatedVar, boolean declare) {
        Assignment<B> assig = createTargetAssignment(rawVar, declare, bb, instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke(validationFunctionName(strategies.model(), shape))
                    .withArgument(rawVar.name() + " as number")
                    .inScope();
        } else {
            assig.assignedToInvocationOf(validationFunctionName(strategies.model(), shape))
                    .withArgument(rawVar.name() + " as number")
                    .inScope();
        }
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>>
            void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withUndefinedIfUndefinedOr(rawVar.typeName()).invoke(
                    validationFunctionName(strategies.model(), shape))
                    .withArgument(rawVar.name() + " as number")
                    .inScope();
        } else {
            inv.withInvocationOf(validationFunctionName(strategies.model(), shape))
                    .withArgument(rawVar.name() + " as number")
                    .inScope();
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = (declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar));
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .expression(rawVar.name() + " as number");
        } else {
            assig.assignedTo(rawVar.name() + " as number");
        }
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, TypescriptSource.ExpressionBuilder<T> ex) {
        String dv = defaultValue(def);
        return ex.element().expression(dv).of(targetType());
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.NUMBER;
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

}
