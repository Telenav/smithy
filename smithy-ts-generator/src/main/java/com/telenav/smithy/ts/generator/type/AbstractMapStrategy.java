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
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractMapStrategy extends AbstractTypeStrategy<MapShape> {

    protected final Shape keyShape;
    protected final Shape valueShape;
    protected final TypeStrategy<?> keyStrategy;
    protected final TypeStrategy<?> valueStrategy;

    protected AbstractMapStrategy(MapShape shape, TypeStrategies strategies,
            Shape keyShape, Shape valueShape) {
        super(shape, strategies);
        this.keyShape = keyShape;
        this.valueShape = valueShape;
        keyStrategy = strategies.strategy(keyShape);
        valueStrategy = strategies.strategy(valueShape);
    }

    @Override
    public TsSimpleType rawVarType() {
        return OBJECT;
    }

}
