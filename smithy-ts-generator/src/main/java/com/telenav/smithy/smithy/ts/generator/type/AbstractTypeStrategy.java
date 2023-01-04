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

import com.mastfrog.code.generation.common.LinesBuilder;
import static com.telenav.smithy.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import static software.amazon.smithy.model.node.NodeType.ARRAY;
import static software.amazon.smithy.model.node.NodeType.NULL;
import static software.amazon.smithy.model.node.NodeType.NUMBER;
import static software.amazon.smithy.model.node.NodeType.OBJECT;
import static software.amazon.smithy.model.node.NodeType.STRING;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractTypeStrategy<S extends Shape> implements TypeStrategy<S> {

    protected final S shape;
    protected final TypeStrategies strategies;

    public AbstractTypeStrategy(S shape, TypeStrategies strategies) {
        this.shape = shape;
        this.strategies = strategies;
    }

    protected <T, B extends TSBlockBuilderBase<T, B>> Assignment<B>
            createTargetAssignment(TsVariable rawVar, boolean declare, B bb, String instantiatedVar) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType();
        Assignment<B> assig = declare ? bb.declare(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
        return assig;
    }

    @Override
    public S shape() {
        return shape;
    }

    @Override
    public TsSimpleType shapeType() {
        return new TsShapeType(shape, strategies.types(), false, false);
    }

    public <T, B extends TSBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
        if (!required) {
            bb.ifFieldDefined(fieldName).ofThis()
                    .assignLiteralRawProperty(queryParam)
                    .of("obj")
                    .assignedToField(fieldName)
                    .ofThis();
        } else {
            bb.assignLiteralRawProperty(queryParam)
                    .of("obj")
                    .assignedToField(fieldName)
                    .ofThis();
        }
    }

    public <A> A populateHttpHeader(Assignment<A> assig, String fieldName) {
        return assig.assignedToField(fieldName).ofThis();
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex) {
        switch (shape.getType()) {
            case BLOB:
            case DOCUMENT:
            case LIST:
            case MAP:
            case STRUCTURE:
            case OPERATION:
            case RESOURCE:
            case SERVICE:
                throw new ExpectationNotMetException("Defaults not supported for "
                        + shape.getType(), shape);
        }
        // Only do the default behavior for things it could possibly work for.
        // If we start supporting list or other defaults, their strategies will
        // need to override this method, so the above check won't run anyway.
        boolean prim = isNotUserType(shape);
        String valueExpression = defaultValue(def);
        if (prim) {
            return ex.expression(valueExpression);
        } else {
            return ex.instantiate(nb -> nb.withArgument(valueExpression).ofType(targetType()));
        }
    }

    protected String defaultValue(DefaultTrait def) {
        Node n = def.toNode();
        switch (n.getType()) {
            case NULL:
                return "null";
            case BOOLEAN:
                return Boolean.toString(n.asBooleanNode().get().getValue());
            case NUMBER:
                Number num = n.asNumberNode().get().getValue();
                switch (shape.getType()) {
                    case INTEGER:
                        return Integer.toString(num.intValue());
                    case LONG:
                        return Long.toString(num.longValue());
                    case BYTE:
                        return Byte.toString(num.byteValue());
                    case SHORT:
                        return Short.toString(num.shortValue());
                    case FLOAT:
                        return Float.toString(num.floatValue());
                    case INT_ENUM:
                        return Integer.toString(num.intValue());
                    case BOOLEAN:
                        return Boolean.toString(num.longValue() != 0);
                    default:
                        throw new IllegalArgumentException("Number default for "
                                + shape.getType() + " " + shape.getId()
                                + " pointing to " + shape.getId()
                                + "?");
                }
            case STRING:
                return '"' + LinesBuilder.escape(n.expectStringNode().getValue()) + '"';
            case OBJECT:
            case ARRAY:
                throw new IllegalArgumentException("Defaults not currently supported for "
                        + shape.getType() + " with default of " + n.getType()
                        + " (in " + shape.getId() + ")"
                );
        }
        return "";

    }

}
