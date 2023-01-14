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
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveMapStrategy extends AbstractMapStrategy {

    PrimitiveMapStrategy(MapShape shape, TypeStrategies strategies, Shape keyShape, Shape valueShape) {
        super(shape, strategies, keyShape, valueShape);
    }

    private String signature() {
        return "Map<" + keyStrategy.targetType() + ", " + valueStrategy.targetType() + ">";
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(signature() + " | undefined");
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name()).instantiate().ofType(signature());
            ConditionalClauseBuilder<B> defined = bb.ifDefined(rawVar.name());
            copyRawObjectProperties(defined, instantiatedVar, rawVar);
            defined.endIf();
        } else {
            assig.ofType(signature()).assignedToNew().ofType(signature());
            copyRawObjectProperties(bb, instantiatedVar, rawVar);
        }
    }

    private <T, B extends TsBlockBuilderBase<T, B>> void copyRawObjectProperties(
            B bb, String instantiatedVar, TsVariable rawVar) {
        bb.forVar("k", loop -> {
            keyStrategy.instantiateFromRawJsonObject(loop, keyStrategy
                    .rawVarType().variable("k"), "key", true, true);
            valueStrategy.instantiateFromRawJsonObject(loop, keyStrategy
                    .rawVarType().variable("k"), "value", true, true);
            loop.invoke("set").withArgument("key").withArgument("value").on(instantiatedVar);
            loop.over(rawVar.name());
        });
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>>
            void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name()).expression("{}");
            bb.ifDefined(rawVar.name(), defined -> {
                applyKeyValuePairsToRawObject(defined, instantiatedVar);
            });
        } else {
            assig.assignedTo("{}");
            applyKeyValuePairsToRawObject(bb, instantiatedVar);
        }
    }

    private <T, B extends TsBlockBuilderBase<T, B>> void applyKeyValuePairsToRawObject(
            B bb, String instantiatedVar) {
        bb.invoke("forEach")
                .withLambda()
                .withArgument("k").ofType(keyStrategy.targetType())
                .withArgument("v").ofType(valueStrategy.targetType())
                .body(lbb -> {
                    keyStrategy.convertToRawJsonObject(lbb, keyStrategy.shapeType()
                            .variable("k"), "rawKey", true);
                    valueStrategy.convertToRawJsonObject(lbb, valueStrategy.shapeType()
                            .variable("v"), "rawValue", true);
                    lbb.assignRawProperty("rawKey").of(instantiatedVar)
                            .assignedTo("rawValue");
                });
    }

    @Override
    public String targetType() {
        return "Map<" + strategies.tsTypeName(keyShape) + ", "
                + strategies.tsTypeName(valueShape) + ">";
    }

}
