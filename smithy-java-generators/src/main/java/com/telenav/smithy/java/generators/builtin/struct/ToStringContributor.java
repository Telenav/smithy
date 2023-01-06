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
