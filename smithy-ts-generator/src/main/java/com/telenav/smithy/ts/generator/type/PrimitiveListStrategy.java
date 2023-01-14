/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.ts.generator.type;

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
                                              String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
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
