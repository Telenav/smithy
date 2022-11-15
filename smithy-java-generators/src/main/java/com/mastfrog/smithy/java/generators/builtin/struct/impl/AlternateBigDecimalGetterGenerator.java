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

package com.mastfrog.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.shapes.BigDecimalShape;

/**
 * Generates get___AsDouble() for BigDecimal fields.
 *
 * @author Tim Boudreau
 */
final class AlternateBigDecimalGetterGenerator implements StructureContributor {

    private final StructureMember<BigDecimalShape> member;

    AlternateBigDecimalGetterGenerator(StructureMember<BigDecimalShape> member) {
        this.member = member;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        boolean canBeNull = !member.isRequired() && !member.hasDefault();
        boolean isWrapperType = member.isModelDefinedType();
        cb.method(member.getterName() + "AsDouble", mth -> {
            String docs = "Convenience getter for the {@link java.math.BigDecimal}" + " property <code>" + member.jsonName() + "</code> as a <code>double</code>.";
            if (canBeNull) {
                docs += "\n@return the value of " + member.field() + ", if it is non-null; Double.NEGATIVE_INFINITY or " + "Double.POSITIVE_INFINITY if the value cannot be expressed " + "in a double, or Double.NaN if the field is null.";
            } else {
                docs += "\n@return the value of " + member.field() + " or Double.NEGATIVE_INFINITY or " + "Double.POSITIVE_INFINITY if the value cannot be expressed " + "in a double, or Double.NaN if the field is null.";
            }
            mth.docComment(docs);
            mth.withModifier(Modifier.PUBLIC).returning("double").body(bb -> {
                if (canBeNull) {
                    ClassBuilder.ElseClauseBuilder<?> els = bb.ifNull("this." + member.field()).returningField("NaN").of("Double").orElse();
                    if (isWrapperType) {
                        els.returningInvocationOf("doubleValue").onInvocationOf("get").onField(member.field()).ofThis();
                    } else {
                        els.returningInvocationOf("doubleValue").onField(member.field()).ofThis();
                    }
                    els.endIf();
                } else {
                    if (isWrapperType) {
                        bb.returningInvocationOf("doubleValue").onInvocationOf("get").onField(member.field()).ofThis();
                    } else {
                        bb.returningInvocationOf("doubleValue").onField(member.field()).ofThis();
                    }
                }
            });
        });
    }

}
