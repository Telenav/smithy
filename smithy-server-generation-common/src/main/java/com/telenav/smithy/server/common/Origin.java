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
package com.telenav.smithy.server.common;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Knows how to generate the code that extracts one input structure member from
 * some specific part of the HTTP request, such as the headers, query string,
 * host name or http payload.
 *
 * @author Tim Boudreau
 */
public abstract class Origin {

    protected final OriginType type;

    /**
     * Construct a new origin.
     *
     * @param type The type of origin (such as headers, or uri path) the
     * parameter this class generates code to obtain obtains its input from
     */
    public Origin(OriginType type) {
        this.type = type;
    }

    public OriginType type() {
        return type;
    }

    /**
     * For generating documentation, the way in which the member is looked up.
     *
     * @return A string
     */
    public abstract String qualifier();

    /**
     * Import any types the generated code will use.
     *
     * @param cb A class builder
     * @param forShape The shape this Origin is building for
     */
    protected void decorateClass(ClassBuilder<?> cb, Shape forShape) {
    }

    /**
     * Called to collect type names that will be used by the generated code.
     *
     * @param typeNames A consumer for fully qualified type names
     */
    protected void requiredArguments(Consumer<String> typeNames) {
    }

    /**
     * Generates the code that obtains the value from the part of the http
     * request described by the OriginType.
     *
     * @param <B> The block builder type - parameterizing on this allows this
     * method to be called with a constructor body, method body, if-block or
     * lambda block.
     * @param <T> The return type on closing the block builder
     * @param cb The class builder, in case fields or additional methods or
     * other decorations need to be added to it.
     * @param bb The code-block into which the code should be generated
     * @param forShape The target shape we are generating for
     * @param member The member shape that owns the target, within the input
     * shape
     * @return The name of the generated variable.
     */
    protected abstract <B extends BlockBuilderBase<T, B, ?>, T> String collectRawValue(
            ClassBuilder<?> cb, B bb, Shape forShape, MemberShape member);

    /**
     * Add any constructor arguments or annotations.
     *
     * @param con The constructor
     * @param typesAdded A set of qualified type names added as input parameters
     * - in some cases, such as the request itself, the same type may be needed
     * by multiple code generators, but should only appear once in the input
     * parameters, so this set is used to de-duplicate those
     */
    protected void decorateConstructor(ConstructorBuilder<?> con, Set<String> typesAdded) {
    }

    /**
     * Collect any types that need to be bound by guice so they can be passed
     * between request handlers in the request scope.
     *
     * @param importing Accepts qualified class names to add as imports
     * @param binding Accepts qualified class names to add bindings for
     */
    protected void collectBoundTypes(Consumer<String> importing, Consumer<String> binding) {
    }

}
