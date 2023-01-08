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
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class SetStrategy extends AbstractListOrSetStrategy {

    SetStrategy(ListShape shape, TypeStrategies strategies, Shape member) {
        super(shape, strategies, member, "Set");
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
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
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void
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
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void
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
