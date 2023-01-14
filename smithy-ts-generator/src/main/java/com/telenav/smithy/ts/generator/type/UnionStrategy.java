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

import static com.telenav.smithy.ts.generator.UnionTypeGenerator.decodeMethodName;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
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
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType() + " | undefined";
        Assignment<B> assig = declare ? bb.declare(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        assig.assignedToInvocationOf(decodeMethodName(shape, strategies.tsTypeName(shape)))
                .withArgument(rawVar.name()).inScope();
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        if (declare) {
            bb.declare(instantiatedVar).ofType(targetType() + " | undefined").unassigned();
        }
        TypescriptSource.ConditionalClauseBuilder<B> iff = null;
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            Shape target = strategies.model().expectShape(e.getValue().getTarget());
            TypeStrategy<?> childStrategy = strategies.strategy(target);
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
        return bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

}
