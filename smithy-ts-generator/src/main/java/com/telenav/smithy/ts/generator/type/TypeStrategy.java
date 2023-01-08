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

import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Optional;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DefaultTrait;

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
            B block, TsVariable rawVar, String instantiatedVar, boolean declare);

    <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>>
            void instantiateFromRawJsonObject(B block, TsVariable rawVar);

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
                return TypeMatchingStrategy.ARRAY_IS_ARRAY;
            }
            if (shape().getType() == ShapeType.MAP) {
                return TypeMatchingStrategy.INSTANCE_OF;
            }
            return TypeMatchingStrategy.TYPE_OF;
        } else {
            return TypeMatchingStrategy.INSTANCE_OF;
        }
    }

    <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex);

    public enum TypeMatchingStrategy {
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

        public String test(String varName, String typeName) {
            switch (this) {
                case TYPE_OF:
                    return "typeof " + varName + " === " + typeName;
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
}
