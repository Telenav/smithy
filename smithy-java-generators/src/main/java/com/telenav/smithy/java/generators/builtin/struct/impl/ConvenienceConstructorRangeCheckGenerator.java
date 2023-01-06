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
