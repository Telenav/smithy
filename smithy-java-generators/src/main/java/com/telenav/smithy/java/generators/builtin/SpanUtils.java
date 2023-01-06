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
package com.telenav.smithy.java.generators.builtin;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.function.TriFunction;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.extensions.SpanTrait;
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
