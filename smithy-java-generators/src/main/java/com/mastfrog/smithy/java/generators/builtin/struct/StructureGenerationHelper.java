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
package com.mastfrog.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.java.generators.builtin.ValidationExceptionProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Wraps the underlying structure we are generating code for, and provides some
 * convenience methods for code generation and accessing the settings for the
 * current generation session.
 *
 * @author Tim Boudreau
 */
public interface StructureGenerationHelper extends Iterable<StructureMember<?>> {

    /**
     * Convenience method: Generates a throwing null-check of the passed
     * variable (or method call or whatever) name.
     *
     * @param <T>
     * @param variable The name of a variable
     * @param bb The code block to generate code into
     * @param on A class builder (in case imports need to be added)
     */
    <T> void generateNullCheck(String variable,
            BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on);

    /**
     * Convenience method: Generates an equality test of two values that may be
     * null, returning false if they are not equal.
     *
     * @param <T> A type
     * @param variable The name of a variable
     * @param compareWith The argument value in the equality test
     * @param bldr The code block to generate code into
     */
    <B extends BlockBuilderBase<T, B, ?>, T> void generateEqualityCheckOfNullable(
            String variable, String compareWith, B bldr);

    /**
     * Get the thing which is supplying class, field, member, method and
     * argument names for this session.
     *
     * @return A Namer
     */
    Namer namer();

    /**
     * Get the smithy model all elements code is dealing with belong to.
     *
     * @return A model
     */
    Model model();

    /**
     * Get the structure we are generating code fore.
     *
     * @return A StructureShape, never null
     */
    StructureShape structure();

    /**
     * Access the context and settings for this code-generation run.
     *
     * @return A context
     */
    SmithyGenerationContext context();

    /**
     * Get the structure class members, wrapped in StructureMember, which can
     * compute names and answer useful questions about the member.
     *
     * @return A list of members
     */
    List<StructureMember<?>> members();

    /**
     * Get the (pluggable) validation exceptions provider - this is used to
     * ensure that exceptions thrown due to bad input throw a specific type of
     * exception that generated code may expect in order to produce the right
     * error response.
     *
     * @return A ValidationExceptionProvider
     */
    ValidationExceptionProvider validation();

    /**
     * Generate the equals test that should appear at the head of almost all
     * implementations of <code>Object.equals()</code> to provide two fast paths
     * to exit: <code>if (o == this) { return true; }
     * else if (o == null || o.getClass() != SomeTargetClass.class) {
     * return false; } SomeTargetClass other = (SomeTargetClass) o;</code>.
     *
     * @param <T> The type the builder is paramterized on
     * @param <R> The type the class builder is parameterized on
     * @param cb A class builder, in case imports need to be added
     * @param bb The code block the test should be added to
     * @return The name of the variable that contains the cast argument, which
     * is guaranteed not to conflict with fields on this type
     */
    <T, R> String generateInitialEqualsTest(ClassBuilder<R> cb, BlockBuilder<T> bb);


    boolean isOmitted(MemberShape shape);
    /**
     * Add imports to a class builder, only if they are not in the
     * <code>java.lang</code> package and not in the same package as the
     * structure being generated.
     *
     * @param cb A class builder
     * @param fqns Some fully qualified class names to import
     */
    void maybeImport(ClassBuilder<?> cb, String... fqns);

    @Override
    default Iterator<StructureMember<?>> iterator() {
        return members().iterator();
    }

    /**
     * Gets the list of class members, sorted by their estimated <i>weight</i> -
     * implementations of <code>equals()</code> should use this to perform the
     * fastest tests first.
     *
     * @return The list of members, from least to greatest weight
     */
    default List<StructureMember<?>> membersSortedByWeight() {
        List<StructureMember<?>> result = new ArrayList<>(members());
        Collections.sort(result);
        return result;
    }

    /**
     * Get the list of members sorted by name - often preferable for consistent
     * toString() implementations.
     *
     * @return A list of members
     */
    default List<StructureMember<?>> membersSortedByName() {
        List<StructureMember<?>> result = new ArrayList<>(members());
        result.sort((a, b) -> {
            return a.jsonName().compareTo(b.jsonName());
        });
        return result;
    }

    /**
     * Get the settings for this code generation run.
     *
     * @return A settings
     */
    default SmithyGenerationSettings settings() {
        return context().settings();
    }

    /**
     * Convenience test if debug settings are in play, in which case code
     * generators may want to emit line comments to detail what is responsible
     * for what.
     *
     * @return True if this session is in debug mode
     */
    default boolean isDebug() {
        return settings().is(DEBUG);
    }

}
