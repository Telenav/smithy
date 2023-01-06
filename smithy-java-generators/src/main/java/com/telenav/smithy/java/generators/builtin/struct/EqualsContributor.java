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
package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import java.util.Optional;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Contributes an equality test clause to an equals method implementation,
 * generating code to return false in the case of a mismatch.
 *
 * @author Tim Boudreau
 */
public interface EqualsContributor<S extends Shape> {

    <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(
            StructureMember<? extends S> member,
            StructureGenerationHelper helper,
            String otherVar,
            B bb,
            ClassBuilder<?> cb);

    /**
     * Wrap the code this check generates in a null check which returns false if
     * the nullity of this and the other instance's field is not the same.
     *
     * @return A wrapper around this EqualsContributor
     */
    default EqualsContributor<S> wrapInNullCheck() {
        return new EqualsContributor<S>() {
            @Override
            public <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(
                    StructureMember<? extends S> member, StructureGenerationHelper helper, String otherVar, B bb, ClassBuilder<?> cb) {

                ClassBuilder.IfBuilder<B> els = bb.iff().booleanExpression("(this." + member.field() + " == null) != (other." + member.field() + " == null)")
                        .returning(false)
                        .elseIf().isNotNull("this." + member.field()).endCondition();
                EqualsContributor.this.contributeToEqualsComputation(member, helper, otherVar, els, cb);
                els.endIf();
            }

            @Override
            public EqualsContributor<S> wrapInNullCheck() {
                return this;
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <S extends Shape> EqualsContributor<S> equalsContributor(
            StructureMember<S> member, StructureGenerationHelper helper) {
        EqualsContributor<S> result;
        if (member.isModelDefinedType()) {
            result = (EqualsContributor<S>) OBJECT_EQUALITY;
        } else {
            Optional<EqualsContributor<?>> opt = member.as(DoubleShape.class).map(sm -> DOUBLE_EQUALITY);
            if (!opt.isPresent()) {
                opt = member.as(FloatShape.class).map(fl -> FLOAT_EQUALITY);
            }
            if (!opt.isPresent() && member.isSmithyApiDefinedType()) {
                if (member.isPrimitive() && (member.isRequired() || member.hasDefault())) {
                    opt = Optional.of(INSTANCE_EQUALITY);
                }
            }
            result = (EqualsContributor<S>) opt.orElse(OBJECT_EQUALITY);
        }
        if (!member.isRequired() && !member.hasDefault()) {
            result = result.wrapInNullCheck();
        }
        return result;
    }

    static EqualsContributor<DoubleShape> DOUBLE_EQUALITY
            = new EqualsContributor<DoubleShape>() {
        @Override
        public <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(StructureMember<? extends DoubleShape> member, StructureGenerationHelper helper, String other, B bb, ClassBuilder<?> cb) {
            bb.iff().invocationOf("doubleToLongBits").withArgument(member.field())
                    .on("Double")
                    .isNotEqualToInvocationOf("doubleToLongBits")
                    .withArgumentFromField(member.field())
                    .of(other)
                    .on("Double")
                    .returning(false)
                    .endIf();
        }
    };

    static EqualsContributor<FloatShape> FLOAT_EQUALITY
            = new EqualsContributor<FloatShape>() {
        @Override
        public <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(StructureMember<? extends FloatShape> member, StructureGenerationHelper helper, String other, B bb, ClassBuilder<?> cb) {
            bb.iff().invocationOf("floatToIntBits").withArgument(member.field())
                    .on("Float")
                    .isNotEqualToInvocationOf("floatToIntBits")
                    .withArgumentFromField(member.field())
                    .of(other)
                    .on("Float")
                    .returning(false)
                    .endIf();
        }
    };

    static EqualsContributor<Shape> OBJECT_EQUALITY
            = new EqualsContributor<Shape>() {
        @Override
        public <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(
                StructureMember<? extends Shape> member,
                StructureGenerationHelper helper,
                String otherVar,
                B bb,
                ClassBuilder<?> cb) {
            if (!member.isRequired() && !member.hasDefault()) {
                helper.generateEqualityCheckOfNullable("this." + member.field(), "other." + member.field(), bb);
            } else {
                bb.iff().invocationOf("equals")
                        .withArgumentFromField(member.field()).of(otherVar)
                        .onField(member.field())
                        .ofThis()
                        .isFalse()
                        .endCondition()
                        .returning(false).endIf();
            }
        }
    };

    static EqualsContributor<Shape> INSTANCE_EQUALITY
            = new EqualsContributor<Shape>() {
        @Override
        public <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(StructureMember<? extends Shape> member, StructureGenerationHelper helper, String otherVar, B bb, ClassBuilder<?> cb) {
            bb.iff().variable("this." + member.field())
                    .notEqualToField(member.field())
                    .of(otherVar)
                    .returning(false)
                    .endIf();
        }
    };

}
