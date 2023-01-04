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
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class PrimitiveMapStrategy extends AbstractMapStrategy {

    PrimitiveMapStrategy(MapShape shape, TypeStrategies strategies, Shape keyShape, Shape valueShape) {
        super(shape, strategies, keyShape, valueShape);
    }

    private String signature() {
        return "Map<" + keyStrategy.targetType() + ", " + valueStrategy.targetType() + ">";
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.ofType(signature() + " | undefined");
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name()).instantiate().ofType(signature());
            ConditionalClauseBuilder<B> defined = bb.ifDefined(rawVar.name());
            copyRawObjectProperties(defined, instantiatedVar, rawVar);
            defined.endIf();
        } else {
            assig.ofType(signature()).assignedToNew().ofType(signature());
            copyRawObjectProperties(bb, instantiatedVar, rawVar);
        }
    }

    private <T, B extends TSBlockBuilderBase<T, B>> void copyRawObjectProperties(
            B bb, String instantiatedVar, TsVariable rawVar) {
        bb.forVar("k", loop -> {
            keyStrategy.instantiateFromRawJsonObject(loop, keyStrategy
                    .rawVarType().variable("k"), "key", true);
            valueStrategy.instantiateFromRawJsonObject(loop, keyStrategy
                    .rawVarType().variable("k"), "value", true);
            loop.invoke("set").withArgument("key").withArgument("value").on(instantiatedVar);
            loop.over(rawVar.name());
        });
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends Invocation<T, B, A>>
            void instantiateFromRawJsonObject(B block, TsVariable rawVar) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>>
            void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name()).expression("{}");
            bb.ifDefined(rawVar.name(), defined -> {
                applyKeyValuePairsToRawObject(defined, instantiatedVar);
            });
        } else {
            assig.assignedTo("{}");
            applyKeyValuePairsToRawObject(bb, instantiatedVar);
        }
    }

    private <T, B extends TSBlockBuilderBase<T, B>> void applyKeyValuePairsToRawObject(
            B bb, String instantiatedVar) {
        bb.invoke("forEach")
                .withLambda()
                .withArgument("k").ofType(keyStrategy.targetType())
                .withArgument("v").ofType(valueStrategy.targetType())
                .body(lbb -> {
                    keyStrategy.convertToRawJsonObject(lbb, keyStrategy.shapeType()
                            .variable("k"), "rawKey", true);
                    valueStrategy.convertToRawJsonObject(lbb, valueStrategy.shapeType()
                            .variable("v"), "rawValue", true);
                    lbb.assignRawProperty("rawKey").of(instantiatedVar)
                            .assignedTo("rawValue");
                });
    }

    @Override
    public String targetType() {
        return "Map<" + strategies.tsTypeName(keyShape) + ", "
                + strategies.tsTypeName(valueShape) + ">";
    }

}
