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

import static com.mastfrog.util.strings.Strings.camelCaseToDelimited;
import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.escape;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.GREATER_THAN;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.LESS_THAN;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.NOT_EQUALLING;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Optional;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * Strategy for converting between a raw JSON object and a typed object and back
 * - used by types which can nest other types and need to invoke the right
 * conversion for the member shape they need to instantiate as part of being
 * instantiated themselves, or when converting to JSON. Most implementations use
 * our toJsonObject() method for complex and wrapper types, but there are some
 * corner cases such as a type declared as a Map which does *not* use a
 * model-defined shape, where we need to simply instantiate a Typescript map.
 *
 * @author Tim Boudreau
 */
public interface TypeStrategy<S extends Shape> {

    <T, B extends TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(
            B bb, TsVariable rawVar, String instantiatedVar, boolean declare,
            boolean generateThrowIfUnrecognized);

    <T, B extends TsBlockBuilderBase<T, B>> void convertToRawJsonObject(
            B block, TsVariable rawVar, String instantiatedVar, boolean declare);

    TsSimpleType rawVarType();

    String targetType();

    TsSimpleType shapeType();

    Shape shape();

    TypeStrategies origin();

    <T, B extends TsBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam);

    <A> A populateHttpHeader(Assignment<A> assig, String fieldName);

    default boolean isModelDefined() {
        return !isNotUserType(shape());
    }

    default TypeMatchingStrategy typeTest() {
        if (TsTypeUtils.isNotUserType(shape())) {
            if (shapeType().isArray()) {
                return TypeMatchingStrategies.ARRAY_IS_ARRAY;
            }
            if (shape().getType() == ShapeType.MAP) {
                return TypeMatchingStrategies.INSTANCE_OF;
            }
            return TypeMatchingStrategies.TYPE_OF;
        } else {
            return TypeMatchingStrategies.INSTANCE_OF;
        }
    }

    <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex);

    public interface TypeMatchingStrategy {

        public String test(String varName, String typeName, Shape shape);
    }

    enum TypeMatchingStrategies implements TypeMatchingStrategy {
        TYPE_OF,
        INSTANCE_OF,
        ARRAY_IS_ARRAY;

        @Override
        public String toString() {
            switch (this) {
                case TYPE_OF:
                    return "typeof";
                case INSTANCE_OF:
                    return "instanceof";
                case ARRAY_IS_ARRAY:
                    return "Array.isArray";
                default:
                    throw new AssertionError(this);
            }
        }

        public String test(String varName, String typeName, Shape shape) {
            switch (this) {
                case TYPE_OF:
                    return "typeof " + varName + " === '" + typeName + "'";
                case INSTANCE_OF:
                    return varName + " instanceof " + typeName;
                case ARRAY_IS_ARRAY:
                    return "Array.isArray(" + varName + ")";
                default:
                    throw new AssertionError(this);
            }
        }
    }

    @SuppressWarnings("unchecked")
    default Optional<? extends TypeStrategy<TimestampShape>> asTimestampStrategy() {
        if (shape().isTimestampShape()) {
            return Optional.of((TypeStrategy<TimestampShape>) this);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    default Optional<? extends TypeStrategy<StructureShape>> asStructureStrategy() {
        if (shape().isStructureShape()) {
            return Optional.of((TypeStrategy<StructureShape>) this);
        }
        return Optional.empty();
    }

    default String lengthProperty() {
        switch (shape().getType()) {
            case STRING:
            case LIST:
            case SET:
                return "length";
            case MAP:
                return "entries.length";
            default:
                return null;
        }
    }

    default String memberName() {
        return shape().getId().getName();
    }

    default ShapeId id() {
        return shape().getId();
    }

    /**
     * Get the type which is expected to own any static fields needed for
     * validation (such as regexen).
     *
     * @return The target type - if this is a member shape, it will be the type
     * of its container, otherwise it is the type this strategy is the strategy
     * for.
     */
    default String owningType(Class<? extends Trait> trait) {
        return targetType();
    }

    default Shape owningShape(Class<? extends Trait> trait) {
        return shape();
    }

    String owningTypeName(Class<? extends Trait> trait);

    default <T, B extends TsBlockBuilderBase<T, B>> void validate(String pathVar, B bb, String on, boolean canBeNull) {

        bb.blankLine();
        bb.lineComment("Validate " + on + " " + pathVar + " nullable? " + canBeNull);
        bb.lineComment("Model defined? " + isModelDefined());
        
        Consumer<Consumer<TsBlockBuilderBase<?, ?>>> testApplier;
        ConditionalClauseBuilder<B> ifExists = null;
        if (canBeNull) {
            ConditionalClauseBuilder<B> ie = ifExists = bb.ifDefined(on);
            testApplier = cons -> {
                cons.accept(ie);
            };
        } else {
            testApplier = cons -> {
                cons.accept(bb);
            };
        }

        trait(LengthTrait.class).ifPresent(len -> {
            len.getMin().ifPresent(min -> {
                if (min != 0L) {
                    boolean same = len.getMax().map(val -> val.equals(min)).orElse(false);
                    testApplier.accept(tsbb -> {
                        if (same) {
                            tsbb.iff().operation(NOT_EQUALLING).field(lengthProperty())
                                    .of(on)
                                    .literal(min)
                                    .invoke("onProblem")
                                    .withArgument(pathVar + " + '.length'")
                                    .withStringLiteralArgument(shape().getId().getName()
                                            + " length must be exactly " + min)
                                    .inScope()
                                    .endIf();
                        } else {
                            tsbb.iff().operation(LESS_THAN).field(lengthProperty())
                                    .of(on)
                                    .literal(min)
                                    .invoke("onProblem")
                                    .withArgument(pathVar + " + '.length'")
                                    .withStringLiteralArgument(shape().getId().getName()
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
                        tsbb.iff().operation(GREATER_THAN).field(lengthProperty())
                                .of(on)
                                .literal(max)
                                .invoke("onProblem")
                                .withArgument(pathVar + " + '.length'")
                                .withStringLiteralArgument(shape().getId().getName()
                                        + " length must be <= " + max)
                                .inScope()
                                .endIf();
                    });
                }
            });
        });
        trait(PatternTrait.class).ifPresent(pat -> {
            testApplier.accept(tsbb -> {
                String test = "!/" + pat.getValue() + "/.test(" + on + ")";
//                String test = "!"
//                        + owningType(PatternTrait.class)
//                        + "."
//                        + patternFieldName()
//                        + ".test(" + on + ")";
                tsbb.iff(test)
                        .invoke("onProblem")
                        .withArgument(pathVar)
                        .withStringLiteralArgument(id().getName() + " must match the pattern /"
                                + pat.getValue() + "/")
                        .inScope()
                        .endIf();
            });
        });
        trait(RangeTrait.class).ifPresent(len -> {
            Consumer<TsBlockBuilderBase<?, ?>> genRangeTest = tsbb -> {
                len.getMin().ifPresent(min -> {
                    tsbb.iff().operation(LESS_THAN).expression(on)
                            .expression(min.toString())
                            .invoke("onProblem")
                            .withArgument(pathVar)
                            .withStringLiteralArgument(id().getName() + " value must be >= " + min)
                            .inScope()
                            .endIf();
                });
                len.getMax().ifPresent(max -> {
                    tsbb.iff().operation(GREATER_THAN).expression(on)
                            .expression(max.toString())
                            .invoke("onProblem")
                            .withArgument(pathVar)
                            .withStringLiteralArgument(id().getName() + " value must be <= " + max)
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

    default <T extends Trait> Optional<T> trait(Class<T> traitClass) {
        return shape().getTrait(traitClass);
    }

    default String patternFieldName() {
        return escape(camelCaseToDelimited(memberName(), '_').toUpperCase() + "_PATTERN");
    }

    default boolean canImplementValidating() {
        return AbstractTypeStrategy.canImplementValidating(shape(), model());
    }

    default boolean hasValidatableValues() {
        return AbstractTypeStrategy.hasValidatableValues(shape(), model());
    }

    Model model();

}
