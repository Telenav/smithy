package com.telenav.smithy.ts.generator.type;

import static com.telenav.smithy.ts.generator.AbstractTypescriptGenerator.escape;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import software.amazon.smithy.model.traits.DefaultTrait;

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

}
