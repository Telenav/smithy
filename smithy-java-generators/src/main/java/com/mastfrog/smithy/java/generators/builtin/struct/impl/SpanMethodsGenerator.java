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
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import static com.mastfrog.smithy.java.generators.builtin.SpanUtils.validateSpanArguments;
import static com.mastfrog.smithy.java.generators.builtin.SpanUtils.withSpanArguments;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
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
