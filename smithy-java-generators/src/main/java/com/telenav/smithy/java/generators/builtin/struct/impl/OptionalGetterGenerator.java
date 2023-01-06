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
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.GetterDecorator;
import com.telenav.smithy.java.generators.builtin.struct.GetterGenerator;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.Collection;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class OptionalGetterGenerator<S extends Shape> implements GetterGenerator<S> {

    @Override
    public void generateGetter(StructureMember<? extends S> member, Collection<? extends GetterDecorator<? super S>> annotators, Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors, ClassBuilder<?> cb) {
        cb.importing(Optional.class);
        cb.method(member.getterName(), mth -> {
            DocumentationContributor.document(member, docContributors).ifPresent(mth::docComment);
            for (GetterDecorator<? super S> anno : annotators) {
                anno.onGenerateGetter(member, cb, mth);
            }
            mth.withModifier(Modifier.PUBLIC).returning("Optional<" + member.typeName() + ">").body(bb -> {
                bb.returningInvocationOf("ofNullable").withArgumentFromField(member.field()).ofThis().on("Optional");
            });
        });
    }

}
