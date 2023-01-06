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
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class FieldCreator<S extends Shape> implements StructureContributor {

    private final FieldGenerator<S> gen;
    private final List<? extends FieldDecorator<? super S>> decorators;
    private final List<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docs;
    private final StructureMember<S> member;

    FieldCreator(StructureMember<S> member, FieldGenerator<S> gen, List<? extends FieldDecorator<? super S>> decorators, List<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docs) {
        this.gen = gen;
        this.decorators = decorators;
        this.docs = docs;
        this.member = member;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        if (!member.isModelDefinedType()) {
            switch (member.target().getType()) {
                case TIMESTAMP:
                    cb.importing(Instant.class);
                    break;
                case BIG_DECIMAL:
                    cb.importing(BigDecimal.class);
                    break;
                case BIG_INTEGER:
                    cb.importing(BigInteger.class);
                    break;
            }
        }
        gen.generateField(member, decorators, docs, cb);
    }

}
