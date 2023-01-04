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

import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractMapStrategy extends AbstractTypeStrategy<MapShape> {

    protected final Shape keyShape;
    protected final Shape valueShape;
    protected final TypeStrategy<?> keyStrategy;
    protected final TypeStrategy<?> valueStrategy;

    protected AbstractMapStrategy(MapShape shape, TypeStrategies strategies,
            Shape keyShape, Shape valueShape) {
        super(shape, strategies);
        this.keyShape = keyShape;
        this.valueShape = valueShape;
        keyStrategy = strategies.strategy(keyShape);
        valueStrategy = strategies.strategy(valueShape);
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.OBJECT;
    }

}
