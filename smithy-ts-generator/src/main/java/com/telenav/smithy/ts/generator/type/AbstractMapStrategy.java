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

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.OBJECT;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.SparseTrait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractMapStrategy extends AbstractTypeStrategy<MapShape> {

    protected final Shape keyShape;
    protected final Shape valueShape;
    protected final MemberStrategy<?> keyStrategy;
    protected final MemberStrategy<?> valueStrategy;

    protected AbstractMapStrategy(MapShape shape, TypeStrategies strategies,
            Shape keyShape, Shape valueShape) {
        super(shape, strategies);
        this.keyShape = keyShape;
        this.valueShape = valueShape;
        keyStrategy = strategies.memberStrategy(shape.getKey(), keyShape);
        valueStrategy = strategies.memberStrategy(shape.getValue(), valueShape);
    }

    @Override
    public TsSimpleType rawVarType() {
        return OBJECT;
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void validate(String pathVar,
            B bb, String on, boolean canBeNull) {
        super.validate(pathVar, bb, on, false);
        boolean validateKeys = keyStrategy.hasValidatableValues();
        boolean validateValues = valueStrategy.hasValidatableValues();
        if (validateKeys || validateValues) {
            bb.declare("counter").ofType("number").assignedTo(0);
            bb.declare("p").ofType("string").assignedTo("(path || '" + shape.getId().getName() + "') + '.'");

            bb.invoke("forEach", ib -> {
                ib.withLambda(lb -> {
                    lb.withArgument("v").inferringType();
                    if (validateKeys) {
                        lb.withArgument("k").inferringType();
                    }
                    lb.body(lbb -> {
                        boolean sparse = shape.getTrait(SparseTrait.class).isPresent();
                        if (validateKeys) {
                            if (keyStrategy.isModelDefined()) {
                                lbb.invoke("validate")
                                        .withStringConcatenation()
                                        .appendExpression("p")
                                        .append("key[")
                                        .appendExpression("counter")
                                        .append("]")
                                        .endConcatenation()
                                        .withArgument("onProblem")
                                        .on("k");
                            } else {
                                lbb.declare("keyPath")
                                        .assignedToStringConcatenation()
                                        .appendExpression("p")
                                        .append("key[")
                                        .appendExpression("counter")
                                        .append("]")
                                        .endConcatenation();
                                keyStrategy.validate("keyPath", lbb, "k", false);
                            }
                        }
                        if (validateValues) {
                            if (valueStrategy.isModelDefined()) {
                                lbb.invoke("validate")
                                        .withStringConcatenation()
                                        .appendExpression("p")
                                        .append("value[")
                                        .appendExpression("counter")
                                        .append("]")
                                        .endConcatenation()
                                        .withArgument("onProblem")
                                        .on("v");
                            } else {
                                lbb.declare("valPath")
                                        .assignedToStringConcatenation()
                                        .appendExpression("p")
                                        .append("value[")
                                        .appendExpression("counter")
                                        .append("]")
                                        .endConcatenation();
                                valueStrategy.validate("valPath", lbb, "v",
                                        false);
                            }
                        }
                        lbb.statement("counter++");
                    });
                });
                ib.onThis();
            });
        }
    }
}
