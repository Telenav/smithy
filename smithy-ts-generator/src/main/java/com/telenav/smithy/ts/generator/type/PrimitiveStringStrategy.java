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

import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.StringShape;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveStringStrategy extends AbstractTypeStrategy<StringShape> {

    PrimitiveStringStrategy(StringShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb,
                                                                                     TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(targetType() + " | undefined")
                    .assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke("toString").on(rawVar.name());
        } else {
            assig.ofType(targetType()).assignedToInvocationOf("toString").on(rawVar.name());
        }
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void
            instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withUndefinedIfUndefinedOr(rawVar.name()).invoke("toString").on(rawVar.name());
        } else {
            inv.withInvocationOf("toString").on(rawVar.name());
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb,
                                                                               TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        assig.ofType(rawVar.optional() ? rawVarType().asOptional().returnTypeSignature() : rawVarType().typeName());
        assig.assignedTo(rawVar.name() + " as string");
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return rawVarType().typeName();
    }

}
