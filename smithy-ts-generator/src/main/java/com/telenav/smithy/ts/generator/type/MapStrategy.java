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

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.OBJECT;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class MapStrategy extends AbstractMapStrategy {

    MapStrategy(MapShape shape, TypeStrategies strategies, Shape keyShape, Shape valueShape) {
        super(shape, strategies, keyShape, valueShape);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
//        new Exception(rawVar.name() + " opt " + rawVar.optional() + " for " + targetType()).printStackTrace();
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke("fromJsonObject")
                    .withArgument(rawVar.name())
                    .on(targetType());
        } else {
            assig.assignedToInvocationOf("fromJsonObject")
                    .withArgument(rawVar.name())
                    .on(targetType());
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke("toJSON")
                    .on(rawVar.name());
        } else {
            assig.assignedToInvocationOf("toJSON")
                    .on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return OBJECT;
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

}
