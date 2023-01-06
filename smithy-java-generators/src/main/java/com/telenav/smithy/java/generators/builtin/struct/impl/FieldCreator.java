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
