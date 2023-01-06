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
