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

import software.amazon.smithy.model.shapes.EnumShape;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractEnumStrategy extends AbstractTypeStrategy<EnumShape> {

    protected AbstractEnumStrategy(EnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public final String targetType() {
        return strategies.tsTypeName(shape);
    }

}
