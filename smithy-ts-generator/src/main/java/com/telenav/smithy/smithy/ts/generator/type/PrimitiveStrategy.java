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

import static com.telenav.smithy.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveStrategy<S extends Shape> extends AbstractTypeStrategy<S> {

    PrimitiveStrategy(S shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb,
                                                                                     TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(targetType() + " | undefined").assignedTo(rawVar.name());
        } else {
            assig.ofType(targetType()).assignedTo(rawVar.name());
        }
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void
            instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        inv.withArgument(rawVar.name() + " as " + targetType());
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb,
                                                                               TsVariable rawVar, String instantiatedVar, boolean declare) {
        (declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar)).ofType(rawVar.optional() ? rawVarType().asOptional().returnTypeSignature() : rawVarType().typeName()).assignedTo(rawVar.name());
    }

    @Override
    public TsSimpleType rawVarType() {
        return bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return rawVarType().typeName();
    }

}
