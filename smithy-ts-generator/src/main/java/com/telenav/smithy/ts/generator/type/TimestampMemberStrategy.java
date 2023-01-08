/*
 * Copyright 2023 Mastfrog Technologies.
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

import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
public class TimestampMemberStrategy extends AbstractMemberStrategy<TimestampShape> {

    public TimestampMemberStrategy(TypeStrategy<TimestampShape> typeStrategy, MemberShape member) {
        super(typeStrategy, member);
    }

    @Override
    public Set<String> constructorArgumentTypes() {
        Set<String> result = new TreeSet<>();
        result.add(targetType());
//        result.add("Date");
        result.add("string");
        result.add("number");
        if (!required()) {
            result.add("undefined");
        }
        return result;
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> String generateConstructorFieldAssignment(B bb) {
        if (canBeAbsent()) {
            ConditionalClauseBuilder<B> test = bb.ifDefined(constructorArgumentName());
            String result = _generateConstructorFieldAssignment(test);
            test.endIf();
            return result;
        } else {
            return _generateConstructorFieldAssignment(bb);
        }
    }

    private <T, B extends TsBlockBuilderBase<T, B>> String _generateConstructorFieldAssignment(B bb) {
        String arg = constructorArgumentName();
        String result = super.structureFieldName();
        ConditionalClauseBuilder<B> dateTest = bb.ifTypeOf(arg, "string");
        if (isModelDefined()) {
            dateTest.assignField(result).ofThis()
                    .toNew(nb -> {
                        nb.withNew(newDate -> {
                            newDate
                                    .withInvocationOf("parse").withArgument(arg).on("Date")
                                    .ofType("Date");
                        }).ofType(targetType());
                    });
        } else {
            dateTest.assignField(result).ofThis()
                    .toNew(nb -> {
                        nb.withInvocationOf("parse").withArgument(arg).on("Date")
                                .ofType("Date");
                    });
        }
        dateTest = dateTest.orElse("typeof " + arg + " === 'number'");
        if (isModelDefined()) {
            dateTest.assignField(result).ofThis()
                    .toNew(nb -> {
                        nb.withNew(newDate -> {
                            newDate.withArgument(arg)
                                    .ofType("Date");
                        }).ofType(targetType());
                    });
        } else {
            dateTest.assignField(result).ofThis()
                    .toNew(nb -> {
                        nb.withArgument(arg).ofType("Date");
                    });
        }
        if (defaulted()) {
            DefaultTrait defs = defaults().get();
            dateTest = dateTest.orElse("typeof " + arg + " === 'undefined'");
            dateTest = applyDefault(defs, dateTest.assign(constructorArgumentName()).assignedTo());
        }
        dateTest.orElse().assignField(result).ofThis()
                .to(arg).endIf();

        return result;
    }

}
