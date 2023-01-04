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

import com.telenav.smithy.ts.vogon.TypescriptSource;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
class NumberStringAndBooleanStrategy extends AbstractTypeStrategy<Shape> {

    // This is only for objects which are wrappers for numbers, strings or booleans
    NumberStringAndBooleanStrategy(Shape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String targetType = targetType() + (rawVar.optional() ? " | undefined" : "");
        TypescriptSource.Assignment<B> decl = (declare ? bb.declare(instantiatedVar) : bb.assign(instantiatedVar)).ofType(targetType);
        if (rawVar.optional()) {
            decl.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").instantiate(nb -> nb.withArgument(rawVar.name()).ofType(targetType()));
        } else {
            decl.assignedToNew().withArgument(rawVar.name()).ofType(targetType());
        }
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withUndefinedIfUndefinedOr(rawVar.name()).instantiate().withArgument(rawVar.name()).ofType(targetType());
        } else {
            inv.withNew().withArgument(rawVar.name()).ofType(targetType());
        }
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String varType = rawVar.optional() ? (rawVarType() + " | undefined") : rawVarType().typeName();
        TypescriptSource.Assignment<B> decl = declare ? bb.declare(instantiatedVar).ofType(varType) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            decl.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("toJSON").on(rawVar.name());
        } else {
            decl.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.NUMBER;
    }

    @Override
    public String targetType() {
        if (TypeStrategies.isNotUserType(shape)) {
            return rawVarType().typeName();
        }
        return strategies.tsTypeName(shape);
    }

}
