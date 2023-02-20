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
package com.telenav.smithy.vertx.periodic.metrics;

import com.mastfrog.settings.Settings;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultOperationWeights implements OperationWeights {

    private final Settings settings;

    @Inject
    DefaultOperationWeights(Settings settings) {
        this.settings = settings;
    }

    @Override
    public double operationWeight(Enum<?> op) {
        if (op == null) {
            return 1;
        }
        Double result = getFromSettings(op);
        if (result == null) {
            return 1D / op.getDeclaringClass().getEnumConstants().length;
        }
        return result;
    }

    private Double getFromSettings(Enum<?> op) {
        Double result = settings.getDouble(op.name() + ".weight");
        if (result == null) {
            return settings.getDouble(loggingNameOf(op) + ".weight");
        }
        return result;
    }

    private static String loggingNameOf(Enum<?> op) {
        return op.name().toLowerCase().replace('_', '-');
    }
}
