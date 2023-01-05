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

import static com.telenav.smithy.smithy.ts.generator.UnionTypeGenerator.decodeMethodName;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import java.util.Map;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 *
 * @author Tim Boudreau
 */
class UnionStrategy extends AbstractTypeStrategy<UnionShape> {

    UnionStrategy(UnionShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType() + " | undefined";
        Assignment<B> assig = declare ? bb.declare(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        assig.assignedToInvocationOf(decodeMethodName(shape, strategies.tsTypeName(shape)))
                .withArgument(rawVar.name()).inScope();
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>>
            void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        inv.withArgumentFromInvoking(decodeMethodName(shape, strategies.tsTypeName(shape)))
                .withArgument(rawVar.name()).inScope();
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        if (declare) {
            bb.declare(instantiatedVar).ofType(targetType() + " | undefined").unassigned();
        }
        TypescriptSource.ConditionalClauseBuilder<B> iff = null;
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            Shape target = strategies.model().expectShape(e.getValue().getTarget());
            TypeStrategy childStrategy = strategies.strategy(target);
            if (iff == null) {
                iff = bb.iff(childStrategy.typeTest().test(rawVar.name(), childStrategy.targetType()));
            } else {
                iff = iff.orElse(childStrategy.typeTest().test(rawVar.name(), childStrategy.targetType()));
            }
            childStrategy.convertToRawJsonObject(iff, rawVar, instantiatedVar, false);
        }
        if (iff != null) {
            iff.orElse().statement(instantiatedVar + " = undefined").endIf();
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

}
