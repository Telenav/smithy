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
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentCheckGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.names.JavaTypes;
import java.math.BigDecimal;
import java.util.Optional;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 *
 * @author Tim Boudreau
 */
final class ConvenienceConstructorRangeCheckGenerator<S extends Shape> implements ConstructorArgumentCheckGenerator<S> {

    @Override
    public <T, B extends ClassBuilder.BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(StructureMember<? extends S> member, StructureGenerationHelper structureOwner, ClassBuilder<?> addTo, B bb, ConstructorKind kind) {
        boolean omitMinTest = false;
        boolean omitMaxTest = false;
        Optional<JavaTypes> ojt = member.javaType();
        if (ojt.isPresent()) {
            JavaTypes jt = ojt.get();
            switch (jt) {
                case FLOAT:
                case SHORT:
                case BYTE:
                    // If there is a min or max specified on the member, it MUST be
                    // within the min/max range, so we don't need an extra test for
                    // someDouble > Float.MIN_VALUE if we are going to follow that
                    // immediately with a test of someDouble > whatever, so don't
                    // clutter things with that.
                    Optional<RangeTrait> rng = member.member().getTrait(RangeTrait.class);
                    if (rng.isPresent()) {
                        Optional<BigDecimal> min = rng.get().getMin();
                        if (min.isPresent()) {
                            omitMinTest = true;
                        }
                        Optional<BigDecimal> max = rng.get().getMax();
                        if (max.isPresent()) {
                            omitMaxTest = true;
                        }
                    }
                    break;
                default:
                    return;
            }
            if (!omitMinTest) {
                ClassBuilder.IfBuilder<B> minTest = bb.iff().variable(member.arg()).isLessThan(jt.minValue());
                structureOwner.validation().createThrow(addTo, minTest, "Value of " + member.jsonName() + " is less than " + jt.minValue(), member.arg());
                minTest.endIf();
            }
            if (!omitMaxTest) {
                ClassBuilder.IfBuilder<B> maxTest = bb.iff().variable(member.arg()).isGreaterThan(jt.maxValue());
                structureOwner.validation().createThrow(addTo, maxTest, "Value of " + member.jsonName() + " is greater than " + jt.maxValue(), member.arg());
                maxTest.endIf();
            }
        }
    }

}
