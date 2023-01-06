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

/**
 * A thing that contributes in some way to a structure class, at the
 * class method (adding fields, constructors, methods, comments).
 *
 * @author Tim Boudreau
 */
public interface StructureContributor {

    /**
     * Perform whatever modifications to the class builder this instants
     * wants to perform.
     *
     * @param <T> The type the class builder is parameterized on (String
     * for a top-level class, the parent class if decorating an inner class).
     * @param helper The structure in question
     * @param cb A class builder
     */
    <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb);
}
