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
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import com.telenav.smithy.names.TypeNames;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Closes the declaration of a variable, assigning it a type.
 *
 * @author Tim Boudreau
 */
public interface DeclarationClose<B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends InvocationBuilderBase<TypeAssignment<B>, Ir>> {

    B closing(Ir ir, ClassBuilder<?> cb);

    /**
     * Wrap a DeclarationClose in one that uses the passed name as the value of
     * toString(), so we can generate debug lines that show the provenance of
     * something into line comments and get a meaningful value.
     *
     * @param <B> The block builder type
     * @param <Tr> The block builder return type
     * @param <Rr> The block builder owner type
     * @param <Ir> An invocation builder or new builder or similar
     * @param nm A name
     * @param cl The original instance
     * @return A wrapper DeclarationClose
     */
    static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends InvocationBuilderBase<TypeAssignment<B>, Ir>>
            DeclarationClose<B, Tr, Rr, Ir> named(String nm, DeclarationClose<B, Tr, Rr, Ir> cl) {
        return new DeclarationClose<B, Tr, Rr, Ir>() {
            @Override
            public B closing(Ir ir, ClassBuilder<?> cb) {
                return cl.closing(ir, cb);
            }

            @Override
            public String toString() {
                return nm;
            }
        };
    }

    public static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr> DeclarationClose<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>>
            onRequest(String as) {
        return named("onRequest " + as, (ir, cb) -> ir.on("smithyRequest").as(as));
    }

    public static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr> DeclarationClose<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>>
            onAs(String as) {
        return named("onAs " + as, (ir, cb) -> ir.on(as).as(as));
    }

    public static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr> DeclarationClose<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>>
            on(String on, String as) {
        return named("on " + on + " as " + as, (ir, cb) -> ir.on(on).as(as));
    }

    public static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr> DeclarationClose<B, Tr, Rr, ClassBuilder.NewBuilder<ClassBuilder.TypeAssignment<B>>>
            forNew(Shape shape, Model model) {
        TypeNames tn = new TypeNames(model);
        return named("forNew " + shape.getId(), (ir, cb) -> {
            String nm = tn.typeNameOf(cb, shape, true);
            return ir.ofType(nm).as(nm);
        });
    }

    public static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr> DeclarationClose<B, Tr, Rr, ClassBuilder.NewBuilder<ClassBuilder.TypeAssignment<B>>>
            forPrimitive(Shape shape, Model model) {
        return named("forPrimitive " + shape.getId(), (ir, cb) -> {
            TypeNames tn = new TypeNames(model);
            String nm = tn.typeNameOf(cb, shape, false);
            return ir.ofType(nm).as(nm);
        });
    }

}
