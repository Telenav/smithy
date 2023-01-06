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
package com.telenav.smithy.ts.generator.type;

/**
 *
 * @author Tim Boudreau
 */
final class InputType implements TsSimpleType {

    private final TsSimpleType type;
    private final boolean optional;

    public InputType(TsSimpleType type, boolean optional) {
        this.type = type.optional() ? type.asNonOptional() : type;
        this.optional = optional;
    }

    @Override
    public boolean optional() {
        return optional;
    }

    @Override
    public String typeName() {
        return type.typeName();
    }

    @Override
    public String argumentSignature() {
        if (optional()) {
            return typeName() + "?";
        }
        return typeName();
    }

    @Override
    public String returnTypeSignature() {
        if (optional()) {
            return typeName() + " | undefined";
        }
        return typeName();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public TsSimpleType asArray() {
        if (isArray()) {
            return this;
        }
        return new InputType(type.asArray(), optional);
    }

    @Override
    public TsSimpleType asNonArray() {
        if (isArray()) {
            return new InputType(type.asNonArray(), optional);
        }
        return this;
    }

    @Override
    public TsSimpleType asNonOptional() {
        if (optional) {
            return type.asNonOptional();
        }
        return this;
    }

    @Override
    public boolean isAnyType() {
        return type.isAnyType();
    }

    @Override
    public boolean isSimpleType() {
        return type.isSimpleType();
    }
}
