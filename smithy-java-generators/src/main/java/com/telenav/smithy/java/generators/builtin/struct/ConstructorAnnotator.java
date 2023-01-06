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

import com.mastfrog.java.vogon.Annotatable;
import com.mastfrog.java.vogon.Imports;

/**
 * Applies one or more annotation or other modification to a constructor under
 * generation.
 *
 * @author Tim Boudreau
 */
public interface ConstructorAnnotator {

    /**
     * Add any annotations to the constructor being generated here (note the
     * passed builder *may* be a MethodBuilder for a factory method, not a
     * constructor - implementations should be usable for either).
     *
     * @param <T> The return type of the annotatable builder
     * @param <A> The type of the annotatable builder
     * @param bldr The builder
     * @param on The class builder, in case imports need to be added
     * @param kind The kind of constructor being built
     * @param helper The structure it is being built for
     */
    <T, A extends Annotatable<T, A>> void apply(A bldr, Imports<?, ?> on,
            ConstructorKind kind, StructureGenerationHelper helper);
}
