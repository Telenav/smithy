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

import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public enum ConstructorKind {

    JSON_DESERIALIZATON,
    SECONDARY_WITH_PRIMITIVES,
    SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES;

    public boolean isPrimary() {
        return this == JSON_DESERIALIZATON;
    }

    public boolean preferPrimitiveTypesForDefaultedValues() {
        return !isPrimary();
    }

    public List<StructureMember<?>> memberList(StructureGenerationHelper helper) {
        switch (this) {
            case SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES:
                return helper.membersSortedByName();
            default:
                return helper.members();
        }
    }
}
