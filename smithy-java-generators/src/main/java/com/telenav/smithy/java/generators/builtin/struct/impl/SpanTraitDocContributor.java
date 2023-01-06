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
