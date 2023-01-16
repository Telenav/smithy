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
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.OBJECT;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
class StructureStrategy extends AbstractTypeStrategy<StructureShape> {

    StructureStrategy(StructureShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(
            B bb, TsVariable rawVar, String instantiatedVar, boolean declare,
            boolean generateThrowIfUnrecognized) {
        String targetType = rawVar.optional() ? targetType() + " | undefined" : targetType();
        TypescriptSource.Assignment<B> assig
                = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        if (rawVar.optional()) {
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'")
                    .expression("undefined").invoke(FROM_JSON)
                    .withArgument(rawVar.name()).on(targetType());
        } else {
            assig.assignedToInvocationOf(FROM_JSON).withArgument(rawVar.name()).on(targetType);
        }
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar,
            String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        TypescriptSource.Assignment<B> assig = declare
                ? bb.declareConst(instantiatedVar).ofType(type)
                : bb.assign(instantiatedVar);
        bb.lineComment("  * " + getClass().getSimpleName() + " - " + rawVar.name() + " " + rawVar.optional() + " assign");
        if (rawVar.optional()) {
            bb.lineComment("   use ternary");
            assig.assignedToTernary("typeof " + rawVar.name() + " === 'undefined'")
                    .expression("undefined").invoke("toJSON").on(rawVar.name());
        } else {
            bb.lineComment("   use non-ternary");
            assig.assignedToInvocationOf("toJSON").on(rawVar.name());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return OBJECT;
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void validate(String pathVar, B bb, String on, boolean canBeNull) {
        super.validate(pathVar, bb, on, canBeNull);
        shape().getAllMembers()
                .forEach((name, val) -> {
                    MemberStrategy<Shape> memStrat
                            = strategies.createMemberStrategy(val, model().expectShape(val.getTarget()));
                    if (!memStrat.hasValidatableValues()) {
                        return;
                    }
                    String targetField = on + "." + memStrat.structureFieldName();
                    String vn = memStrat.structureFieldName() + "Path";
                    bb.declareConst(vn).ofType("string").assignedToStringConcatenation()
                            .appendExpression(pathVar)
                            .append(".")
                            .append(name)
                            .endConcatenation();
                    bb.lineComment("TargetField " + targetField + " for " + memStrat.shape().getType());
                    bb.lineComment("Member " + memStrat.memberName() + " of " + memStrat.shape().getId().getName()
                            + " type " + memStrat.shape().getType());
                    bb.lineComment("Strategy is " + memStrat.getClass().getName() + " " + memStrat);

                    if (memStrat.trait(RequiredTrait.class).isPresent()) {
                        TypescriptSource.ConditionalClauseBuilder<B> test = bb.ifDefined(targetField);
                        memStrat.validate(vn, test, targetField, false);
                        test.endIf();
                    } else {
                        memStrat.validate(vn, bb, targetField, !memStrat.trait(RequiredTrait.class).isPresent());
                    }
                });
    }
}
