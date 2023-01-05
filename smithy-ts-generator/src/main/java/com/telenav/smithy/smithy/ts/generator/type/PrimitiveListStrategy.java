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

import static com.telenav.smithy.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Strategies for a list whose type is a typescript primitive type
 *
 * @author Tim Boudreau
 */
final class PrimitiveListStrategy extends AbstractListOrSetStrategy {

    PrimitiveListStrategy(ListShape shape, TypeStrategies strategies, Shape member) {
        super(shape, strategies, member, "Array");
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar,
                    String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType();
        TypescriptSource.Assignment<B> decl = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            TypescriptSource.ExpressionBuilder<B> partial = decl.assignedToTernary("typeof " + rawVar.name() + " === undefined")
                    .expression("undefined");
            partial.ternary("Array.isArray(" + rawVar.name() + ")")
                    .expression(rawVar.name())
                    .expression("[" + rawVar.name() + " as " + memberStrategy.targetType() + "]");
        } else {
            decl.assignedToTernary("Array.isArray(" + rawVar.name() + ")")
                    .expression(rawVar.name())
                    .expression("[" + rawVar.name() + " as " + memberStrategy.targetType() + "]");
        }
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>>
            void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            TypescriptSource.ExpressionBuilder<B> partial = inv.withTernary("typeof " + rawVar.name() + " === undefined")
                    .expression("undefined");
            partial.ternary("Array.isArray(" + rawVar.name() + ")")
                    .expression(rawVar.name())
                    .expression("[" + rawVar.name() + " as " + memberStrategy.targetType() + "]");
        } else {
            inv.withTernary("Array.isArray(" + rawVar.name() + ")")
                    .expression(rawVar.name())
                    .expression("[" + rawVar.name() + " as " + memberStrategy.targetType() + "]");
        }

    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(
            B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = memberStrategy.rawVarType() + "[]";
        TypescriptSource.Assignment<B> decl = declare ? bb.declareConst(instantiatedVar)
                .ofType(type)
                : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            decl.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .instantiate().ofType(targetType());
            TypescriptSource.ConditionalClauseBuilder<B> test = bb.ifDefined(rawVar.name());
            iterAdd(test, rawVar, instantiatedVar);
        } else {
            decl.assignedToNew().ofType(targetType());
            iterAdd(bb, rawVar, instantiatedVar);
        }

    }

    @Override
    public TsSimpleType shapeType() {
        return new TsShapeType(this.member, strategies.types(), true, false);
    }
}
