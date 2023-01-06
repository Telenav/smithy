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
package com.telenav.smithy.java.generators.builtin.struct.spi;

import com.telenav.smithy.java.generators.builtin.struct.ConstructorAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAssignmentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.EqualsContributor;
import com.telenav.smithy.java.generators.builtin.struct.FieldDecorator;
import com.telenav.smithy.java.generators.builtin.struct.GetterDecorator;
import com.telenav.smithy.java.generators.builtin.struct.HashCodeContributor;
import com.telenav.smithy.java.generators.builtin.struct.HeadTailToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.ToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentCheckGenerator;

/**
 * Aggregates two StructureExtensions.
 *
 * @author Tim Boudreau
 */
final class StructureExtensionsAggregate implements StructureExtensions {

    private final StructureExtensions a;
    private final StructureExtensions b;

    StructureExtensionsAggregate(StructureExtensions a, StructureExtensions b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public Optional<StructureContributor> classDocCreator(StructureGenerationHelper helper) {
        return a.classDocCreator(helper).or(() -> b.classDocCreator(helper));
    }

    @Override
    public Optional<StructureContributor> defaultInstanceFieldGenerator(StructureGenerationHelper helper) {
        return a.defaultInstanceFieldGenerator(helper).or(() -> b.defaultInstanceFieldGenerator(helper));
    }

    @Override
    public Optional<StructureContributor> equalsGenerator(StructureGenerationHelper structureOwner) {
        return a.equalsGenerator(structureOwner).or(() -> b.equalsGenerator(structureOwner));
    }

    @Override
    public Optional<StructureContributor> hashCodeGenerator(StructureGenerationHelper helper, List<? extends StructureMember<?>> members) {
        return a.hashCodeGenerator(helper, members).or(() -> b.hashCodeGenerator(helper, members));
    }

    @Override
    public Optional<StructureContributor> toStringGenerator(StructureGenerationHelper helper) {
        return a.toStringGenerator(helper).or(() -> b.toStringGenerator(helper));
    }

    @Override
    public <S extends Shape> Optional<ConstructorAssignmentGenerator<? super S>> constructorArgumentAssigner(StructureMember<S> member, StructureGenerationHelper helper) {
        return a.constructorArgumentAssigner(member, helper).or(() -> b.constructorArgumentAssigner(member, helper));
    }

    @Override
    public <S extends Shape> Optional<ConstructorArgumentGenerator<S>> constructorArgumentGeneratorFor(StructureMember<S> member, ConstructorKind kind, StructureGenerationHelper helper) {
        return a.constructorArgumentGeneratorFor(member, kind, helper).or(() -> b.constructorArgumentGeneratorFor(member, kind, helper));
    }

    @Override
    public <S extends Shape> Optional<EqualsContributor<? super S>> equalsContributor(StructureGenerationHelper helper, StructureMember<S> member) {
        return a.equalsContributor(helper, member).or(() -> b.equalsContributor(helper, member));
    }

    @Override
    public <S extends Shape> Optional<StructureContributor> fieldGenerator(StructureMember<S> member, StructureGenerationHelper helper) {
        return a.fieldGenerator(member, helper).or(() -> b.fieldGenerator(member, helper));
    }

    @Override
    public <S extends Shape> Optional<StructureContributor> getterGenerator(StructureGenerationHelper helper, StructureMember<S> member) {
        return a.getterGenerator(helper, member).or(() -> b.getterGenerator(helper, member));
    }

    @Override
    public void collectClassDocContributors(StructureGenerationHelper helper, Consumer<? super DocumentationContributor<StructureShape, StructureGenerationHelper>> into) {
        a.collectClassDocContributors(helper, into);
        b.collectClassDocContributors(helper, into);
    }

    @Override
    public void collectConstructorAnnotators(StructureGenerationHelper helper, Consumer<? super ConstructorAnnotator> into, ConstructorKind kind) {
        a.collectConstructorAnnotators(helper, into, kind);
        b.collectConstructorAnnotators(helper, into, kind);
    }

    @Override
    public <S extends Shape> void collectConstructorArgumentAnnotators(StructureMember<S> member,
            StructureGenerationHelper helper, ConstructorKind kind, Consumer<? super ConstructorArgumentAnnotator<? super S>> into) {
        a.collectConstructorArgumentAnnotators(member, helper, kind, into);
        b.collectConstructorArgumentAnnotators(member, helper, kind, into);
    }

    @Override
    public <S extends Shape> void collectConstructorArgumentCheckGenerators(ConstructorKind kind, StructureMember<S> member,
            StructureGenerationHelper helper, Consumer<? super ConstructorArgumentCheckGenerator<? super S>> into) {
        a.collectConstructorArgumentCheckGenerators(kind, member, helper, into);
        b.collectConstructorArgumentCheckGenerators(kind, member, helper, into);
    }

    @Override
    public void collectConstructorDocumentationContributors(StructureGenerationHelper helper, ConstructorKind kind,
            Consumer<? super DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs) {
        a.collectConstructorDocumentationContributors(helper, kind, docs);
        b.collectConstructorDocumentationContributors(helper, kind, docs);
    }

    @Override
    public void collectConstructorGenerators(ConstructorKind kind, StructureGenerationHelper helper,
            List<? extends StructureMember<?>> members, Consumer<? super StructureContributor> into) {
        a.collectConstructorGenerators(kind, helper, members, into);
        b.collectConstructorGenerators(kind, helper, members, into);
    }

    @Override
    public <S extends Shape> void collectEqualsContributors(StructureGenerationHelper helper, StructureMember<S> member, Consumer<? super EqualsContributor<? super S>> into) {
        a.collectEqualsContributors(helper, member, into);
        b.collectEqualsContributors(helper, member, into);
    }

    @Override
    public <S extends Shape> void collectFieldDecorators(StructureMember<S> member, StructureGenerationHelper helper,
            Consumer<? super FieldDecorator<S>> into) {
        a.collectFieldDecorators(member, helper, into);
        b.collectFieldDecorators(member, helper, into);
    }

    @Override
    public <S extends Shape> void collectFieldDocumentationContributors(StructureMember<S> member, StructureGenerationHelper helper,
            Consumer<? super DocumentationContributor<? super S, StructureMember<? extends S>>> into) {
        a.collectFieldDocumentationContributors(member, helper, into);
        b.collectFieldDocumentationContributors(member, helper, into);
    }

    @Override
    public void collectFieldGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        a.collectFieldGenerators(helper, members, into);
        b.collectFieldGenerators(helper, members, into);
    }

