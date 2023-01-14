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

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.NUMBER;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
class IntKeyedEnumStrategy extends AbstractEnumStrategy {

    IntKeyedEnumStrategy(EnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public TsSimpleType rawVarType() {
        return NUMBER;
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex) {
        return ex.element().literal(defaultValue(def)).of(targetType());
    }
}
