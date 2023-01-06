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

import static com.telenav.smithy.ts.generator.IntEnumGenerator.validationFunctionName;
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
