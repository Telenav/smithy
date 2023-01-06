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
package com.telenav.smithy.extensions.java;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.HashCodeContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.spi.StructureExtensions;
import com.telenav.smithy.extensions.IdentityTrait;
import com.mastfrog.util.service.ServiceProvider;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptySet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import static java.util.Optional.empty;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(StructureExtensions.class)
public final class StructureIdentityExtensionJava implements StructureExtensions {

    @Override
    public Optional<StructureContributor> hashCodeGenerator(StructureGenerationHelper structureOwner,
            List<? extends StructureMember<?>> members) {
        Set<StructureMember<?>> idMembers = identityMembers(structureOwner.membersSortedByWeight());
        if (idMembers.isEmpty()) {
            return empty();
        }
        StructureExtensions allExts = structureOwner.context().get(KEY).get();
        List<HashCodeWrapper<?>> wrappers = new ArrayList<>();
        for (StructureMember<?> sm : idMembers) {
            wrappers.add(hashCoders(structureOwner, sm, allExts));
        }
        return Optional.of(new IdentityHashCodeGenerator(idMembers, wrappers));
    }

    private <S extends Shape> HashCodeWrapper<S> hashCoders(StructureGenerationHelper helper,
            StructureMember<S> member, StructureExtensions allExts) {

        List<HashCodeContributor<? super S>> contributors = new ArrayList<>();
        allExts.collectHashCodeContributors(helper, member, contributors::add);
        return new HashCodeWrapper<>(member, contributors);
    }

    @Override
    public Optional<StructureContributor> equalsGenerator(StructureGenerationHelper structureOwner) {
        Set<StructureMember<?>> idMembers = identityMembers(structureOwner.membersSortedByWeight());
        if (idMembers.isEmpty()) {
            return empty();
        }
        return Optional.of(new IdentityEqualsGenerator(idMembers));
    }

    @Override
    public void collectClassDocContributors(StructureGenerationHelper helper, Consumer<? super DocumentationContributor<StructureShape, StructureGenerationHelper>> into) {
        Set<StructureMember<?>> idMembers = identityMembers(helper.members());
        if (!idMembers.isEmpty()) {
            into.accept(new IdentityClassDocContributor(idMembers));
        }
    }

    Set<StructureMember<?>> identityMembers(Collection<? extends StructureMember> members) {
        Set<StructureMember<?>> result = null;
        for (StructureMember<?> sm : members) {
            if (sm.member().getTrait(IdentityTrait.class).isPresent()) {
                if (result == null) {
                    result = new LinkedHashSet<>();
                }
                result.add(sm);
                if (!sm.isRequired() && !sm.hasDefault()) {
                    throw new IllegalArgumentException(sm.member().getId()
                            + " is not @required and has no default - identity cannot be based on it.");
                }
            }
        }
        return result == null ? emptySet() : result;
    }

    static final class HashCodeWrapper<S extends Shape> {

        private final StructureMember<S> member;
        private final List<? extends HashCodeContributor<? super S>> contributor;

        HashCodeWrapper(StructureMember<S> member, List<? extends HashCodeContributor<? super S>> contributor) {
            this.member = member;
            this.contributor = contributor;
        }

        <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void generate(B bldr, String hashVar, ClassBuilder<?> cb) {
            contributor.forEach(contrib -> contrib.contributeFieldHashCodeComputation(member, hashVar, bldr, cb));
        }

    }

}
