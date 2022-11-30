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
package com.mastfrog.smithy.server.common;

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