    @Override
    public <S extends Shape> void collectGetterDecorators(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super GetterDecorator<? super S>> into) {
        a.collectGetterDecorators(helper, member, into);
        b.collectGetterDecorators(helper, member, into);
    }

    @Override
    public <S extends Shape> void collectGetterDocumentationContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super DocumentationContributor<? super S, StructureMember<? extends S>>> into) {
        a.collectGetterDocumentationContributors(helper, member, into);
        b.collectGetterDocumentationContributors(helper, member, into);
    }

    @Override
    public void collectGetterGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        a.collectGetterGenerators(helper, members, into);
        b.collectGetterGenerators(helper, members, into);
    }

    @Override
    public <S extends Shape> void collectHashCodeContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super HashCodeContributor<? super S>> contributors) {
        a.collectHashCodeContributors(helper, member, contributors);
        b.collectHashCodeContributors(helper, member, contributors);
    }

    @Override
    public <S extends Shape> void collectOtherContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super StructureContributor> into) {
        a.collectOtherContributors(helper, member, into);
        b.collectOtherContributors(helper, member, into);
    }

    @Override
    public void collectOtherGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        a.collectOtherGenerators(helper, members, into);
        b.collectOtherGenerators(helper, members, into);
    }

    @Override
    public <S extends Shape> void collectToStringContributors(StructureMember<S> shape, StructureGenerationHelper helper,
            Consumer<? super ToStringContributor<? super S>> into) {
        a.collectToStringContributors(shape, helper, into);
        b.collectToStringContributors(shape, helper, into);
    }

    @Override
    public void collectConstructorGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        a.collectConstructorGenerators(helper, members, into);
        b.collectConstructorGenerators(helper, members, into);
    }

    @Override
    public void collectConstructorKinds(StructureGenerationHelper structureOwner, Consumer<? super ConstructorKind> into) {
        a.collectConstructorKinds(structureOwner, into);
        b.collectConstructorKinds(structureOwner, into);
    }

    @Override
    public void collectHeadToStringContributionWrappers(StructureGenerationHelper structureOwner, Consumer<? super HeadTailToStringContributor> into) {
        a.collectHeadToStringContributionWrappers(structureOwner, into);
        b.collectHeadToStringContributionWrappers(structureOwner, into);
    }

    @Override
    public void collectTailToStringContributionWrappers(StructureGenerationHelper structureOwner, Consumer<? super HeadTailToStringContributor> into) {
        a.collectTailToStringContributionWrappers(structureOwner, into);
        b.collectTailToStringContributionWrappers(structureOwner, into);
    }
}
