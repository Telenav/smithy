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

import com.mastfrog.util.strings.Strings;
import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.escape;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.GREATER_THAN;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.LESS_THAN;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.NOT_EQUALLING;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractMemberStrategy<S extends Shape> implements TypeStrategy<S>, MemberStrategy<S> {

    private final TypeStrategy<S> typeStrategy;
    private final MemberShape member;

    AbstractMemberStrategy(TypeStrategy<S> typeStrategy, MemberShape member) {
        this.typeStrategy = typeStrategy;
        this.member = member;
    }

    @Override
    public final MemberShape member() {
        return member;
    }

    @Override
    public final TsSimpleType rawVarType() {
        return typeStrategy.rawVarType();
    }

    @Override
    public final String targetType() {
        return typeStrategy.targetType();
    }

    @Override
    public final TsSimpleType shapeType() {
        return typeStrategy.shapeType();
    }

    @Override
    public final Shape shape() {
        return typeStrategy.shape();
    }

    @Override
    public final TypeMatchingStrategy typeTest() {
        return typeStrategy.typeTest();
    }

    @Override
    public final <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(
            B bb, TsVariable rawVar, String instantiatedVar, boolean declare, boolean generateThrowIfUnrecognized) {
        typeStrategy.instantiateFromRawJsonObject(bb, rawVar, instantiatedVar, declare, true);
    }

    @Override
    public final <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        typeStrategy.convertToRawJsonObject(block, rawVar, instantiatedVar, declare);
    }

    @Override
    public final <T, B extends TsBlockBuilderBase<T, B>> void populateQueryParam(String fieldName, boolean required, B bb, String queryParam) {
        typeStrategy.populateQueryParam(fieldName, required, bb, queryParam);
    }

    @Override
    public final <A> A populateHttpHeader(TypescriptSource.Assignment<A> assig, String fieldName) {
        return typeStrategy.populateHttpHeader(assig, fieldName);
    }

    @Override
    public final <T> T applyDefault(DefaultTrait def, TypescriptSource.ExpressionBuilder<T> ex) {
        return typeStrategy.applyDefault(def, ex);
    }

    @Override
    public TypeStrategies origin() {
        return typeStrategy.origin();
    }

    @Override
    public Model model() {
        return typeStrategy.model();
    }

    public String owningType(Class<? extends Trait> trait) {
        if (member.getTrait(trait).isPresent()) {
            return new TsTypeUtils(model()).tsTypeName(model().expectShape(member.getContainer()));
        }
        return targetType();
    }

    public Shape owningShape() {
        return model().expectShape(member.getContainer());
    }

    @Override
    public String owningTypeName(Class<? extends Trait> trait) {
        if (member().getTrait(trait).isPresent()) {
            Shape sh = model().expectShape(member.getContainer());
            return origin().tsTypeName(sh);
        }
        return typeStrategy.owningType(trait);
    }

    @Override
    public String patternFieldName() {
        if (member().getTrait(PatternTrait.class).isPresent()) {
            Shape sh = model().expectShape(member.getContainer());
            return escape(origin().strategy(sh).patternFieldName()
                    + "_" + member().getMemberName().toUpperCase());
        }
        return typeStrategy.patternFieldName();
    }

    @Override
    public String toString() {
        return member.getContainer().getName() + "." + member.getMemberName()
                + "(" + typeStrategy + ")";
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void validate(String pathVar, B bb, String on, boolean canBeNull) {

        switch (model().expectShape(member.getContainer()).getType()) {
            case MAP:
            case LIST:
            case SET:
            case UNION:
                // do nothing
                break;
            default:
                canBeNull |= !member.getTrait(RequiredTrait.class).isPresent();
        }

        boolean modelDefined = isModelDefined();
        doValidate(owningType(PatternTrait.class), patternFieldName(), member, lengthProperty(),
                pathVar, bb, on, canBeNull, member.getContainer().getName() + "." + member().getMemberName(),
                modelDefined, shape().getType());

        if (typeStrategy.isModelDefined() && typeStrategy.hasValidatableValues()) {
            if (shape().isUnionShape()) {
                typeStrategy.validate(pathVar, bb, on, canBeNull);
                return;
            }
            if (canBeNull) {

                bb.ifDefined(on).invoke("validate").withArgument(pathVar).withArgument("onProblem")
                        .on(on).endIf();
            } else {
                bb.invoke("validate").withArgument(pathVar).withArgument("onProblem")
                        .on(on);
            }
        }
    }

    private static <T, B extends TsBlockBuilderBase<T, B>> void doValidate(String owningType,
            String patternField, MemberShape shape, String lengthProperty, String pathVar, B bb,
            String on, boolean canBeNull, String name, boolean modelDefined, ShapeType type) {

        if (!shape.getTrait(LengthTrait.class).isPresent()
                && !shape.getTrait(PatternTrait.class).isPresent()
                && !shape.getTrait(RangeTrait.class).isPresent()) {
            return;
        }
        bb.blankLine();
        bb.lineComment("Validate " + on + " " + pathVar + " nullable? " + canBeNull);
//        bb.lineComment("Model defined? " + isModelDefined());

        Consumer<Consumer<TsBlockBuilderBase<?, ?>>> testApplier;
        TypescriptSource.ConditionalClauseBuilder<B> ifExists = null;
        if (canBeNull) {
            TypescriptSource.ConditionalClauseBuilder<B> ie = ifExists = bb.ifDefined(on);
            testApplier = cons -> {
                cons.accept(ie);
            };
        } else {
            testApplier = cons -> {
                cons.accept(bb);
            };
        }

        bb.lineComment("SHAPE ID: " + shape.getId());
        String realOn;
        if (modelDefined) {
            bb.lineComment("IS MODEL DEFINED.");
            switch (type) {
                case BOOLEAN:
                case LONG:
                case SHORT:
                case FLOAT:
                case DOUBLE:
                case INTEGER:
                case BYTE:
                case BIG_DECIMAL:
                case BIG_INTEGER:
                case STRING:
                    bb.lineComment("USE VALUE FIELD");
                    realOn = on + ".value";
                    break;
                default:
                    bb.lineComment("NO VALUE FIELD FOR " + shape.getType());
                    realOn = on;
            }
        } else {
            realOn = on;
        }

        shape.getTrait(LengthTrait.class).ifPresent(len -> {
            len.getMin().ifPresent(min -> {
                if (min != 0L) {
                    boolean same = len.getMax().map(max -> {
                        bb.lineComment("MIN " + min + " MAX " + max + " on " + shape.getId());
                        return max.equals(min);
                    }).orElse(false);

                    testApplier.accept(tsbb -> {
                        if (same) {
                            tsbb.iff().operation(NOT_EQUALLING).field(lengthProperty)
                                    .of(realOn)
                                    .literal(min)
                                    .invoke("onProblem")
                                    .withArgument(pathVar + " + '.length'")
                                    .withStringLiteralArgument(name
                                            + " length must be exactly " + min)
                                    .inScope()
                                    .endIf();
                        } else {
                            tsbb.iff().operation(LESS_THAN).field(lengthProperty)
                                    .of(realOn)
                                    .literal(min)
                                    .invoke("onProblem")
                                    .withArgument(pathVar + " + '.length'")
                                    .withStringLiteralArgument(name
                                            + " length must be >= " + min)
                                    .inScope()
                                    .endIf();
                        }
                    });
                }
            });
            len.getMax().ifPresent(max -> {
                boolean same = len.getMin().map(val -> val.equals(max)).orElse(false);
                if (!same && max < Integer.MAX_VALUE) {
                    testApplier.accept(tsbb -> {
                        tsbb.iff().operation(GREATER_THAN).field(lengthProperty)
                                .of(realOn)
                                .literal(max)
                                .invoke("onProblem")
                                .withArgument(pathVar + " + '.length'")
                                .withStringLiteralArgument(name
                                        + " length must be <= " + max)
                                .inScope()
                                .endIf();
                    });
                }
            });
        });
        shape.getTrait(PatternTrait.class).ifPresent(pat -> {

            testApplier.accept(tsbb -> {
                String rex = pat.getValue().replace("/", "\\/");
                String test = "!/" + rex + "/.test(" + realOn + ")";
                tsbb.iff(test)
                        .invoke("onProblem")
                        .withArgument(pathVar)
                        .withStringLiteralArgument(name + " must match the pattern /"
                                + pat.getValue() + "/")
                        .inScope()
                        .endIf();
            });
        });
        shape.getTrait(RangeTrait.class).ifPresent(len -> {
            Consumer<TsBlockBuilderBase<?, ?>> genRangeTest = tsbb -> {
                if (realOn.contains("this.u as Age")) {
                    tsbb.lineComment(Strings.toString(new Exception()));
                }

                len.getMin().ifPresent(min -> {
                    tsbb.iff().operation(LESS_THAN).expression(realOn)
                            .expression(min.toString())
                            .invoke("onProblem")
                            .withArgument(pathVar)
                            .withStringLiteralArgument(name + " value must be >= " + min)
                            .inScope()
                            .endIf();
                });
                len.getMax().ifPresent(max -> {
                    tsbb.iff().operation(GREATER_THAN).expression(realOn)
                            .expression(max.toString())
                            .invoke("onProblem")
                            .withArgument(pathVar)
                            .withStringLiteralArgument(name + " value must be <= " + max)
                            .inScope()
                            .endIf();
                });
            };
            testApplier.accept(genRangeTest);
        });
        if (ifExists != null) {
            ifExists.endIf();
        }
    }

    @Override
    public boolean isTsObject() {
        return typeStrategy.isTsObject();
    }

    @Override
    public boolean isModelDefined() {
        return typeStrategy.isModelDefined();
    }

    @Override
    public boolean defaulted() {
        return MemberStrategy.super.defaulted();
    }

}
