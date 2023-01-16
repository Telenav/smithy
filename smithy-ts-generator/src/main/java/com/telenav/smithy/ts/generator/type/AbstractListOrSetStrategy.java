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

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractListOrSetStrategy extends AbstractTypeStrategy<ListShape> {

    protected final Shape member;
    protected final TypeStrategy<?> memberStrategy;
    private final String collectionType;

    protected AbstractListOrSetStrategy(ListShape shape, TypeStrategies strategies, Shape member, String collectionType) {
        super(shape, strategies);
        this.collectionType = collectionType;
        this.member = member;
        memberStrategy = strategies.strategy(member);
    }

    @Override
    public final TsSimpleType rawVarType() {
        return bestMatch(strategies.model(), shape);
    }

    protected final String tsCollectionTypeName() {
        return collectionType;
    }

    @Override
    public final String targetType() {
        boolean prim = isNotUserType(shape);
        if (prim) {
            return tsCollectionTypeName() + "<" + memberStrategy.targetType() + ">";
        } else {
            return strategies.tsTypeName(shape);
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
        if (isNotUserType(shape)) {
            populateQueryParamForTsCollection(fieldName, required, bb, queryParam);
        } else {
            if (!required) {
                bb.ifFieldDefined(fieldName).ofThis()
                        .assignLiteralRawProperty(queryParam).of("obj")
                        .assignedToInvocationOf("toString")
                        .onField(fieldName)
                        .ofThis()
                        .endIf();
            } else {
                bb.assignLiteralRawProperty(queryParam)
                        .of("obj")
                        .assignedToInvocationOf("toString")
                        .onField(fieldName)
                        .ofThis();
            }
        }
    }

    @Override
    public <A> A populateHttpHeader(Assignment<A> assig, String fieldName) {
        if (isNotUserType(shape)) {
            return populateHttpHeaderForTsCollection(assig, fieldName);
        }
        return assig.assignedToInvocationOf("toString").onField(fieldName).ofThis();
    }

    protected <A> A populateHttpHeaderForTsCollection(Assignment<A> assig, String fieldName) {
        return convertToArrayOfStrings(assig, fieldName, true, true);
    }

    protected <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void populateQueryParamForTsCollection(
            String fieldName, boolean required, B bb, String queryParam) {
        if (!required) {
            Assignment<TypescriptSource.ConditionalClauseBuilder<B>> assig = bb.ifFieldDefined(fieldName).ofThis()
                    .assignLiteralRawProperty(queryParam)
                    .of("obj");
            convertToArrayOfStrings(assig, fieldName, required, false).endIf();
        } else {
            Assignment<B> assig = bb.assignLiteralRawProperty(queryParam).of("obj");
            convertToArrayOfStrings(assig, fieldName, required, false);
        }
    }

    protected <A> A convertToArrayOfStrings(Assignment<A> assig,
            String fieldName, boolean required, boolean headers) {
        if (isNotUserType(member)) {
            return assig.assignedToSelfExecutingFunction(ebb -> {
                convertToRawArray(ebb, fieldName, false);
            });
        } else {
            return assig.assignedToInvocationOf("join").withStringLiteralArgument(",")
                    .onField(fieldName).ofThis();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public String lengthProperty() {
        if (shape.isSetShape() || shape.getTrait(UniqueItemsTrait.class).isPresent()) {
            return "size";
        }
        return super.lengthProperty();
    }

    protected void convertToRawArray(TypescriptSource.TsBlockBuilder<Void> bb, String fieldName,
            boolean headers) {
        bb.declare("arr").ofType(memberStrategy.rawVarType() + "[]")
                .assignedTo("[]");
        bb.ifDefined("this." + fieldName, ifdef -> {
            ifdef.invoke("forEach").withLambda(lam -> {
                lam.withArgument("unrawItem").inferringType()
                        .body(lbb -> {
                            if (headers) {
                                Assignment<TypescriptSource.TsBlockBuilder<Void>> assig
                                        = lbb.declare("rawItem").ofType("string");
                                memberStrategy.populateHttpHeader(assig, "unrawItem");
                            } else {
                                TsVariable var = memberStrategy.rawVarType().variable("unrawItem");
                                memberStrategy.convertToRawJsonObject(lbb, var, "rawItem", true);
                            }
                            lbb.invoke("push").withArgument("rawItem").on("arr");
                        });
            }).onField(fieldName).ofThis();
            ifdef.endIf();
        });
        bb.returningInvocationOf("join").withStringLiteralArgument(",").on("arr");
    }

    protected <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void iterAdd(B bb, TsVariable rawVar, String instantiatedVar) {
        bb.invoke("forEach").withLambda().withArgument("item").inferringType()
                .body(lbb -> {
                    TsVariable itemVar = memberStrategy.shapeType().variable("item");
                    memberStrategy.instantiateFromRawJsonObject(lbb, itemVar, "realItem", true, true);
                    lbb.invoke("push").withArgument("realItem").on(instantiatedVar);
                }).on(rawVar.name());
    }

    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void validate(String pathVar, B bb, String on, boolean canBeNull) {
        super.validate(pathVar, bb, on, canBeNull);
        if (memberStrategy.hasValidatableValues()) {
            bb.declare("p").ofType("string").assignedTo("(path || '" + shape.getId().getName() + "') + '.'");
            if (isModelDefined()) {
                bb.invoke("forEach")
                        .withLambda().withArgument("item").inferringType()
                        .withArgument("index").inferringType()
                        .body(lbb -> {
                            lbb.invoke("validate")
                                    .withStringConcatenation()
                                    .appendExpression("p")
                                    .appendExpression("index")
                                    .endConcatenation()
                                    .withArgument("onProblem")
                                    .on("item");
                        })
                        .on(on);
            } else {
                bb.invoke("forEach")
                        .withLambda(lb -> {
                            lb.withArgument("item").inferringType();
                            lb.withArgument("index").inferringType();
                            lb.body(lbb -> {
                                lbb.declareConst("subPath").assignedToStringConcatenation()
                                        .appendExpression(pathVar)
                                        .append(".")
                                        .appendExpression("index")
                                        .endConcatenation();
                                memberStrategy.validate("subPath", bb, "item",
                                        memberStrategy.trait(SparseTrait.class).isPresent());
                            });
                        })
                        .on(on);
            }
        }
    }
}
