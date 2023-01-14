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

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveStrategy<S extends Shape> extends AbstractTypeStrategy<S> {

    PrimitiveStrategy(S shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb,
                                                                                     TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(targetType() + " | undefined").assignedTo(rawVar.name());
        } else {
            assig.ofType(targetType()).assignedTo(rawVar.name());
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
