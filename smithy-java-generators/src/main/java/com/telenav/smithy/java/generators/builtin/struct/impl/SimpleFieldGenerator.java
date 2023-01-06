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
import com.telenav.smithy.java.generators.builtin.struct.FieldDecorator;
import com.telenav.smithy.java.generators.builtin.struct.FieldGenerator;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleFieldGenerator<S extends Shape> implements FieldGenerator<S> {

    @Override
    public <R> void generateField(StructureMember<? extends S> member, Collection<? extends FieldDecorator<? super S>> annotators, Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors, ClassBuilder<R> cb) {
        if (!member.isModelDefinedType()) {
            switch (member.target().getType()) {
                case BIG_INTEGER:
                    cb.importing(BigInteger.class);
                    break;
                case BIG_DECIMAL:
                    cb.importing(BigDecimal.class);
                    break;
                case MAP:
                    cb.importing(Map.class);
                    break;
                case LIST:
                    if (member.member().getTrait(UniqueItemsTrait.class).isPresent()) {
                        cb.importing(Set.class);
                    } else {
                        cb.importing(List.class);
                    }
                    break;
                case SET:
                    cb.importing(Set.class);
                    break;
            }
        }
        cb.field(member.field(), fld -> {
            fld.withModifier(Modifier.PRIVATE, Modifier.FINAL);
            annotators.forEach(anno -> {
                anno.onGenerateField(member, cb, fld);
            });
            DocumentationContributor.document(member, docContributors).ifPresent(fld::docComment);
            fld.ofType(member.typeName());
        });
    }

}
