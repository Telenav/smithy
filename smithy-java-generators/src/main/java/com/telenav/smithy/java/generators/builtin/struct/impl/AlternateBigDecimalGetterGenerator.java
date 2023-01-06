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
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
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
