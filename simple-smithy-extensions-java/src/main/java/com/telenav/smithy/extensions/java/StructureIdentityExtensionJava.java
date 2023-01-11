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

    Set<StructureMember<?>> identityMembers(Collection<? extends StructureMember<?>> members) {
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
