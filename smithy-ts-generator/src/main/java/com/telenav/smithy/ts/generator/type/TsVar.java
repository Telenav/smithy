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
final class TsVar implements TsVariable {

    private final String name;
    private final TsSimpleType type;

    TsVar(String name, TsSimpleType type) {
        this.name = name;
        this.type = type;
    }

    public TsVar as(TsSimpleType otherType) {
        if (otherType == type) {
            return this;
        }
        return new TsVar(name, otherType);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String argumentSignature() {
        return type.argumentSignature();
    }

    @Override
    public boolean optional() {
        return type.optional();
    }

    @Override
    public String returnTypeSignature() {
        return type.returnTypeSignature();
    }

    @Override
    public String typeName() {
        return type.typeName();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public TsSimpleType asOptional() {
        return type.asOptional();
    }

    @Override
    public TsSimpleType asArray() {
        return type.asArray();
    }

    @Override
    public TsSimpleType asNonArray() {
        return type.asNonArray();
    }

    @Override
    public TsSimpleType asNonOptional() {
        return type.asNonOptional();
    }

    @Override
    public boolean isAnyType() {
        return type.isAnyType();
    }

    @Override
    public String toString() {
        return name();
    }
}
