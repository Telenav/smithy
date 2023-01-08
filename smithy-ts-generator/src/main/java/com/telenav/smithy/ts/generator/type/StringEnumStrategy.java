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

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.STRING;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.CaseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.SwitchBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
class StringEnumStrategy extends AbstractEnumStrategy {

    StringEnumStrategy(EnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    Set<String> enumValues() {
        return new TreeSet<>(shape.getMemberNames());
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType();
        Assignment<B> assig = (declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar));

        if (rawVar.optional()) {
            ConditionalClauseBuilder<B> test = bb.iff(rawVar.name());
            checkValidValue(test, rawVar);
            test.endIf();
        } else {
            checkValidValue(bb, rawVar);
        }

        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name()).expression(rawVar.name() + " as " + targetType());
        } else {
            assig.assignedTo(rawVar.name() + " as " + targetType());
        }
    }

    private <T, B extends TsBlockBuilderBase<T, B>> void checkValidValue(
            B bb, TsVariable rawVar) {
        CaseBuilder<SwitchBuilder<B>> caseBlock = null;
        for (String ev : enumValues()) {
            if (caseBlock != null) {
                caseBlock = caseBlock.endBlock().inStringLiteralCase(ev);
            } else {
                caseBlock = bb.switchStringLiteralCase(ev);//.lineComment("ok");
            }
        }
        if (caseBlock != null) {
            caseBlock.statement("break");
            caseBlock.endBlock().inDefaultCase(def -> {
                def.throwing(err -> {
                    err.withStringConcatenation(str -> {
                        str.appendExpression(rawVar.name())
                                .append(" is not a member of enum "
                                        + targetType());
                    });
                });
            }).on(rawVar.name());
        }
        System.out.println(caseBlock);
    }

    @Override
    public <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>> void
            instantiateFromRawJsonObject(B ib, TsVariable rawVar) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = (declare ? block.declareConst(instantiatedVar).ofType(type) : block.assign(instantiatedVar));
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name())
                    .expression(rawVar.name());
        } else {
            assig.assignedTo(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return STRING;
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex) {
//        return ex.element().literal(defaultValue(def)).of(targetType());
        return ex.expression(defaultValue(def));
    }

}
