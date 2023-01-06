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
package com.telenav.smithy.utils;

import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.EnumValueTrait;

/**
 *
 * @author Tim Boudreau
 */
public enum EnumCharacteristics {
    /**
     * The enum has string values associated with the names, and at least some
     * <i>do not match</i> the associated enum constant name.
     */
    STRING_VALUED,
    /**
     * The enum has string values, and all of the names match the string values
     * (in typescript, the enum is expressible as
     * <code>type x = 'item1' | 'item2'</code>, for example).
     */
    STRING_VALUED_MATCHING_NAMES,
    /**
     * The enum has int values for all members.
     */
    INT_VALUED,
    /**
     * The enum has some constants which have values and some which do not.
     */
    HETEROGENOUS,
    /**
     * The enum does not have any values associated with its constants.
     */
    NONE;

    public boolean canBeConst() {
        return this != HETEROGENOUS;
    }

    public static EnumCharacteristics characterizeEnum(EnumShape shape) {
        boolean intValued = false;
        boolean stringValued = false;
        boolean nonValued = false;
        boolean stringUnequalNameAndStringValue = false;
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            Optional<EnumValueTrait> ev = e.getValue().getTrait(EnumValueTrait.class);
            if (ev.isPresent()) {
                EnumValueTrait evt = ev.get();
                if (evt.getIntValue().isPresent()) {
                    intValued = true;
                } else if (evt.getStringValue().isPresent()) {
                    String v = evt.getStringValue().get();
                    if (!v.equals(e.getKey())) {
                        stringUnequalNameAndStringValue = true;
                    }
                    stringValued = true;
                } else {
                    nonValued = false;
                }
            }
        }
        if (nonValued && (intValued || stringValued)) {
            return EnumCharacteristics.HETEROGENOUS;
        }
        if (!nonValued) {
            if (intValued) {
                return EnumCharacteristics.INT_VALUED;
            } else if (stringValued) {
                if (!stringUnequalNameAndStringValue) {
                    return EnumCharacteristics.STRING_VALUED_MATCHING_NAMES;
                }
                return EnumCharacteristics.STRING_VALUED;
            }
        }
        if (nonValued && !(intValued || stringValued)) {
            return EnumCharacteristics.NONE;
        }
        return EnumCharacteristics.HETEROGENOUS;
    }

}
