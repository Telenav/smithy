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
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import static com.telenav.smithy.java.generators.builtin.SpanUtils.validateSpanArguments;
import static com.telenav.smithy.java.generators.builtin.SpanUtils.withSpanArguments;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.names.NumberKind;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 *
 * @author Tim Boudreau
 */
public final class SpanMethodsGenerator implements StructureContributor {

    static final SpanMethodsGenerator INSTANCE = new SpanMethodsGenerator();

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        withSpanArguments(helper, (less, more, emptyOk) -> {
            validateSpanArguments(helper, less, more, (lesser, greater) -> {
                if (emptyOk) {
                    generateIsEmptyMethod(helper, cb, lesser, greater);
                }
                generateSizeMethod(helper, cb, lesser, greater);
            });
            return null;
        });
    }

    public static boolean isApplicable(StructureGenerationHelper help) {
        return Boolean.TRUE.equals(withSpanArguments(help, (x, y, z) -> true));
    }

    private <T> void generateIsEmptyMethod(StructureGenerationHelper helper,
            ClassBuilder<T> cb, StructureMember<?> lesser, StructureMember<?> greater) {
        if (cb.containsMethodNamed("isEmpty")) {
            return;
        }
        cb.method("isEmpty", mth -> {
            cb.importing("com.fasterxml.jackson.annotation.JsonIgnore");
            mth.annotatedWith("JsonIgnore").closeAnnotation();
            mth.withModifier(PUBLIC)
                    .docComment("Determine if this " + cb.className() + " represents an empty span of <code>" + lesser.typeName() + "</code>s."
                            + "\n@return true if the " + lesser.jsonName() + " is equal to the " + greater.jsonName())
                    .returning("boolean");
            mth.body(bb -> {
                if (lesser.isPrimitiveNumber()) {
                    bb.returning(variable(lesser.field()).isEqualTo(variable(greater.field())));
                } else {
                    bb.returning(invocationOf("compareTo").withArgument(greater.field()).on(lesser.field()).isEqualTo(number(0)));
                }
            });
        });
    }

    private <T> void generateSizeMethod(StructureGenerationHelper helper,
            ClassBuilder<T> cb, StructureMember<?> lesser, StructureMember<?> greater) {
        if (cb.containsMethodNamed("size")) {
            return;
        }
        cb.method("size", mth -> {
            mth.docComment("Returns the value of <code>" + greater.member().getMemberName()
                    + " - " + lesser.member().getMemberName() + "</code>."
                    + "\n@return The span size of this " + cb.className());
            mth.withModifier(PUBLIC);
            NumberKind retKind;
            NumberKind nk = NumberKind.forShape(lesser.target());
            switch (nk) {
                case BYTE:
                case SHORT:
                case INT:
                    retKind = NumberKind.INT;
                    break;
                case LONG:
                    retKind = NumberKind.LONG;
                    break;
                default:
                    retKind = nk;
            }
            mth.returning(retKind.primitiveTypeName());
            boolean needInvocation = lesser.isModelDefinedType();
            mth.body(bb -> {
                if (!needInvocation) {
                    bb.returning(variable(greater.field()).minus(variable(lesser.field())));
                } else {
                    String toCall = retKind.numberMethod();
                    bb.returning(invocationOf(toCall).on(greater.field())
                            .minus(invocationOf(toCall).on(lesser.field())));
                }
            });
        });
    }
}
