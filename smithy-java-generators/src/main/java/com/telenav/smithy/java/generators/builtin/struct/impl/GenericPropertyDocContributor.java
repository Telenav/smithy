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

import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class GenericPropertyDocContributor<S extends Shape> implements DocumentationContributor<S, StructureMember<? extends S>> {

    @Override
    public void generateDocumentation(StructureMember<? extends S> member, StringBuilder docHead, StringBuilder docTail) {
        boolean opt = !member.isRequired() && !member.hasDefault();
        docTail.append("Returns the value of the");
        if (opt) {
            docTail.append(" <i>optional</i>");
        }
        docTail.append(" property <code>").append(member.jsonName()).append("</code> defined in the Smithy spec as <code>").append(member.member().getId()).append("</code> of type <code>").append(member.target().getId()).append("</code>.");
        if (opt) {
            docTail.append("\n@return an {@link java.util.Optional} containing the value of ").append(member.jsonName()).append(" if a value is present");
        } else {
            docTail.append("\n@return the value of ").append(member.jsonName());
        }
    }

}
