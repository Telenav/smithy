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
 * Generates the test used to determine if a raw object can be an instance
 * of a particular shape type.  In the case of unions and enums, this can
 * be fairly complex.
 */
public interface TypeMatchingStrategy {

    /**
     * Emits the test to use in an if to determine if an object of type
     * any can be an instance of a particular shape type.
     *
     * @param varName
     * @param typeName
     * @param shape
     * @return
     */
    String test(String varName, String typeName, Shape shape);

    /**
     * If true, then the test is exhaustive, and the result cannot be
     * some other type if it passes this strategy's test.
     *
     * @return Whether or not the test is exhaustive
     */
    default boolean isExhaustive() {
        return false;
    }
    
}
