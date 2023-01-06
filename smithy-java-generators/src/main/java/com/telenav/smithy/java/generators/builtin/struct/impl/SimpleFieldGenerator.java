/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
