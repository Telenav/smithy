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
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.Trait;
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
        });
        pair(RangeTrait.class).ifPresent(lengths -> {
            check(lengths.left, lengths.right);
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
