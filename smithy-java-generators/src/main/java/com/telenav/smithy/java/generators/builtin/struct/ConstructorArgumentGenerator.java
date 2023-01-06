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
import com.mastfrog.java.vogon.ParameterConsumer;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Adds a constructor argument to a constructor, applying the passed set of
 * annotators.
 *
 * @author Tim Boudreau
 */
public interface ConstructorArgumentGenerator<S extends Shape> {

    /**
     * Generate a single constructor argument for the passed structure member,
     * given the constructor kind. Note that the passed builder may be a factory
     * <i>method</i> builder rather than a constructor builder - implementations
     * should work with either.
     *
     * @param <P> The type of builder
     * @param <R> The return type of the builder
     * @param member The structure member
     * @param annotators The set of annotators that have requested the ability
     * to annotate this constructor argument
     * @param cb The class builder (for adding imports, or creating fields with
     * default values, or similar)
     * @param con The builder
     * @param ck The kind of constructor being built
     */
    <P extends ParameterConsumer<R>, R> void generateConstructorArgument(
            StructureMember<? extends S> member,
            List<? extends ConstructorArgumentAnnotator<? super S>> annotators,
            ClassBuilder<?> cb,
            P con,
            ConstructorKind ck);

}
