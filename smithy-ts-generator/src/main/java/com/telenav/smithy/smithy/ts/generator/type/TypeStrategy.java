/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.smithy.smithy.ts.generator.type;

import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.Invocation;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
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

    <T, B extends TSBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(
            B block, TsVariable rawVar, String instantiatedVar, boolean declare);

    <T, A extends InvocationBuilder<B>, B extends Invocation<T, B, A>>
            void instantiateFromRawJsonObject(B block, TsVariable rawVar);

    <T, B extends TSBlockBuilderBase<T, B>> void convertToRawJsonObject(
            B block, TsVariable rawVar, String instantiatedVar, boolean declare);

    TsSimpleType rawVarType();

    String targetType();

    TsSimpleType shapeType();

    Shape shape();

    <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam);

    <A> A populateHttpHeader(Assignment<A> assig, String fieldName);
    
//    BodyContributor constructorFieldAssignmentContributor(String argName, String fieldName);

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
}
