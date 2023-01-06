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
package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.StringConcatenationBuilder;

/**
 * Adds contents not specific to a member to the toString() method of a
 * structure.
 */
public interface HeadTailToStringContributor {

    /**
     * Contribute code to the toString() method; if you need to do some
     * complicated computation to derive the string value, it is an option to
     * generate a new private method on the passed class builder, and append the
     * invocation of that method to the StringConcatenationBuilder.
     *
     * @param helper The structure
     * @param cb The class builder being generated into (for adding imports or
     * utility members to)
     * @param concat The concatentation that will be the return value of
     * toString().
     */
    void contributeToToString(StructureGenerationHelper helper, ClassBuilder<?> cb,
            StringConcatenationBuilder<?> concat);

}
