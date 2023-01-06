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
import com.mastfrog.java.vogon.ClassBuilder.MultiAnnotatedArgumentBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ParameterNameBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeNameBuilder;
import com.mastfrog.java.vogon.ParameterConsumer;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultConstructorArgumentGenerator<S extends Shape> implements ConstructorArgumentGenerator<S> {

    @Override
    public <P extends ParameterConsumer<R>, R> void generateConstructorArgument(StructureMember<? extends S> member, List<? extends ConstructorArgumentAnnotator<? super S>> annotators, ClassBuilder<?> cb, P con, ConstructorKind ck) {
        if (annotators.isEmpty()) {
            con.addArgument(member.convenienceConstructorTypeName(), member.arg());
        } else {
            MultiAnnotatedArgumentBuilder<ParameterNameBuilder<TypeNameBuilder<R>>> multi = con.addMultiAnnotatedArgument();
            annotators.forEach(anno -> {
                anno.generateConstructorArgumentAnnotations(member, ck, multi, cb);
            });
            multi.closeAnnotations().named(member.arg()).ofType(member.constructorArgumentTypeName());
        }
    }
}
