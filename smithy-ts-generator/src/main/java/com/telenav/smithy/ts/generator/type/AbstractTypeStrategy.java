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

import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.escape;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import java.util.Map;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.BLOB;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.DOCUMENT;
import static software.amazon.smithy.model.shapes.ShapeType.ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import static software.amazon.smithy.model.shapes.ShapeType.MEMBER;
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;
import static software.amazon.smithy.model.shapes.ShapeType.RESOURCE;
import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;
import static software.amazon.smithy.model.shapes.ShapeType.SET;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import static software.amazon.smithy.model.shapes.ShapeType.UNION;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractTypeStrategy<S extends Shape> implements TypeStrategy<S> {

    protected final S shape;
    protected final TypeStrategies strategies;

    AbstractTypeStrategy(S shape, TypeStrategies strategies) {
        this.shape = shape;
        this.strategies = strategies;
    }

    @Override
    public TypeStrategies origin() {
        return strategies;
    }

    protected <T, B extends TsBlockBuilderBase<T, B>> Assignment<B>
            createTargetAssignment(TsVariable rawVar, boolean declare, B bb, String instantiatedVar) {
        String type = rawVar.optional() ? targetType() + " | undefined" : targetType();
        return declare ? bb.declare(instantiatedVar).ofType(type) : bb.assign(instantiatedVar);
    }

    @Override
    public S shape() {
        return shape;
    }

    @Override
    public TsSimpleType shapeType() {
        return new TsShapeType(shape, strategies.types(), false, false);
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
        if (!required) {
            ConditionalClauseBuilder<B> test;
            if (shape.getType() == ShapeType.BOOLEAN) {
                test = bb.iff("typeof this." + fieldName + " !== 'undefined'");
            } else {
                test = bb.ifFieldDefined(fieldName).ofThis();
            }
            test.assignLiteralRawProperty(queryParam)
                    .of("obj")
                    .assignedToField(fieldName)
                    .ofThis()
                    .endIf();
        } else {
            bb.assignLiteralRawProperty(queryParam)
                    .of("obj")
                    .assignedToField(fieldName)
                    .ofThis();
        }
    }

    @Override
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
                    case DOUBLE:
                        return Double.toString(num.doubleValue());
                    case INT_ENUM:
                        return Integer.toString(num.intValue());
                    case BOOLEAN:
                        return Boolean.toString(num.longValue() != 0);
                    case BIG_INTEGER:
                        return Long.toString(num.longValue());
                    case BIG_DECIMAL:
                        return Double.toString(num.doubleValue());
                    default:
                        throw new IllegalArgumentException("Number default for "
                                + shape.getType() + " " + shape.getId()
                                + " pointing to " + shape.getId()
                                + "?");
                }
            case STRING:
                return '"' + escape(n.expectStringNode().getValue()) + '"';
            case OBJECT:
            case ARRAY:
                throw new IllegalArgumentException("Defaults not currently supported for "
                        + shape.getType() + " with default of " + n.getType()
                        + " (in " + shape.getId() + ")"
                );
        }
        return "";

    }

    public Model model() {
        return strategies.model();
    }

    static boolean canImplementValidating(Shape shape, Model model) {
        if (shape.isMemberShape()) {
            return _canImplementValidating(shape, model)
                    || _canImplementValidating(model.expectShape(shape.asMemberShape().get().getTarget()), model);
        }
        return _canImplementValidating(shape, model);
    }

    private static boolean _canImplementValidating(Shape shape, Model model) {
        if (TsTypeUtils.isNotUserType(shape)) {
            return false;
        }
        switch (shape.getType()) {
            case ENUM:
            case INT_ENUM:
            case BLOB:
            case DOCUMENT:

            case OPERATION:
            case SERVICE:
            case RESOURCE:
            case UNION:
            case BOOLEAN:
            case TIMESTAMP:
                return false;
            case MEMBER:
                return canImplementValidating(model.expectShape(shape.asMemberShape().get().getTarget()), model);
            default:
                return true;
        }
    }

    static boolean hasValidatableValues(Shape shape, Model model) {
        if (!canImplementValidating(shape, model)) {
//            return false;
        }
        boolean result = shape.getTrait(PatternTrait.class).isPresent()
                || shape.getTrait(RangeTrait.class).isPresent()
                || shape.getTrait(LengthTrait.class).isPresent();
        if (!result) {
            switch (shape.getType()) {
                case MEMBER:
                    return hasValidatableValues(model.expectShape(shape.asMemberShape().get().getTarget()), model);
                case LIST:
                case SET:
                    return hasValidatableValues(shape.asListShape().get().getMember(), model);
                case MAP:
                    return hasValidatableValues(shape.asMapShape().get().getKey(), model)
                            || hasValidatableValues(shape.asMapShape().get().getValue(), model);
                case UNION:
                case STRUCTURE:
                    for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                        if (hasValidatableValues(e.getValue(), model) || hasValidatableValues(e.getValue(), model)) {
                            return true;
                        }
                    }
            }
        }
        return result;
    }

    @Override
    public String owningTypeName(Class<? extends Trait> trait) {
        Shape s = owningShape(trait);
        return strategies.strategy(s).patternFieldName();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + shape.getId().getName() + ")";
    }
}
