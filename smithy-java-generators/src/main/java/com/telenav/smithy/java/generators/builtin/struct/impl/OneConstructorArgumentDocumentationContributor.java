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

import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.extensions.SpanTrait;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
final class OneConstructorArgumentDocumentationContributor<S extends Shape> implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    private final StructureMember<S> member;
    private final ConstructorKind kind;

    OneConstructorArgumentDocumentationContributor(StructureMember<S> member, ConstructorKind kind) {
        this.member = member;
        this.kind = kind;
    }

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        docTail.append("\n@param ").append(member.arg()).append(' ');
        if (kind == ConstructorKind.JSON_DESERIALIZATON) {
            member.member().getTrait(DefaultTrait.class).ifPresentOrElse(def -> {
                docTail.append(" - <i>defaulted to ").append(def.toNode().toNode().toString().replaceAll("\n", "\\n")).append(" if null</i>. ");
            }, () -> {
                if (member.isRequired()) {
                    docTail.append(" - <i>required</i> - ");
                } else {
                    docTail.append(" - <i>may be null</i> - ");
                }
            });
            target.structure().getTrait(SpanTrait.class).ifPresent(span -> {
                if (member.member().getMemberName().equals(span.lesser())) {
                    String cmp = span.emptyAllowed() ? "&lt;=" : "&lt;";
                    docTail.append(" Must be <code>").append(cmp).append(span.greater())
                            .append("</code>. ");
                } else if (member.member().getMemberName().equals(span.greater())) {
                    String cmp = span.emptyAllowed() ? "&gt;=" : "&gt;";
                    docTail.append(" Must be <code>").append(cmp).append(span.lesser())
                            .append("</code>. ");
                }
            });
        }
        member.member().getTrait(DocumentationTrait.class).or(() -> member.target().getTrait(DocumentationTrait.class)).ifPresentOrElse(dox -> docTail.append(dox.getValue()), () -> docTail.append("a ").append(member.typeName()));
    }

}
