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
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentCheckGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAssignmentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.EqualsContributor;
import com.telenav.smithy.java.generators.builtin.struct.FieldDecorator;
import com.telenav.smithy.java.generators.builtin.struct.FieldGenerator;
import com.telenav.smithy.java.generators.builtin.struct.GetterDecorator;
import com.telenav.smithy.java.generators.builtin.struct.GetterGenerator;
import com.telenav.smithy.java.generators.builtin.struct.HashCodeContributor;
import com.telenav.smithy.java.generators.builtin.struct.HeadTailToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.ToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.spi.StructureExtensions;
import com.telenav.smithy.extensions.SpanTrait;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultStructureGenerators implements StructureExtensions {

    static final String MAVEN_ARTIFACT_ID = "smithy-java-generators";
    static final String MAVEN_GROUP_ID = "com.telenav.smithy";

    static boolean hasInconvenientPrimitiveArguments(StructureGenerationHelper helper) {
        for (StructureMember<?> mem : helper.members()) {
            if (mem.isSmithyApiDefinedType()) {
                switch (mem.target().getType()) {
                    case BYTE:
                    case SHORT:
                    case FLOAT:
                        return true;
                }
            }
        }
        return false;
    }

    static boolean hasNullablePrimitiveTypesInConstructor(StructureGenerationHelper helper) {
        for (StructureMember<?> mem : helper.members()) {
            if (!mem.constructorArgumentTypeName().equals(mem.typeName())) {
                return true;
            }
        }
        return false;
    }

    protected static <T> Optional<T> ifNotEmpty(Collection<?> items, Supplier<T> supp) {
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(supp.get());
    }

    @Override
    public Optional<StructureContributor> classDocCreator(StructureGenerationHelper helper) {
        List<DocumentationContributor<StructureShape, StructureGenerationHelper>> docs = new ArrayList<>();
        helper.context().get(KEY).get().collectClassDocContributors(helper, docs::add);
        return ifNotEmpty(docs, () -> new ClassDocAggregator(docs));
    }

    @Override
    public Optional<StructureContributor> hashCodeGenerator(StructureGenerationHelper helper, List<? extends StructureMember<?>> members) {
        List<HashCodeWrapper<?>> wrappers = new ArrayList<>();
        collectHashCodeWrappers(helper, wrappers);
        return Optional.of(new DefaultHashCodeGenerator(wrappers));
    }

    @Override
    public void collectConstructorGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members, Consumer<? super StructureContributor> into) {
        Set<ConstructorKind> kinds = EnumSet.noneOf(ConstructorKind.class);
        assert !kinds.isEmpty() : "Empty kinds";
        helper.context().get(KEY).get().collectConstructorKinds(helper, kinds::add);
        for (ConstructorKind ck : kinds) {
            collectConstructorGenerators(ck, helper, members, into);
        }
    }

    @Override
    public void collectConstructorGenerators(ConstructorKind kind, StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        List<DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs = new ArrayList<>();
        helper.context().get(KEY).get().collectConstructorDocumentationContributors(helper, kind, docs::add);
        List<ConstructorAnnotator> constructorAnnotators = new ArrayList<>(8);
        helper.context().get(KEY).get().collectConstructorAnnotators(helper, constructorAnnotators::add, kind);
        List<ConstructorArgumentWrapper<?>> arguments = new ArrayList<>(members.size());
        for (StructureMember<?> mem : kind.memberList(helper)) {
            constructorArgumentWrapper(mem, helper, kind).ifPresent(arguments::add);
        }
        into.accept(new ConstructorCreator(kind, true, arguments, constructorAnnotators, docs));
    }

    protected <S extends Shape> Optional<ConstructorArgumentWrapper<S>> constructorArgumentWrapper(StructureMember<S> member, StructureGenerationHelper structureOwner, ConstructorKind kind) {
        List<ConstructorAnnotator> annotators = new ArrayList<>();
        structureOwner.context().get(KEY).get().collectConstructorAnnotators(structureOwner, annotators::add, kind);
        Optional<ConstructorArgumentGenerationWrapper<S>> argGenerator = constructorArgumentGenerationWrapper(member, structureOwner, kind);
        Optional<ConstructorArgumentAssignmentWrapper<S>> assig = constructorArgumentAssignmentWrapper(member, structureOwner, kind);
        if (!argGenerator.isPresent() || !assig.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new ConstructorArgumentWrapper<>(member, argGenerator.get(), assig.get()));
    }

    protected <S extends Shape> Optional<ConstructorArgumentGenerationWrapper<S>> constructorArgumentGenerationWrapper(StructureMember<S> member, StructureGenerationHelper helper, ConstructorKind kind) {
        List<ConstructorArgumentAnnotator<? super S>> annotators = new ArrayList<>();
        helper.context().get(KEY).get().collectConstructorArgumentAnnotators(member, helper, kind, annotators::add);
        return constructorArgumentGeneratorFor(member, kind, helper).map(gen -> new ConstructorArgumentGenerationWrapper<>(member, gen, annotators));
    }

    @Override
    public <S extends Shape> void collectConstructorArgumentAnnotators(StructureMember<S> member, StructureGenerationHelper helper, ConstructorKind kind, Consumer<? super ConstructorArgumentAnnotator<? super S>> into) {
        switch (kind) {
            case JSON_DESERIALIZATON:
                into.accept(new JsonPropertyConstructorArgumentAnnotator<>());
                break;
        }
    }

    protected <S extends Shape> Optional<ConstructorArgumentAssignmentWrapper<S>> constructorArgumentAssignmentWrapper(StructureMember<S> member, StructureGenerationHelper helper, ConstructorKind kind) {
        List<ConstructorArgumentCheckGenerator<? super S>> checks = new ArrayList<>(4);
        helper.context().get(KEY).get().collectConstructorArgumentCheckGenerators(kind, member, helper, checks::add);
        return constructorArgumentAssigner(member, helper).map(assig -> new ConstructorArgumentAssignmentWrapper<>(member, assig, checks));
    }

    @Override
    public <S extends Shape> Optional<ConstructorAssignmentGenerator<? super S>> constructorArgumentAssigner(StructureMember<S> member, StructureGenerationHelper helper) {
        if (true || member.hasDefault()) {
            return Optional.of(ConstructorAssignmentGenerator.DEFAULT);
        }
        return Optional.of(SimpleConstructorArgumentAssigner.INSTANCE);
    }

    private void collectToStringContributors(StructureGenerationHelper helper, Consumer<? super ToStringContributionWrapper<?>> into) {
        for (StructureMember<?> sm : helper.membersSortedByName()) {
            collectToStringContributorWrappers(sm, helper, into);
        }
    }

    protected <S extends Shape> void collectToStringContributorWrappers(StructureMember<S> member, StructureGenerationHelper helper, Consumer<? super ToStringContributionWrapper<? super S>> into) {
        List<ToStringContributor<? super S>> forMember = new ArrayList<>();
        helper.context().get(KEY).get().collectToStringContributors(member, helper, forMember::add);
        for (ToStringContributor<? super S> ts : forMember) {
            into.accept(new ToStringContributionWrapper<>(member, ts));
        }
    }

    @Override
    public <S extends Shape> void collectToStringContributors(StructureMember<S> shape, StructureGenerationHelper helper, Consumer<? super ToStringContributor<? super S>> into) {
        into.accept(ToStringContributor.DEFAULT);
    }

    @Override
    public void collectConstructorAnnotators(StructureGenerationHelper helper, Consumer<? super ConstructorAnnotator> into, ConstructorKind kind) {
        switch (kind) {
            case JSON_DESERIALIZATON:
                into.accept(new JsonCreatorConstructorAnnotator());
                break;
        }
    }

    @Override
    public void collectConstructorKinds(StructureGenerationHelper structureOwner, Consumer<? super ConstructorKind> into) {
        into.accept(ConstructorKind.JSON_DESERIALIZATON);
        if (hasNullablePrimitiveTypesInConstructor(structureOwner)) {
            into.accept(ConstructorKind.SECONDARY_WITH_PRIMITIVES);
        }
        if (hasInconvenientPrimitiveArguments(structureOwner)) {
            into.accept(ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES);
        }
    }

    @Override
    public Optional<StructureContributor> equalsGenerator(StructureGenerationHelper structureOwner) {
        List<EqualsContributorWrapper<?>> fieldTesters = new ArrayList<>();
        for (StructureMember<?> mem : structureOwner.membersSortedByWeight()) {
            collectWrappers(structureOwner, mem, fieldTesters::add);
        }
        return Optional.of(new EqualsCreator(fieldTesters));
    }

    private <S extends Shape> void collectWrappers(StructureGenerationHelper structureOwner, StructureMember<S> member,
            Consumer<? super EqualsContributorWrapper<? super S>> into) {
        structureOwner.context().get(KEY).get().equalsContributor(structureOwner, member)
                .ifPresent((EqualsContributor<? super S> contrib)
                        -> into.accept(new EqualsContributorWrapper<S>(member, contrib)));
    }

    @Override
    public <S extends Shape> Optional<EqualsContributor<? super S>> equalsContributor(StructureGenerationHelper structureOwner, StructureMember<S> member) {
        List<EqualsContributor<? super S>> all = new ArrayList<>();
        structureOwner.context().get(KEY).get().collectEqualsContributors(structureOwner, member, all::add);
        switch (all.size()) {
            case 0:
                return Optional.empty();
            case 1:
                return Optional.of(all.get(0));
            default:
                return Optional.of(new CombinedEqualsContributor<>(all));
        }
    }

    static class CombinedEqualsContributor<S extends Shape> implements EqualsContributor<S> {

        private final List<? extends EqualsContributor<? super S>> all;

        CombinedEqualsContributor(List<? extends EqualsContributor<? super S>> all) {
            this.all = all;
        }

        @Override
        public <R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void contributeToEqualsComputation(StructureMember<? extends S> member, StructureGenerationHelper helper, String otherVar, B bb, ClassBuilder<?> cb) {
            for (EqualsContributor<? super S> eq : all) {
                eq.contributeToEqualsComputation(member, helper, otherVar, bb, cb);
            }
        }
    }

    @Override
    public <S extends Shape> void collectEqualsContributors(StructureGenerationHelper helper, StructureMember<S> member, Consumer<? super EqualsContributor<? super S>> into) {
        into.accept(EqualsContributor.equalsContributor(member, helper));
    }

    @Override
    public Optional<StructureContributor> toStringGenerator(StructureGenerationHelper helper) {
        List<ToStringContributionWrapper<?>> contributors = new ArrayList<>();
        collectToStringContributors(helper, contributors::add);
        List<HeadTailToStringContributor> headContributors = new ArrayList<>();
        helper.context().get(KEY).get().collectHeadToStringContributionWrappers(helper, headContributors::add);
        List<HeadTailToStringContributor> tailContributors = new ArrayList<>();
        helper.context().get(KEY).get().collectTailToStringContributionWrappers(helper, tailContributors::add);
        return ifNotEmpty(contributors, () -> new ToStringCreator(headContributors, contributors, tailContributors));
    }

    @Override
    public <S extends Shape> Optional<ConstructorArgumentGenerator<S>> constructorArgumentGeneratorFor(StructureMember<S> member,
            ConstructorKind kind, StructureGenerationHelper helper) {
        switch (kind) {
            case JSON_DESERIALIZATON:
                return Optional.of(new DefaultConstructorArgumentGenerator<>());
            case SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES:
                return Optional.of(new ConvenienceConstructorArgumentGenerator<>());
            case SECONDARY_WITH_PRIMITIVES:
                return Optional.of(new PrimitiveConstructorArgumentGenerator<>());
            default:
                throw new AssertionError(kind);
        }
    }

    @Override
    public void collectFieldGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members, Consumer<? super StructureContributor> into) {
        for (StructureMember<?> member : members) {
            helper.context().get(KEY).get().fieldGenerator(member, helper).ifPresent(into);
        }
    }

    @Override
    public void collectGetterGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members, Consumer<? super StructureContributor> into) {
        for (StructureMember<?> mem : members) {
            helper.context().get(KEY).get().getterGenerator(helper, mem).ifPresent(into);
        }
    }

    @Override
    public <S extends Shape> Optional<StructureContributor> getterGenerator(StructureGenerationHelper helper, StructureMember<S> member) {
        List<GetterDecorator<? super S>> annotators = new ArrayList<>();
        helper.context().get(KEY).get().collectGetterDecorators(helper, member, annotators::add);
        List<DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors = new ArrayList<>();
        helper.context().get(KEY).get().collectGetterDocumentationContributors(helper, member, docContributors::add);
        GetterGenerator<S> generator = getterGenerator(member);
        return Optional.of(new GetterCreator<>(member, generator, annotators, docContributors));
    }

    protected <S extends Shape> GetterGenerator<S> getterGenerator(StructureMember<S> member) {
        if (!member.isRequired() && !member.hasDefault()) {
            return new OptionalGetterGenerator<>();
        }
        return new SimpleGetterGenerator<>();
    }

    @Override
    public <S extends Shape> void collectGetterDocumentationContributors(StructureGenerationHelper helper, StructureMember<S> member, Consumer<? super DocumentationContributor<? super S, StructureMember<? extends S>>> into) {
        into.accept(new DocTraitDocContributor<>());
        into.accept(new GenericPropertyDocContributor<>());
    }

    @Override
    public <S extends Shape> void collectGetterDecorators(StructureGenerationHelper helper, StructureMember<S> member, Consumer<? super GetterDecorator<? super S>> into) {
        if (member.isRequired() || member.hasDefault()) {
            into.accept(new JsonPropertyGetterDecorator<>());
        }
    }

    @Override
    public void collectOtherGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members, Consumer<? super StructureContributor> into) {
        into.accept(new SerialVersionUidDecorator());
        into.accept(new GeneratedAnnotationContributor());
        List<DocumentationContributor<StructureShape, StructureGenerationHelper>> docs = new ArrayList<>();
        if (!docs.isEmpty()) {
            into.accept(new ClassDocumentationCreator(docs));
        }
        for (StructureMember<?> mem : members) {
            helper.context().get(KEY).get().collectOtherContributors(helper, mem, into);
        }
    }

    @Override
    public <S extends Shape> void collectOtherContributors(StructureGenerationHelper helper, StructureMember<S> member, Consumer<? super StructureContributor> into) {
        member.as(BigDecimalShape.class).ifPresent(bds -> {
            into.accept(new AlternateBigDecimalGetterGenerator(bds));
        });
        member.as(BigIntegerShape.class).ifPresent(bis -> {
            into.accept(new AlternateBigIntegerGetterGenerator(bis));
        });
        into.accept(IsEmptyMethodGenerator.INSTANCE);
        if (ConversionMethodGenerator.canGenerateConversionMethods(helper)) {
            into.accept(ConversionMethodGenerator.INSTANCE);
        }
        if (SpanMethodsGenerator.isApplicable(helper)) {
            into.accept(SpanMethodsGenerator.INSTANCE);
        }
    }

    @Override
    public void collectClassDocContributors(StructureGenerationHelper helper, Consumer<? super DocumentationContributor<StructureShape, StructureGenerationHelper>> all) {
        all.accept(new ClassDocs());
        all.accept(new ConstraintDocs());
        all.accept(new NullableAndDefaultDocs());
        helper.structure().getTrait(SpanTrait.class).ifPresent(sp -> {
            all.accept(SpanTraitDocContributor.INSTANCE);
        });
        all.accept(MemoryUsageDocs.INSTANCE);
        all.accept(new GeneratedFromClassDocs());
    }

    @Override
    public <S extends Shape> void collectFieldDecorators(StructureMember<S> member, StructureGenerationHelper helper, Consumer<? super FieldDecorator<S>> into) {
        if (!member.isRequired()) {
            into.accept(new JsonPropertyFieldDecorator<>());
        }
    }

    @Override
    public <S extends Shape> void collectFieldDocumentationContributors(StructureMember<S> member, StructureGenerationHelper helper, Consumer<? super DocumentationContributor<? super S, StructureMember<? extends S>>> into) {
        into.accept(new DocTraitDocContributor<>());
    }

    @Override
    public <S extends Shape> Optional<StructureContributor> fieldGenerator(StructureMember<S> member, StructureGenerationHelper helper) {
        List<FieldDecorator<? super S>> decorators = new ArrayList<>();
        List<DocumentationContributor<? super S, StructureMember<? extends S>>> docs = new ArrayList<>();
        helper.context().get(KEY).get().collectFieldDecorators(member, helper, decorators::add);
        helper.context().get(KEY).get().collectFieldDocumentationContributors(member, helper, docs::add);
        FieldGenerator<S> gen = fieldGenerator(member, decorators, docs, helper);
        return Optional.of(new FieldCreator<>(member, gen, decorators, docs));
    }

    protected <S extends Shape> HashCodeContributor<S> hashCodeForMember(StructureMember<S> member, StructureGenerationHelper helper) {
        HashCodeContributor<S> result = HashCodeContributor.forMember(member);
        if (!member.isRequired() && !member.hasDefault()) {
            result = result.nullable();
        }
        return result;
    }

    @Override
    public void collectConstructorDocumentationContributors(StructureGenerationHelper helper, ConstructorKind kind, Consumer<? super DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs) {
        docs.accept(new GenericConstructorDocumentationContributor(kind));
        for (StructureMember<?> mem : helper.members()) {
            docs.accept(new OneConstructorArgumentDocumentationContributor<>(mem, kind));
        }
        docs.accept(new ConstructorThrowsDocumentation(kind));
    }

    protected <S extends Shape> FieldGenerator<S> fieldGenerator(StructureMember<S> member, List<? extends FieldDecorator<? super S>> decorators, List<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docs, StructureGenerationHelper helper) {
        return new SimpleFieldGenerator<>();
    }

    private void collectHashCodeWrappers(StructureGenerationHelper helper, List<? super HashCodeWrapper<?>> contributors) {
        for (StructureMember<?> s : helper.membersSortedByWeight()) {
            for (HashCodeWrapper<?> hc : hashCodeContribs(s, helper)) {
                contributors.add(hc);
            }
        }
    }

    private <S extends Shape> List<HashCodeWrapper<S>> hashCodeContribs(StructureMember<S> sm, StructureGenerationHelper helper) {
        List<HashCodeContributor<? super S>> contribs = new ArrayList<>(2);
        helper.context().get(KEY).get().collectHashCodeContributors(helper, sm, contribs::add);
        List<HashCodeWrapper<S>> result = new ArrayList<>();
        for (HashCodeContributor<? super S> hc : contribs) {
            result.add(HashCodeWrapper.create(sm, hc));
        }
        return result;
    }

    @Override
    public <S extends Shape> void collectHashCodeContributors(StructureGenerationHelper helper, StructureMember<S> member, Consumer<? super HashCodeContributor<? super S>> contributors) {
        contributors.accept(hashCodeForMember(member, helper));
    }

    @Override
    public Optional<StructureContributor> defaultInstanceFieldGenerator(StructureGenerationHelper helper) {
        if (allMembersHaveDefaults(helper)) {
            return Optional.of(new DefaultInstanceFieldGenerator());
        }
        return Optional.empty();
    }

    protected boolean allMembersHaveDefaults(StructureGenerationHelper helper) {
        return allMembersHaveDefaults(helper, helper.structure(), new HashSet<>());
    }

    protected boolean allMembersHaveDefaults(StructureGenerationHelper helper, StructureShape shape, Set<ShapeId> seen) {
        for (Map.Entry<String, MemberShape> mem : shape.getAllMembers().entrySet()) {
            boolean hasDefault = mem.getValue().getTrait(DefaultTrait.class).isPresent();
            if (hasDefault) {
                continue;
            }
            if ("smithy.api".equals(shape.getId().getNamespace())) {
                return false;
            }
            ShapeId target = mem.getValue().getTarget();
            Shape targetShape = helper.model().expectShape(target);
            switch (targetShape.getType()) {
                case STRUCTURE:
                    if (!allMembersHaveDefaults(helper, targetShape.asStructureShape().get(), seen)) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return !shape.getAllMembers().isEmpty();
    }

    @Override
    public <S extends Shape> void collectConstructorArgumentCheckGenerators(
            ConstructorKind kind, StructureMember<S> member, StructureGenerationHelper helper, Consumer<? super ConstructorArgumentCheckGenerator<? super S>> into) {
        if (member.isModelDefinedType() || !member.isPrimitive()) {
            if (member.isRequired() && !member.hasDefault()) {
                into.accept(new ConstructorArgumentNullCheckGenerator<>());
            }
        }
        if (kind == ConstructorKind.SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES && member.isPrimitiveNumber()) {
            into.accept(new ConvenienceConstructorRangeCheckGenerator<S>());
        }
        if (member.target().isStructureShape() && member.member().getTrait(PatternTrait.class).isPresent()) {
            into.accept(ConstructorArgumentCheckGenerator.STRING_PATTERN);
        }
        if (helper.structure().getTrait(SpanTrait.class).isPresent()) {
            SpanTrait st = helper.structure().getTrait(SpanTrait.class).get();
            if (st.greater().equals(member.member().getMemberName())) {
                into.accept(ConstructorArgumentCheckGenerator.SPAN_CHECK);
            }
        }
        switch (member.target().getType()) {
            case MAP:
            case SET:
            case LIST:
                if (member.member().getTrait(LengthTrait.class).isPresent()) {
                    into.accept(ConstructorArgumentCheckGenerator.COLLECTION_SIZE);
                }
                break;
            case STRING:
                if (member.member().getTrait(LengthTrait.class).isPresent()) {
                    into.accept(ConstructorArgumentCheckGenerator.STRING_LENGTH);
                }
                break;
            case DOUBLE:
            case FLOAT:
            case LONG:
            case INTEGER:
            case SHORT:
            case BYTE:
                if (member.member().getTrait(RangeTrait.class).isPresent()) {
                    into.accept(ConstructorArgumentCheckGenerator.NUMBER_RANGE);
                }
                break;
            case BIG_DECIMAL:
            case BIG_INTEGER:
                if (member.member().getTrait(RangeTrait.class).isPresent()) {
                    into.accept(ConstructorArgumentCheckGenerator.BIG_NUMBER_RANGE);
                }
                break;
        }
    }

}
