/*
 * Copyright 2023 Mastfrog Technologies.
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

import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public enum TypeMatchingStrategies implements TypeMatchingStrategy {
    TYPE_OF, INSTANCE_OF, ARRAY_IS_ARRAY;

    @Override
    public String toString() {
        switch (this) {
            case TYPE_OF:
                return "typeof";
            case INSTANCE_OF:
                return "instanceof";
            case ARRAY_IS_ARRAY:
                return "Array.isArray";
            default:
                throw new AssertionError(this);
        }
    }

    @Override
    public boolean isExhaustive() {
        return this == INSTANCE_OF;
    }

    public String test(String varName, String typeName, Shape shape) {
        switch (this) {
            case TYPE_OF:
                return "typeof " + varName + " === '" + typeName + "'";
            case INSTANCE_OF:
                return varName + " instanceof " + typeName;
            case ARRAY_IS_ARRAY:
                return "Array.isArray(" + varName + ")";
            default:
                throw new AssertionError(this);
        }
    }
    
}
