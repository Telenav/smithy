/*
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.smithy.utils;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;
import software.amazon.smithy.utils.Pair;

/**
 *
 * @author Tim Boudreau
 */
public final class ConstraintsChecker {

    private final Model model;
    private final MemberShape member;

    private ConstraintsChecker(Model model, MemberShape member) {
        this.model = model;
        this.member = member;
    }

    public static void check(Model model, MemberShape member) {
        new ConstraintsChecker(model, member).check();
    }

    private <T extends Trait> Optional<Pair<T, T>> pair(Class<T> c) {
        Optional<T> memberTrait = member.getTrait(c);
        if (!memberTrait.isPresent()) {
            return Optional.empty();
        }
        Optional<T> targetTrait = model.expectShape(member.getTarget()).getTrait(c);
        if (memberTrait.isPresent() && targetTrait.isPresent()) {
            return Optional.of(Pair.of(memberTrait.get(), targetTrait.get()));
        } else {
            return Optional.empty();
        }
    }

    public void check() {
        pair(LengthTrait.class).ifPresent(lengths -> {
            check(lengths.left, lengths.right);
            Shape target = model.expectShape(member.getTarget());
            if (target.isListShape() && target.hasTrait(UniqueItemsTrait.class)) {
                MemberShape mem = target.asListShape().get().getMember();
                sanityCheckTypeIsConstructible(mem, lengths.left);
                sanityCheckTypeIsConstructible(mem, lengths.right);
            } else if (target.isMapShape()) {
                MemberShape mem = target.asMapShape().get().getKey();
                sanityCheckTypeIsConstructible(mem, lengths.left);
                sanityCheckTypeIsConstructible(mem, lengths.right);
            }
        });
        pair(RangeTrait.class).ifPresent(lengths -> {
            check(lengths.left, lengths.right);
        });
    }

    private void sanityCheckTypeIsConstructible(MemberShape mem, LengthTrait len) {
        Shape memberTarget = model.expectShape(mem.getTarget());
        if (memberTarget.isEnumShape()) {
            int count = memberTarget.asEnumShape().get().getEnumValues().size();
            checkMaxConstantsCanFit(count, len, mem);
        } else if (memberTarget.isIntEnumShape()) {
            int count = memberTarget.asEnumShape().get().getEnumValues().size();
            checkMaxConstantsCanFit(count, len, mem);
        }
    }

    private void checkMaxConstantsCanFit(int count, LengthTrait trait, Shape memberTarget) {
        trait.getMin().ifPresent(min -> {
            if (min > count) {
                throw new ExpectationNotMetException("This shape is a set or map of "
                        + "the enumerated type " + memberTarget.getId()
                        + ", but the minimum size is greater than the number of "
                        + "enum constants.  A valid instance cannot be created.", member);
            }
        });
    }

    private void check(LengthTrait a, LengthTrait b) {
        IntRange<? extends IntRange<?>> aRange = Range.ofCoordinates(
                a.getMin()
                        .map(l -> l.intValue()).orElse(0),
                a.getMax()
                        .map(l -> l.intValue()).orElse(Integer.MAX_VALUE));

        IntRange<? extends IntRange<?>> bRange = Range.ofCoordinates(
                b.getMin()
                        .map(l -> l.intValue()).orElse(0),
                b.getMax()
                        .map(l -> l.intValue()).orElse(Integer.MAX_VALUE));

        if (!aRange.overlaps(bRange)) {
            throw new ExpectationNotMetException("Contradictory length constraints on "
                    + member.getId() + " with range " + aRange + " and its shape "
                    + member.getTarget() + " " + bRange, member);
        }
    }

    private void check(RangeTrait a, RangeTrait b) {
        a.getMax().ifPresent(aMax -> {
            b.getMin().ifPresent(bMin -> {
                if (bMin.compareTo(aMax) > 1) {
                    throw new ExpectationNotMetException("Member range contradicts range specified on type - "
                            + " maximum on member " + aMax + " is less than minimum on shape " + bMin, member);
                }
            });
        });

        a.getMin().ifPresent(aMin -> {
            b.getMax().ifPresent(bMax -> {
                if (aMin.compareTo(bMax) > 1) {
                    throw new ExpectationNotMetException("Member range contradicts range specified on type - "
                            + " minimum on member " + aMin + " is greater than minimum on shape " + bMax, member);
                }
            });
        });
    }
}
