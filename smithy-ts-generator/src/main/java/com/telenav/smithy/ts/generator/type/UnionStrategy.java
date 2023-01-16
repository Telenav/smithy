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

import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.TO_JSON;
import com.telenav.smithy.ts.generator.UnionTypeGenerator;
import static com.telenav.smithy.ts.generator.UnionTypeGenerator.decodeMethodName;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ParameterizedTypeBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.shapes.UnionShape;
import static com.telenav.smithy.ts.generator.UnionTypeGenerator.rawJsonConversionFunctionName;

/**
 *
 * @author Tim Boudreau
 */
class UnionStrategy extends AbstractTypeStrategy<UnionShape> {

    UnionStrategy(UnionShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>>
            void instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType() + " | undefined";
        Assignment<B> assig = declare ? bb.declare(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        assig.assignedToInvocationOf(decodeMethodName(shape, strategies.tsTypeName(shape)))
                .withArgument(rawVar.name()).inScope();
    }

    private Set<String> rawMemberTypes() {
        Set<String> result = new TreeSet<>();
        shape.getAllMembers().forEach((memName, memShape) -> {
            MemberStrategy<?> strat = strategies.memberStrategy(memShape);
            result.add(strat.rawVarType().typeName());
        });
        return result;
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        if (declare) {
            ParameterizedTypeBuilder<Assignment<B>> decl = bb.declare(instantiatedVar)
                    .ofComplexType("undefined");
            for (String type : rawMemberTypes()) {
                decl = decl.or(type);
            }
            decl = decl.or("void");
            decl.endType()
                    .unassigned();
        }
        if (UnionTypeGenerator.allUnionMembersAreObjectTypes(shape, strategies)) {
            if (rawVar.optional()) {
                bb.ifDefined(rawVar.name()).assign(instantiatedVar)
                        .assignedToInvocationOf(TO_JSON)
                        .on(rawVar.name()).endIf();
            } else {
                bb.assign(instantiatedVar)
                        .assignedToInvocationOf(TO_JSON)
                        .on(rawVar.name());
            }
        } else {

            if (rawVar.optional()) {
                bb.ifDefined(rawVar.name()).assign(instantiatedVar)
                        .assignedToInvocationOf(rawJsonConversionFunctionName(shape, strategies.types()))
                        .withArgument(rawVar.name())
                        .inScope().endIf();
            } else {
                bb.assign(instantiatedVar)
                        .assignedToInvocationOf(rawJsonConversionFunctionName(shape, strategies.types()))
                        .withArgument(rawVar.name())
                        .inScope();
            }
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return bestMatch(strategies.model(), shape);
    }

    @Override
    public String targetType() {
        return strategies.tsTypeName(shape);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void validate(String pathVar, B bb, String on, boolean canBeNull) {
        bb.lineComment("Union validate");
        super.validate(pathVar, bb, on, canBeNull);
        shape.getAllMembers().values().forEach(mem -> {

            MemberStrategy<?> memStrat = strategies.memberStrategy(mem);

            bb.lineComment("Member " + mem.getMemberName() + " has validatable " + memStrat.hasValidatableValues());
            if (memStrat.hasValidatableValues()) {
                bb.lineComment("Member can be validated: " + memStrat.member().getId() + " for "
                        + memStrat.shape().getId() + " " + memStrat.shape().getType());

                ConditionalClauseBuilder<B> test = bb.iff(memStrat.typeTest().test(on,
                        memStrat.targetType(), memStrat.shape()));

                String cast;
                switch (memStrat.shape().getType()) {
                    case BOOLEAN:
                    case LONG:
                    case SHORT:
                    case FLOAT:
                    case INTEGER:
                    case BYTE:
                    case BIG_DECIMAL:
                    case BIG_INTEGER:
                    case STRING:
                        if (memStrat.isModelDefined()) {
                            if (memStrat.typeTest().isExhaustive()) {
//                                cast = on + ".value";
                                cast = on;
                            } else {
//                                cast = "(" + on + " as " + memStrat.targetType() + ").value";
                                cast = "(" + on + " as " + memStrat.targetType() + ")";
                            }
                        } else {
                            if (memStrat.typeTest().isExhaustive()) {
                                cast = on;
                            } else {
                                cast = "(" + on + " as " + memStrat.targetType() + ")";
                            }
                        }
                        break;
                    case TIMESTAMP:
                        cast = on;
                        break;
                    default:
                        if (memStrat.typeTest().isExhaustive()) {
                            cast = on;
                        } else {
                            cast = "(" + on + " as " + memStrat.targetType() + ")";
                        }
                }

//                String cast = "(" + on + " as " + memStrat.targetType() + ")";
                memStrat.validate(pathVar, test, cast, false);
                test.endIf();
            } else {
                bb.lineComment("Member CANNOT be validated: " + memStrat.member().getId() + " for "
                        + memStrat.shape().getId() + " " + memStrat.shape().getType());
            }
        });
    }
}
