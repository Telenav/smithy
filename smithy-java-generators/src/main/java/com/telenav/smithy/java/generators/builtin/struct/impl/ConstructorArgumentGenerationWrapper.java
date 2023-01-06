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
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Maps a member to a generator for its corresponding constructor argument, and
 * a collection of annotators which may want to add annotations to the argument.
 *
 * @author Tim Boudreau
 */
final class ConstructorArgumentGenerationWrapper<S extends Shape> {

    private final StructureMember<S> member;
    private final ConstructorArgumentGenerator<S> generator;
    private final List<? extends ConstructorArgumentAnnotator<? super S>> annnotators;

    ConstructorArgumentGenerationWrapper(StructureMember<S> member, ConstructorArgumentGenerator<S> generator, List<? extends ConstructorArgumentAnnotator<? super S>> annnotators) {
        this.member = member;
        this.generator = generator;
        this.annnotators = annnotators;
    }

    <T> void generateArguments(ClassBuilder.ConstructorBuilder<T> con, ClassBuilder<?> cb, ConstructorKind kind) {
        generator.generateConstructorArgument(member, annnotators, cb, con, kind);
    }

}
