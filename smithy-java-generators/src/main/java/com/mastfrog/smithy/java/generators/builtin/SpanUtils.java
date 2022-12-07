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
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.function.TriFunction;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import com.mastfrog.smithy.simple.extensions.SpanTrait;
import com.telenav.smithy.names.NumberKind;
import static com.telenav.smithy.names.NumberKind.forShape;
import java.util.Optional;
import java.util.function.BiConsumer;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.NumberShape;

/**
 * Validation and member lookup for structures with a SpanTrait.
 *
 * @author Tim Boudreau
 */
public final class SpanUtils {

    private SpanUtils() {
        throw new AssertionError();
    }

    public static void withSpanTargets(StructureGenerationHelper help, TriConsumer<StructureMember<? extends NumberShape>, StructureMember<? extends NumberShape>, Boolean> tri) {
        withSpanArguments(help, (nameA, nameB, allowEmpty) -> {
            validateSpanArguments(help, nameA, nameB, (memA, memB) -> {
                tri.accept(memA.as(NumberShape.class).get(), memB.as(NumberShape.class).get(), allowEmpty);
            });
            return null;
        });
    }

    public static <T> T withSpanArguments(StructureGenerationHelper help, TriFunction<String, String, Boolean, T> logic) {
        return help.structure().getTrait(SpanTrait.class).map(span -> {
            return logic.apply(span.lesser(), span.greater(), span.emptyAllowed());
        }).orElse(null);
    }

    public static void validateSpanArguments(StructureGenerationHelper help, String lesser, String greater,
            BiConsumer<StructureMember<?>, StructureMember<?>> c) {
        Optional<StructureMember<?>> less = help.findMember(lesser);
        if (!less.isPresent()) {
            throw new ExpectationNotMetException("No member named " + lesser + " on " + help.structure().getId(), help.structure());
        }
        Optional<StructureMember<?>> more = help.findMember(greater);
        if (!more.isPresent()) {
            throw new ExpectationNotMetException("No member named " + greater + " on " + help.structure().getId(), help.structure());
        }

        StructureMember<?> lessMember = less.get();
        StructureMember<?> moreMember = more.get();

        if (lessMember.isRequired() && !lessMember.hasDefault()) {
            throw new ExpectationNotMetException("Members named in a @span trait must be @required or "
                    + "have a default value, but " + lesser + " has neither", help.structure());
        }
        if (moreMember.isRequired() && !moreMember.hasDefault()) {
            throw new ExpectationNotMetException("Members named in a @span trait must be @required or "
                    + "have a default value, but " + greater + " has neither", help.structure());
        }

        String lessType = lessMember.typeName();
        String moreType = moreMember.typeName();
        if (!lessType.equals(moreType)) {
            throw new ExpectationNotMetException("Members named in a @span trait must be of "
                    + "the same type, but " + lesser + " is "
                    + lessType + " and " + greater + " is "
                    + moreType, help.structure());
        }

        NumberKind lessKind = forShape(lessMember.target());
        if (lessKind == null) {
            throw new ExpectationNotMetException("Members named in a @span trait must be numbers, "
                    + "but " + lesser + " is a " + lessMember.target().getType(), help.structure());

        }

        c.accept(lessMember, moreMember);
    }

}
