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

import com.telenav.smithy.generators.SettingsKey;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentCheckGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAssignmentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.EqualsContributor;
import com.telenav.smithy.java.generators.builtin.struct.FieldDecorator;
import com.telenav.smithy.java.generators.builtin.struct.GetterDecorator;
import com.telenav.smithy.java.generators.builtin.struct.HashCodeContributor;
import com.telenav.smithy.java.generators.builtin.struct.HeadTailToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.ToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import static java.util.Optional.empty;
import java.util.function.Consumer;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * API to allow other libraries to extend the way structures are generated,
 * adding annotations, fields, methods, or wholesale replacing the way things
 * are generated. These are loaded via ServiceLoader, and backstopped by a
 * default implementation provided by this library.
 * <p>
 * The methods which return an Optional allow for wholesale overriding of the
 * way a class element is generated; the <code>collect*</code> methods allow for
 * <i>adding</i> to sets of things that contribute to what the default
 * generators generate. So, for example, if you wanted to add just an annotation
 * to certain constructor arguments of type FLOAT, you would do something like:
 * </p>
 * <pre>
 * &#064;ServiceProvider(StructureExtensions.class)
 * public final class MyExtensions {
 *   public &lt;S extends Shape&gt; void collectConstructorArgumentAnnotators(
 *       StructureMember&lt;S&gt; member, StructureGenerationHelper helper,
 * ConstructorKind kind,
 * List&lt;? super ConstructorArgumentAnnotator&lt;? super S&gt;&gt; into) {
 *     if (member.target().shapeType() == ShapeType.FLOAT) {
 *       into.add(new MyAnnotator());
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * The ServiceProvider annotation generates a <code>META-INF/services</code>
 * registration file for your type, and it will simply be picked up and used.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface StructureExtensions {

    /**
     * During a generation run, the value of this key in the generation context
     * (obtainable from the StructureGenerationHelper passed to all methods)
     * will be set to the set of installed extensions.
     */
    public static SettingsKey<StructureExtensions> KEY
            = SettingsKey.key(StructureExtensions.class, "extensions");

    /**
     * Define a precedence order in which extensions are applied - extensions
     * that want to override others need a lower value. The default value is
     * zero.
     * <p>
     * The built-in structure extensions will <i>always</i> come last.
     * </p>
     *
     * @return zero by default
     */
    default int precedence() {
        return 0;
    }

    default StructureExtensions andThen(StructureExtensions another) {
        if (another == null) {
            return this;
        }
        if (another == this || another.getClass() == getClass()) {
            return this;
        }
        return new StructureExtensionsAggregate(this, another);
    }

    default StructureExtensions precededBy(StructureExtensions another) {
        if (another == null) {
            return this;
        }
        if (another == this || another.getClass() == getClass()) {
            return this;
        }
        return new StructureExtensionsAggregate(another, this);
    }

    default StructureExtensions precededBy(Iterable<? extends StructureExtensions> discovered) {
        StructureExtensions result = null;
        Iterator<? extends StructureExtensions> it = discovered.iterator();
        while (it.hasNext()) {
            StructureExtensions e = it.next();
            if (result == null) {
                result = e;
            } else {
                result = result.andThen(e);
            }
        }
        return result == null ? this : precededBy(result);
    }

    /**
     * Return a non-empty value to override the creation of class javadoc for
     * any subsequent extensions.
     *
     * @return An optional
     */
    default Optional<StructureContributor> classDocCreator(StructureGenerationHelper helper) {
        return empty();
    }

    /**
     * Return a non-empty value to override the generation of a default value
     * field for any subsequent extensions.
     *
     * @return An optional
     */
    default Optional<StructureContributor> defaultInstanceFieldGenerator(StructureGenerationHelper helper) {
        return empty();
    }

    /**
     * Return a non-empty value to override the generation of the equals()
     * method for any subsequent extensions.
     *
     * @param structureOwner The structure
     * @return An optional
     */
    default Optional<StructureContributor> equalsGenerator(StructureGenerationHelper structureOwner) {
        return empty();
    }

    /**
     * Return a non-empty value to override the generation of the equals()
     * method for any subsequent extensions.
     *
     * @param helper the structure
     * @param members the members of the structure
     * @return an optional
     */
    default Optional<StructureContributor> hashCodeGenerator(StructureGenerationHelper helper,
            List<? extends StructureMember<?>> members) {
        return empty();
    }

    /**
     * Return a non-empty value to override the generation of the toString()
     * method for any subsequent extensions.
     *
     * @param helper the structure
     * @return An optional
     */
    default Optional<StructureContributor> toStringGenerator(StructureGenerationHelper helper) {
        return empty();
    }

    /**
     * Return a non-empty value to override the generation of <b>all</b>
     * constructor assignment statements for a structure type.
     *
     * @param helper the structure
     * @return An optional
     */
    default <S extends Shape> Optional<ConstructorAssignmentGenerator<? super S>>
            constructorArgumentAssigner(
                    StructureMember<S> member,
                    StructureGenerationHelper helper) {
        return empty();
    }

    /**
     * Return a non-empty value to override the generation of the assignment of
     * a specific structure member.
     *
     * @param helper the structure
     * @return An optional
     */
    default <S extends Shape> Optional<ConstructorArgumentGenerator<S>> constructorArgumentGeneratorFor(StructureMember<S> member,
            ConstructorKind kind, StructureGenerationHelper helper) {
        return empty();
    }

    /**
     * Return a non-empty value to override the way the lines pertaining to a
     * specific structure member are generated within the equals() method of the
     * class representing the passed structure.
     * <p>
     * To simply add to, rather than replace, the way a member is compared for
     * purposes of equality tests, implement
     * <code>collectEqualsContributors</code> instead; to wholesale replace the
     * way the equals method is generated, implement
     * <code>equalsGenerator</code>.
     * </p>
     *
     * @param helper the structure
     * @return An optional
     */
    default <S extends Shape> Optional<EqualsContributor<? super S>> equalsContributor(StructureGenerationHelper helper,
                                                                                       StructureMember<S> member) {
        return empty();
    }

    /**
     * Return a non-empty value to override the way the field pertaining to a
     * specific class member is generated.
     *
     * @param helper the structure
     * @return An optional
     */
    default <S extends Shape> Optional<StructureContributor> fieldGenerator(StructureMember<S> member,
            StructureGenerationHelper helper) {
        return empty();
    }

    /**
     * Return a non-empty value to override the way the getter method is
     * generated for a specific structure member (e.g. returning Optional for
     * non-required fields, using a boxed type or unboxed one, etc.).
     *
     * @param helper the structure
     * @return An optional
     */
    default <S extends Shape> Optional<StructureContributor> getterGenerator(StructureGenerationHelper helper,
            StructureMember<S> member) {
        return empty();
    }

    /**
     * Collect all of the things that want to generate constructors for the
     * passed structure.
     *
     * @param helper The structure
     * @param members Its members
     * @param into A consumer to add code generators to
     */
    default void collectConstructorGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        // do nothing
    }

    /**
     * Pass any DocumentationContributors this StructureExtensions wants to have
     * contribute to class javadoc to the passed consumer.
     *
     * @param helper The structure
     * @param into A consumer
     */
    default void collectClassDocContributors(StructureGenerationHelper helper,
            Consumer<? super DocumentationContributor<StructureShape, StructureGenerationHelper>> into) {
        // do nothing
    }

    /**
     * Pass any ConstructorAnnotators that should be called to create
     * annotations on a particular constructor of the generated type.
     *
     * @param helper The structure
     * @param into A consumer
     * @param kind The specific type of constructor this call pertains to
     */
    default void collectConstructorAnnotators(StructureGenerationHelper helper,
            Consumer<? super ConstructorAnnotator> into, ConstructorKind kind) {
        // do nothing
    }

    /**
     * Pass any ConstructorArgumentAnnotators that need to add annotations to
     * the constructor argument for this member in the specific kind of
     * constructor currently being generated.
     *
     * @param <S> The shape type
     * @param member A structure member
     * @param helper The structure
     * @param kind The kind of constructor
     * @param into A consumer
     */
    default <S extends Shape> void collectConstructorArgumentAnnotators(StructureMember<S> member,
            StructureGenerationHelper helper, ConstructorKind kind, Consumer<? super ConstructorArgumentAnnotator<? super S>> into) {
        // do nothing
    }

    /**
     * Pass any ConstructorArgumentCheckGenerators that should generate tests of
     * the value passed to a constructor of the given kind for the argument
     * corresponding to the passed structure member.
     *
     * @param <S> The shape type
     * @param kind The kind of constructor
     * @param member The structure member
     * @param helper The structure
     * @param into a consumer
     */
    default <S extends Shape> void collectConstructorArgumentCheckGenerators(ConstructorKind kind, StructureMember<S> member,
            StructureGenerationHelper helper, Consumer<? super ConstructorArgumentCheckGenerator<? super S>> into) {
        // do nothing
    }

    /**
     * Pass any DocumentationContributors that should contribute to the
     * documentation of the particular constructor kind for the passed
     * structure.
     *
     * @param helper A structure
     * @param kind The kind of constructor being generated
     * @param docs A consumer
     */
    default void collectConstructorDocumentationContributors(StructureGenerationHelper helper, ConstructorKind kind,
            Consumer<? super DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs) {
        // do nothing
    }

    /**
     * Collect all constructor generators that will create a constructor for a
     * give structure.
     *
     * @param kind The kind of constructor
     * @param helper The structure
     * @param members Its members
     * @param into A consumer
     */
    default void collectConstructorGenerators(ConstructorKind kind, StructureGenerationHelper helper,
            List<? extends StructureMember<?>> members, Consumer<? super StructureContributor> into) {
        // do nothing
    }

    /**
     * Pass any EqualsContributor instances that should do an equality test for
     * the given member to the passed consumer.
     *
     * @param <S> The member type
     * @param helper The structure
     * @param member The member
     * @param into A consumer
     */
    default <S extends Shape> void collectEqualsContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super EqualsContributor<? super S>> into) {
        // do nothing
    }

    /**
     * Pass any FieldDecorators that should annotate or otherwise modify the
     * field for the given member.
     *
     * @param <S> The shape type
     * @param member A structure member
     * @param helper A structure
     * @param into A consumer
     */
    default <S extends Shape> void collectFieldDecorators(StructureMember<S> member, StructureGenerationHelper helper,
            Consumer<? super FieldDecorator<S>> into) {
        // do nothing
    }

    default <S extends Shape> void collectFieldDocumentationContributors(StructureMember<S> member,
            StructureGenerationHelper helper, Consumer<? super DocumentationContributor<? super S, StructureMember<? extends S>>> into) {
        // do nothing
    }

    default void collectFieldGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        // do nothing
    }

    default <S extends Shape> void collectGetterDecorators(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super GetterDecorator<? super S>> into) {
        // do nothing
    }

    default <S extends Shape> void collectGetterDocumentationContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super DocumentationContributor<? super S, StructureMember<? extends S>>> into) {
        // do nothing
    }

    default void collectGetterGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        // do nothing
    }

    default <S extends Shape> void collectHashCodeContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super HashCodeContributor<? super S>> contributors) {
        // do nothing
    }

    default <S extends Shape> void collectOtherContributors(StructureGenerationHelper helper, StructureMember<S> member,
            Consumer<? super StructureContributor> into) {
        // do nothing
    }

    default void collectOtherGenerators(StructureGenerationHelper helper, List<? extends StructureMember<?>> members,
            Consumer<? super StructureContributor> into) {
        // do nothing
    }

    default <S extends Shape> void collectToStringContributors(StructureMember<S> shape, StructureGenerationHelper helper,
            Consumer<? super ToStringContributor<? super S>> into) {
        // do nothing
    }

    default void collectConstructorKinds(StructureGenerationHelper structureOwner, Consumer<? super ConstructorKind> into) {

    }

    /**
     * Collect contributors that want to prepend to the toString() value.
     *
     * @param structureOwner The structure
     * @param into A consumer
     */
    default void collectHeadToStringContributionWrappers(StructureGenerationHelper structureOwner, Consumer<? super HeadTailToStringContributor> into) {

    }

    default void collectTailToStringContributionWrappers(StructureGenerationHelper structureOwner, Consumer<? super HeadTailToStringContributor> into) {

    }
}
