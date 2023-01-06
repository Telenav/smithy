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
package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.StringConcatenationBuilder;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Contributes code to render a string representation of a field to the string
 * concatenation returned by the toString() method of a toString() method on a
 * structure.
 *
 * @author Tim Boudreau
 */
public interface ToStringContributor<S extends Shape> {

    <T> void contributeToToString(
            StructureMember<? extends S> member,
            StructureGenerationHelper helper,
            ClassBuilder<?> cb,
            StringConcatenationBuilder<T> concat,
            boolean hasFollowingMembers
    );

    static ToStringContributor<Shape> DEFAULT
            = new ToStringContributor<Shape>() {

        @Override
        public <T> void contributeToToString(StructureMember<? extends Shape> member,
                StructureGenerationHelper helper, ClassBuilder<?> cb, StringConcatenationBuilder<T> concat,
                boolean hasFollowingMembers) {
            concat.append("\"")
                    .append(member.jsonName())
                    .append("\":");
            if (member.isSmithyApiDefinedType()) {
                switch (member.target().getType()) {
                    case STRING:
                    case TIMESTAMP:
                    case ENUM:
                        concat.append("\"");
                        concat.appendField(member.field()).ofThis();
                        concat.append("\"");
                        break;
                    default:
                        concat.appendField(member.field()).ofThis();
                }
            } else {
                switch (member.target().getType()) {
                    case INT_ENUM:
                        concat.appendInvocationOf("getAsInt").onField(member.field()).ofThis();
                        break;
                    case STRING:
                    case TIMESTAMP:
                    case ENUM:
                        concat.append("\"").appendField(member.field()).ofThis().append("\"");
                        break;
                    default:
                        concat.appendField(member.field()).ofThis();
                }

            }
            if (hasFollowingMembers) {
                concat.append(",");
            }
        }
    };

}
