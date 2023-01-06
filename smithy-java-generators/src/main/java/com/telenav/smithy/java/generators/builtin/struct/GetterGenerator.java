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
 * Generate a getter method, applying the passed annotators and documentation
 * contributors.
 *
 * @author Tim Boudreau
 */
public interface GetterGenerator<S extends Shape> {

    void generateGetter(
            StructureMember<? extends S> member,
            Collection<? extends GetterDecorator<? super S>> annotators,
            Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors,
            ClassBuilder<?> cb);

}
