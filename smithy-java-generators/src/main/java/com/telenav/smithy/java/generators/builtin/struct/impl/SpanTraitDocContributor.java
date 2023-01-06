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

import com.telenav.smithy.java.generators.builtin.SpanUtils;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
final class SpanTraitDocContributor implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    static SpanTraitDocContributor INSTANCE = new SpanTraitDocContributor();

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        SpanUtils.withSpanArguments(target, (lesserMemberName, greaterMemberName, emptyAllowed) -> {
            docTail.append("This type represents a <i>span</i> of the values from <i><code>")
                    .append(lesserMemberName)
                    .append("</code></i> and to <i><code>")
                    .append(greaterMemberName)
                    .append("</code></i>.  If constructed with arguments where <code>")
                    .append(lesserMemberName)
                    .append(emptyAllowed ? " &gt; " : " &gt;= ")
                    .append(greaterMemberName)
                    .append("</code>, then the constructor will throw <code>")
                    .append(target.validation().name())
                    .append("</code>.");
            docTail.append("\nInstances where <code>")
                    .append(lesserMemberName)
                    .append(" == ").append(greaterMemberName)
                    .append("</code> are <b>");
            if (!emptyAllowed) {
                docTail.append("not ");
            }
            docTail.append("permitted</b>.");
            return null;
        });
    }

}
