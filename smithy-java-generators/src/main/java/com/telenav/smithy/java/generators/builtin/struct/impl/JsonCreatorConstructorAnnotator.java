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
package com.telenav.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.Annotatable;
import com.mastfrog.java.vogon.Imports;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;

/**
 *
 * @author Tim Boudreau
 */
final class JsonCreatorConstructorAnnotator implements ConstructorAnnotator {

    @Override
    public <T, A extends Annotatable<T, A>> void apply(A bldr, Imports<?, ?> on, ConstructorKind kind, StructureGenerationHelper helper) {
        on.importing("com.fasterxml.jackson.annotation.JsonCreator");
        bldr.annotatedWith("JsonCreator").closeAnnotation();
    }

}
