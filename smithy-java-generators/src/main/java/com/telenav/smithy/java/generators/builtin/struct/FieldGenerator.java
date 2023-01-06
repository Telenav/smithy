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
import java.util.Collection;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates a field in a structure class for one member, applying any
 * decorators and documentation contributors.
 *
 * @author Tim Boudreau
 */
public interface FieldGenerator<S extends Shape> {

    /**
     * Add a field to the passed class builder, representing the passed member.
     *
     * @param <R> The class builder's parameter type
     * @param member The class member
     * @param annotators Any annotators that want to annotate the field
     * @param docContributors Any contributors that want to contribute to the
     * javadoc for the field
     * @param cb A class builder to add to
     */
    <R> void generateField(
            StructureMember<? extends S> member,
            Collection<? extends FieldDecorator<? super S>> annotators,
            Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors,
            ClassBuilder<R> cb);

}
