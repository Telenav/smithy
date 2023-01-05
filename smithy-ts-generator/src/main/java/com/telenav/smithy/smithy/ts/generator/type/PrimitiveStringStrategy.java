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

import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import software.amazon.smithy.model.shapes.StringShape;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveStringStrategy extends AbstractTypeStrategy<StringShape> {

    PrimitiveStringStrategy(StringShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TSBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb,
            TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(targetType() + " | undefined")
                    .assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke("toString").on(rawVar.name());
        } else {
            assig.ofType(targetType()).assignedToInvocationOf("toString").on(rawVar.name());
        }
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void
            instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
//            inv.withArgument("typeof " + rawVar.name() + " === 'undefined' ? undefined : " + rawVar.name() + ".toString(");
            inv.withUndefinedIfUndefinedOr(rawVar.name()).invoke("toString").on(rawVar.name());
        } else {
            inv.withArgumentFromInvoking("toString").on(rawVar.name());
        }
    }

    @Override
    public <T, B extends TSBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb,
            TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        assig.ofType(rawVar.optional() ? rawVarType().asOptional().returnTypeSignature() : rawVarType().typeName());
        assig.assignedTo(rawVar.name() + " as string");
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return rawVarType().typeName();
    }

}
