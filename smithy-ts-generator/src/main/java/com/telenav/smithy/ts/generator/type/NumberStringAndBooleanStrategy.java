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
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String targetType = targetType() + (rawVar.optional() ? " | undefined" : "");
        TypescriptSource.Assignment<B> decl = (declare ? bb.declare(instantiatedVar) : bb.assign(instantiatedVar)).ofType(targetType);
        if (rawVar.optional()) {
            decl.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").instantiate(nb -> nb.withArgument(rawVar.name()).ofType(targetType()));
        } else {
            decl.assignedToNew().withArgument(rawVar.name()).ofType(targetType());
        }
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withUndefinedIfUndefinedOr(rawVar.name()).instantiate().withArgument(rawVar.name()).ofType(targetType());
        } else {
            inv.withNew().withArgument(rawVar.name()).ofType(targetType());
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String varType = rawVar.optional() ? (rawVarType() + " | undefined") : rawVarType().typeName();
        TypescriptSource.Assignment<B> decl = declare ? bb.declare(instantiatedVar).ofType(varType) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            decl.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("toJSON").on(rawVar.name());
        } else {
            decl.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        switch (shape.getType()) {
            case BOOLEAN:
                return TsPrimitiveTypes.BOOLEAN;
            case STRING:
                return TsPrimitiveTypes.STRING;
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case LONG:
            case INTEGER:
            case FLOAT:
            case SHORT:
            case DOUBLE:
            case BYTE:
                return TsPrimitiveTypes.NUMBER;
            default:
                throw new AssertionError(shape.getType());
        }
    }

    @Override
    public String targetType() {
        if (TypeStrategies.isNotUserType(shape)) {
            return rawVarType().typeName();
        }
        return strategies.tsTypeName(shape);
    }

}
