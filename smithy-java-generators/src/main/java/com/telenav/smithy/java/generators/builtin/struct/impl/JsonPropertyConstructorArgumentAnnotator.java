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

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class JsonPropertyConstructorArgumentAnnotator<S extends Shape> implements ConstructorArgumentAnnotator<S> {

    @Override
    public void generateConstructorArgumentAnnotations(StructureMember<? extends S> member, ConstructorKind kind, ClassBuilder.MultiAnnotatedArgumentBuilder<?> annos, ClassBuilder<?> cb) {
        cb.importing("com.fasterxml.jackson.annotation.JsonProperty");
        annos.annotatedWith("JsonProperty", ab -> {
            ab.addArgument("value", member.jsonName());
            if (!member.isRequired()) {
                ab.addArgument("required", false);
            }
        });
    }

}
