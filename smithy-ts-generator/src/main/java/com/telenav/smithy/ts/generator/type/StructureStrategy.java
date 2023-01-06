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
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
class StructureStrategy extends AbstractTypeStrategy<StructureShape> {

    StructureStrategy(StructureShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String targetType = rawVar.optional() ? targetType() + " | undefined" : targetType();
        TypescriptSource.Assignment<B> assig = 
                declare ? block.declareConst(instantiatedVar) : block.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("fromJsonObject").withArgument(rawVar.name()).on(targetType());
        } else {
            assig.assignedToInvocationOf("fromJsonObject").withArgument(rawVar.name()).on(targetType);
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        TypescriptSource.Assignment<B> assig = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("toJSON").on(rawVar.name());
        } else {
            assig.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void instantiateFromRawJsonObject(B inv, TsVariable rawVar) {
        if (rawVar.optional()) {
            inv.withTernary("typeof " + rawVar.name() + " === 'undefined'").expression("undefined").invoke("fromJsonObject").withArgument(rawVar.name()).on(targetType());
        } else {
            inv.withInvocationOf("fromJsonObject").withArgument(rawVar.name()).on(targetType());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.OBJECT;
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

}
