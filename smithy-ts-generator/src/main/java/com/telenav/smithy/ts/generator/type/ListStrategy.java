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

import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class ListStrategy extends AbstractListOrSetStrategy {

    ListStrategy(ListShape shape, TypeStrategies strategies, Shape member) {
        super(shape, strategies, member, "Array");
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType();
        Assignment<B> decl = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            decl.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke("fromJsonObject")
                    .withArgument(rawVar.name()).on(targetType());
        } else {
            decl.assignedToInvocationOf("fromJsonObject")
                    .withArgument(rawVar.name()).on(targetType());
        }
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void
            instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withUndefinedIfUndefinedOr(rawVar.name())
                    .invoke("fromJsonObject")
                    .withArgument(rawVar.name()).on(targetType());
        } else {
            inv.withInvocationOf("fromJsonObject")
                    .withArgument(rawVar.name()).on(targetType());
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void
            convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.returnTypeSignature();
        TypescriptSource.Assignment<B> decl = declare ? bb.declareConst(instantiatedVar)
                .ofType(type)
                : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            decl.assignedToUndefinedIfUndefinedOr(rawVar.name()).invoke("toJSON").on(rawVar.name());
        } else {
            decl.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }
}
