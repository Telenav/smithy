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
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.shapes.BigIntegerShape;

/**
 * Generates get___AsLong getters for BigInteger fields.
 *
 * @author Tim Boudreau
 */
final class AlternateBigIntegerGetterGenerator implements StructureContributor {

    private final StructureMember<BigIntegerShape> member;

    AlternateBigIntegerGetterGenerator(StructureMember<BigIntegerShape> member) {
        this.member = member;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        boolean canBeNull = !member.isRequired() && !member.hasDefault();
        boolean isWrapperType = member.isModelDefinedType();
        cb.method(member.getterName() + "AsLong", mth -> {
            String docs = "Convenience getter for the {@link java.math.BigInteger}" + " property <code>" + member.jsonName() + "</code> as a <code>long</code>.";
            if (canBeNull) {
                docs += "\n@return the value of " + member.field() + ", if it is non-null; <code>0L</code> if it is null.";
            } else {
                docs += "\n@return the value of " + member.field() + " as a long";
            }
            docs += "\n@throws ArithmeticException if the value cannot fit in a long";
            mth.docComment(docs);
            mth.withModifier(Modifier.PUBLIC).returning("long").body(bb -> {
                if (canBeNull) {
                    ClassBuilder.ElseClauseBuilder<?> els = bb.ifNull("this." + member.field()).returning(0L).orElse();
                    if (isWrapperType) {
                        els.returningInvocationOf("longValueExact").onInvocationOf("get").onField(member.field()).ofThis();
                    } else {
                        els.returningInvocationOf("longValueExact").onField(member.field()).ofThis();
                    }
                    els.endIf();
                } else {
                    if (isWrapperType) {
                        bb.returningInvocationOf("longValueExact").onInvocationOf("get").onField(member.field()).ofThis();
                    } else {
                        bb.returningInvocationOf("longValueExact").onField(member.field()).ofThis();
                    }
                }
            });
        });
    }

}
