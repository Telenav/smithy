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
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
class ConvenienceConstructorArgStrategy<S extends Shape> extends AbstractMemberStrategy<S> {

    ConvenienceConstructorArgStrategy(TypeStrategy<S> typeStrategy, MemberShape member) {
        super(typeStrategy, member);
    }

    @Override
    public Set<String> constructorArgumentTypes() {
        Set<String> result = new TreeSet<>(super.constructorArgumentTypes());
        result.add(rawVarType().typeName());
        return result;
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> String generateConstructorFieldAssignment(B bb) {
        String fld = structureFieldName();
        Optional<DefaultTrait> defs = defaults();
        defs.ifPresent(defaults -> {
            ConditionalClauseBuilder<B> test = bb.ifTypeOf(constructorArgumentName(), "undefined");
            test = applyDefault(defaults, test.assign(constructorArgumentName()).assignedTo());
            test.endIf();
        });

        ConditionalClauseBuilder<B> test = bb.ifTypeOf(constructorArgumentName(),
                rawVarType().typeName());
        test.assignField(fld).ofThis().toNew().withArgument(constructorArgumentName()).ofType(targetType());
        test.orElse().assignField(fld).ofThis().to(constructorArgumentName()).endIf();
        return fld;
    }
}
