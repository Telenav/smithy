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
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.names.TypeNames;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public final class PayloadOrigin extends Origin {

    final String qualifiedType;

    public PayloadOrigin(String qualifiedType) {
        super(OriginType.HTTP_PAYLOAD);
        this.qualifiedType = qualifiedType;
    }

    @Override
    public String qualifier() {
        return "deserialized to " + qualifiedType;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + qualifiedType + ")";
    }

    @Override
    protected void requiredArguments(Consumer<String> typeNames) {
        typeNames.accept(qualifiedType);
    }

    @Override
    protected void collectBoundTypes(Consumer<String> importing, Consumer<String> binding) {
        if (!qualifiedType.startsWith("java.lang") && qualifiedType.contains(".")) {
            importing.accept(qualifiedType);
            binding.accept(qualifiedType);
        }
    }

    @Override
    protected void decorateClass(ClassBuilder<?> cb, Shape shape) {
        cb.importing("com.mastfrog.acteur.preconditions.InjectRequestBodyAs");
//        cb.annotatedWith("InjectRequestBodyAs").addClassArgument("value", OperationGenerator.simpleName(qualifiedType)).closeAnnotation();
    }

    private String varName() {
        return "_" + Strings.decapitalize(TypeNames.simpleNameOf(qualifiedType));
    }

    @Override
    protected void decorateConstructor(ClassBuilder.ConstructorBuilder<?> con, Set<String> typesAdded) {
        if (!typesAdded.add(qualifiedType)) {
            con.addArgument(TypeNames.simpleNameOf(qualifiedType), varName());
        }
    }

    @Override
    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> String collectRawValue(ClassBuilder<?> cb, B bb, Shape forShape, MemberShape member) {
        bb.lineComment(getClass().getSimpleName() + " " + forShape + " " + forShape.getId() + " for " + (member == null ? "null" : member.getId()));
        // It's a constructor argument, no code needed
        return varName();
    }

}
