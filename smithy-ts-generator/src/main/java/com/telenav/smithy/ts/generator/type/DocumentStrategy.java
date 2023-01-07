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

import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ArrayElementBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ObjectLiteralBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.To;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentStrategy implements TypeStrategy<DocumentShape> {

    private final DocumentShape shape;

    DocumentStrategy(DocumentShape shape) {
        this.shape = shape;
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(
            B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        TypescriptSource.Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        assig.assignedTo(rawVar.name());
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void instantiateFromRawJsonObject(B block, TsVariable rawVar) {
        block.withArgument(rawVar.name());
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        TypescriptSource.Assignment<B> assig = declare ? bb.declareConst(instantiatedVar) : bb.assign(instantiatedVar);
        assig.assignedTo(rawVar.name());
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.OBJECT;
    }

    @Override
    public String targetType() {
        return "any";
    }

    @Override
    public TsSimpleType shapeType() {
        return TsPrimitiveTypes.OBJECT;
    }

    @Override
    public Shape shape() {
        return shape;
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
        bb.assignElement().literal(queryParam).of("obj").assignedToField(fieldName).ofThis();
    }

    @Override
    public <A> A populateHttpHeader(TypescriptSource.Assignment<A> assig, String fieldName) {
        return assig.assignedToField(fieldName).ofThis();
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, ExpressionBuilder<T> ex) {
        Node nd = def.toNode();
        switch (nd.getType()) {
            case OBJECT:
                return ex.objectLiteral(lit -> {
                    walk(nd.expectObjectNode(), lit);
                });
            case ARRAY:
                return ex.arrayLiteral(alit -> {
                    walkArray(nd.expectArrayNode(), alit);
                });
            case NULL:
                return ex.expression("null");
            case BOOLEAN:
                return ex.literal(nd.expectBooleanNode().getValue());
            case NUMBER:
                NumberNode num = nd.expectNumberNode();
                return ex.literal(num.isFloatingPointNumber() ? num.getValue().doubleValue()
                        : num.getValue().longValue());
            case STRING:
                return ex.literal(nd.expectStringNode().getValue());
            default:
                throw new AssertionError(nd.getType());
        }
    }

    private <T> void walk(ObjectNode on, ObjectLiteralBuilder<T> lit) {
        on.getMembers().forEach((nameNode, nd) -> {
            To<ObjectLiteralBuilder<T>> to = lit.assigning(nameNode.getValue());
            switch (nd.getType()) {
                case ARRAY:
                    ArrayElementBuilder<ObjectLiteralBuilder<T>> arr = to.toArrayLiteral();
                    this.walkArray(nd.asArrayNode().get(), arr);
                    arr.endArrayLiteral();
                    break;
                case BOOLEAN:
                    to.to(nd.asBooleanNode().get().getValue());
                    break;
                case NULL:
                    to.toExpression("null");
                    break;
                case NUMBER:
                    NumberNode num = nd.asNumberNode().get();
                    if (num.isFloatingPointNumber()) {
                        to.to(num.getValue().doubleValue());
                    } else {
                        to.to(num.getValue().longValue());
                    }
                    break;
                case OBJECT:
                    to.toObjectLiteral(olb -> {
                        walk(nd.expectObjectNode(), olb);
                    });
                    break;
                case STRING:
                    to.toStringLiteral(nd.asStringNode().get().getValue());
                    break;
                default:
                    throw new AssertionError(nd.getType());
            }
        });
    }

    private <V> ArrayElementBuilder<V> walkArray(ArrayNode node, ArrayElementBuilder<V> arr) {
        for (Node nd : node.getElements()) {
            switch (nd.getType()) {
                case ARRAY:
                    arr = arr.arrayLiteral(aeb -> {
                        walkArray(nd.asArrayNode().get(), aeb);
                    });
                    break;
                case BOOLEAN:
                    arr = arr.literal(nd.asBooleanNode().get().getValue());
                    break;
                case NULL:
                    arr = arr.expression("null");
                    break;
                case NUMBER:
                    NumberNode n = nd.asNumberNode().get();
                    arr = arr.literal(n.isFloatingPointNumber() ? n.getValue().doubleValue() : n.getValue().longValue());
                    break;
                case OBJECT:
                    arr = arr.objectLiteral(olit -> {
                        walk(nd.asObjectNode().get(), olit);
                    });
                    break;
                case STRING:
                    arr.literal(nd.asStringNode().get().getValue());
                    break;
                default:
                    throw new AssertionError(nd.getType());
            }
        }
        return arr;
    }

}
