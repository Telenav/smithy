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
import com.mastfrog.java.vogon.ClassBuilder.MultiAnnotatedArgumentBuilder;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Applies annotations or other modifications to a constructor argument for a
 * specific structure property under generation.
 *
 * @author Tim Boudreau
 */
public interface ConstructorArgumentAnnotator<S extends Shape> {

    void generateConstructorArgumentAnnotations(
            StructureMember<? extends S> member,
            ConstructorKind kind,
            MultiAnnotatedArgumentBuilder<?> annos,
            ClassBuilder<?> cb);

}
