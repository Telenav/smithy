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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class ListStrategy extends AbstractListOrSetStrategy {

    ListStrategy(ListShape shape, TypeStrategies strategies, Shape member) {
        super(shape, strategies, member, "Array");
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType();
        Assignment<B> decl = declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            decl.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .invoke(FROM_JSON)
                    .withArgument(rawVar.name()).on(targetType());
        } else {
            decl.assignedToInvocationOf(FROM_JSON)
                    .withArgument(rawVar.name()).on(targetType());
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void
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
