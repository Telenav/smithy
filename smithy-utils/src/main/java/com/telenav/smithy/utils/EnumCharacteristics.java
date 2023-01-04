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
