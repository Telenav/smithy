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
package com.telenav.smithy.ts.generator.type;

import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.util.EnumSet;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Set of primitive types which have equivalents in javascript types (with the
 * exception of "any" which is a catch-all).
 *
 * @author Tim Boudreau
 */
public enum TsPrimitiveTypes implements TsSimpleType {
    STRING,
    NUMBER,
    BIGINT,
    BOOLEAN,
    OBJECT,
    ANY,
    STRING_ARRAY,
    NUMBER_ARRAY,
    BIGINT_ARRAY,
    OBJECT_ARRAY,
    BOOLEAN_ARRAY,
    ANY_ARRAY;

    public static TsPrimitiveTypes bestMatch(Model model, Shape shape) {
        switch (shape.getType()) {
            case MEMBER:
                return bestMatch(model, model.expectShape(shape.asMemberShape().get().getTarget()));
            case STRUCTURE:
                return OBJECT;
            case STRING:
                return STRING;
            case BOOLEAN:
                return BOOLEAN;
            case DOCUMENT:
            case MAP:
                return OBJECT;
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case LONG:
            case SHORT:
            case INTEGER:
            case BYTE:
            case FLOAT:
            case DOUBLE:
                return NUMBER;
            case LIST:
            case SET:
                Shape listMember = model.expectShape(shape.asListShape().get().getMember().getTarget());
                return bestMatch(model, listMember).asArray();
            case BLOB:
                return NUMBER_ARRAY;
            case INT_ENUM:
                return NUMBER;
            case ENUM:
                switch (characterizeEnum(shape.asEnumShape().get())) {
                    case INT_VALUED:
                        return NUMBER;
                    case STRING_VALUED_MATCHING_NAMES:
                        return STRING;
                    default:
                        return STRING;
                }
            case TIMESTAMP:
                return STRING;
            case UNION:
                Set<TsPrimitiveTypes> memberTypes = EnumSet.noneOf(TsPrimitiveTypes.class);
                shape.asUnionShape().get().getAllMembers().forEach((name, member) -> {
                    memberTypes.add(bestMatch(model, member));
                });
                if (memberTypes.size() == 1) {
                    return memberTypes.iterator().next();
                }
                return ANY;
        }
        throw new AssertionError(shape.getType() + " is not convertable"
                + " into a typescript type");
    }

    @Override
    public boolean isArray() {
        switch (this) {
            case ANY_ARRAY:
            case STRING_ARRAY:
            case NUMBER_ARRAY:
            case BIGINT_ARRAY:
            case BOOLEAN_ARRAY:
            case OBJECT_ARRAY:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return typeName();
    }

    @Override
    public String argumentSignature() {
        return typeName();
    }

    @Override
    public boolean optional() {
        return false;
    }

    @Override
    public String returnTypeSignature() {
        return typeName();
    }

    @Override
    public String typeName() {
        switch (this) {
            case ANY:
                return "any";
            case ANY_ARRAY:
                return "any[]";
            case BIGINT:
                return "BigInt";
            case BIGINT_ARRAY:
                return "BigInt[]";
            case BOOLEAN:
                return "boolean";
            case BOOLEAN_ARRAY:
                return "boolean[]";
            case NUMBER:
                return "number";
            case NUMBER_ARRAY:
                return "number[]";
            case OBJECT:
                return "object";
            case OBJECT_ARRAY:
                return "object[]";
            case STRING:
                return "string";
            case STRING_ARRAY:
                return "string[]";
            default:
                throw new AssertionError(this);
        }
    }

    @Override
    public TsPrimitiveTypes asArray() {
        switch (this) {
            case ANY:
                return ANY_ARRAY;
            case BOOLEAN:
                return BOOLEAN_ARRAY;
            case NUMBER:
                return NUMBER_ARRAY;
            case OBJECT:
                return OBJECT_ARRAY;
            case STRING:
                return STRING_ARRAY;
            case BIGINT:
                return BIGINT_ARRAY;
        }
        return this;
    }

    @Override
    public TsSimpleType asNonArray() {
        switch (this) {
            case ANY_ARRAY:
                return ANY;
            case BOOLEAN_ARRAY:
                return BOOLEAN;
            case NUMBER_ARRAY:
                return NUMBER;
            case OBJECT_ARRAY:
                return OBJECT;
            case STRING_ARRAY:
                return STRING;
            case BIGINT_ARRAY:
                return BIGINT;
        }
        return this;
    }

    @Override
    public TsSimpleType asNonOptional() {
        return this;
    }

    @Override
    public boolean isAnyType() {
        return this == ANY;
    }
}
