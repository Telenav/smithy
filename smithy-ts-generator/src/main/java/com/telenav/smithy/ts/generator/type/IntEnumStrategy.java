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

import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.escape;
import static com.telenav.smithy.ts.generator.IntEnumGenerator.recognitionFunctionName;
import static com.telenav.smithy.ts.generator.IntEnumGenerator.validationFunctionName;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.NUMBER;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
final class IntEnumStrategy extends AbstractTypeStrategy<IntEnumShape> {

    IntEnumStrategy(IntEnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar,
                    String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        String type = rawVar.optional() || !generateThrowIfUnrecognized
                ? targetType() + " | undefined"
                : targetType();
        Assignment<B> assig = (declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar));
        String mth = generateThrowIfUnrecognized
                ? validationFunctionName(strategies.model(), shape)
                : recognitionFunctionName(strategies.model(), shape);
        if (rawVar.optional()) {
            assig
                    .assignedToTernary("typeof " + rawVar.name() + " === 'undefined'")
                    .expression("undefined")
//                    .assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke(mth)
                    .withArgument(rawVar.name() + " as number")
                    .inScope();
        } else {
            assig.assignedToInvocationOf(mth)
                    .withArgument(rawVar.name() + " as number")
                    .inScope();
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = (declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar));
        if (rawVar.optional()) {
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'")
                    .expression("undefined").expression(rawVar.name() + " as number");
        } else {
            assig.assignedTo(rawVar.name() + " as number");
        }
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, TypescriptSource.ExpressionBuilder<T> ex) {
        if (def.toNode().isNumberNode()) {
            int val = def.toNode().asNumberNode().get().getValue().intValue();
            for (Map.Entry<String, Integer> e : shape.getEnumValues().entrySet()){
                if (val == e.getValue().intValue()) {
                    return ex.field(escape(e.getKey())).of(targetType());
                }
            }
        }
        return ex.expression(defaultValue(def));
    }

    @Override
    public TsSimpleType rawVarType() {
        return NUMBER;
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

    @Override
    public TypeMatchingStrategy typeTest() {
        return new IntEnumTypeMatchingStrategy();
    }

    static class IntEnumTypeMatchingStrategy implements TypeMatchingStrategy {

        @Override
        public String test(String varName, String typeName, Shape shape) {
            StringBuilder sb = new StringBuilder("typeof " + varName + " === 'number' && (");
            IntEnumShape es = shape.asIntEnumShape().get();
            Set<Integer> names = new TreeSet<>(es.getEnumValues().values());
            for (Iterator<Integer> it = names.iterator(); it.hasNext();) {
                sb.append(varName).append(" === ").append(it.next());
                if (it.hasNext()) {
                    sb.append(" || ");
                }
            }
            return sb.append(')').toString();
        }
    }
}
