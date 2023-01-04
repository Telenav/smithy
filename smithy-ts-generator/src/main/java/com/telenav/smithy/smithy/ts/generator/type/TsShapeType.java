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
package com.telenav.smithy.smithy.ts.generator.type;

import software.amazon.smithy.model.shapes.Shape;

/**
 * Wraps a shape as a TsSimpleType.
 *
 * @author Tim Boudreau
 */
public class TsShapeType implements TsSimpleType {

    private final Shape shape;
    private final TsTypeUtils types;
    private final boolean array;
    private final boolean optional;

    public TsShapeType(Shape shape, TsTypeUtils types, boolean array, boolean optional) {
        this.shape = shape;
        this.types = types;
        this.array = array;
        this.optional = optional;
    }

    @Override
    public String argumentSignature() {
        return typeName() + (array ? "[]" : "") + (optional ? "?" : "");
    }

    @Override
    public boolean optional() {
        return optional;
    }

    @Override
    public String returnTypeSignature() {
        return typeName() + (array ? "[]" : "") + (optional ? " | undefined" : "");
    }

    @Override
    public String typeName() {
        if (TsTypeUtils.isNotUserType(shape)) {
            return types.typeNameOf(shape, false);
        }
        return types.tsTypeName(shape);
    }

    @Override
    public boolean isArray() {
        return array;
    }

    @Override
    public TsSimpleType asArray() {
        if (array) {
            return this;
        }
        return new TsShapeType(shape, types, true, false);
    }

    @Override
    public TsSimpleType asNonArray() {
        if (array) {
            return new TsShapeType(shape, types, false, false);
        }
        return this;
    }

    @Override
    public TsSimpleType asNonOptional() {
        if (optional) {
            return new TsShapeType(shape, types, array, false);
        }
        return this;
    }

    @Override
    public boolean isAnyType() {
        return false;
    }

}
