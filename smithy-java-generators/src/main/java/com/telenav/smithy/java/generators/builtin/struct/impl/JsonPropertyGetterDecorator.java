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
import com.telenav.smithy.java.generators.builtin.struct.GetterDecorator;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class JsonPropertyGetterDecorator<S extends Shape> implements GetterDecorator<S> {

    @Override
    public <R> void onGenerateGetter(StructureMember<? extends S> member, ClassBuilder<?> cb, ClassBuilder.MethodBuilder<R> mth) {
        cb.importing("com.fasterxml.jackson.annotation.JsonProperty");
        mth.annotatedWith("JsonProperty").addArgument("value", member.jsonName()).closeAnnotation();
    }

}
