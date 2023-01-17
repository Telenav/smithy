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

import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.FROM_JSON;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.BOOLEAN;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.NUMBER;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.STRING;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
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
    public <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        String targetType = targetType() + (rawVar.optional() ? " | undefined" : "");
        boolean prim = isNotUserType(shape);
        Assignment<B> decl = (declare ? bb.declare(instantiatedVar) : bb.assign(instantiatedVar)).ofType(targetType);
        if (rawVar.optional()) {
            ExpressionBuilder<B> exp;
            if (super.valuesCanEvaluateToFalse()) {
                exp = decl.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined");
            } else {
                exp = decl.assignedToUndefinedIfUndefinedOr(rawVar.name());
            }
            if (prim) {
                exp.as(rawVarType().typeName()).expression(rawVar.name());
            } else {
                exp.invoke(FROM_JSON)
                        .withArgument(rawVar.name()).on(targetType());
            }
        } else {
            if (prim) {
                decl.assignedTo().as(rawVarType().typeName()).expression(rawVar.name());
            } else {
                decl.assignedToInvocationOf(FROM_JSON)
                        .withArgument(rawVar.name()).on(targetType());
            }
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String varType = rawVar.optional() ? (rawVarType() + " | undefined") : rawVarType().typeName();
        bb.lineComment("  * " + getClass().getSimpleName() + " - " + rawVar.name() + " " + rawVar.optional() + " assign");
        Assignment<B> decl = declare ? bb.declare(instantiatedVar).ofType(varType) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            bb.lineComment("   use ternary");
            decl.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'")
                    .expression("undefined")
                    .invoke("toJSON").on(rawVar.name());
        } else {
            bb.lineComment("   use non-ternary");
            decl.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        switch (shape.getType()) {
            case BOOLEAN:
                return BOOLEAN;
            case STRING:
                return STRING;
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case LONG:
            case INTEGER:
            case FLOAT:
            case SHORT:
            case DOUBLE:
            case BYTE:
                return NUMBER;
            default:
                throw new AssertionError(shape.getType());
        }
    }

    @Override
    public String targetType() {
        if (isNotUserType(shape)) {
            return rawVarType().typeName();
        }
        return strategies.tsTypeName(shape);
    }
}
