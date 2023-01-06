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

import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.names.TypeNames.simpleNameOf;
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
        return "_" + decapitalize(simpleNameOf(qualifiedType));
    }

    @Override
    protected void decorateConstructor(ClassBuilder.ConstructorBuilder<?> con, Set<String> typesAdded) {
        if (!typesAdded.add(qualifiedType)) {
            con.addArgument(simpleNameOf(qualifiedType), varName());
        }
    }

    @Override
    protected <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> String collectRawValue(ClassBuilder<?> cb, B bb, Shape forShape, MemberShape member) {
        bb.lineComment(getClass().getSimpleName() + " " + forShape + " " + forShape.getId() + " for " + (member == null ? "null" : member.getId()));
        // It's a constructor argument, no code needed
        return varName();
    }

}
