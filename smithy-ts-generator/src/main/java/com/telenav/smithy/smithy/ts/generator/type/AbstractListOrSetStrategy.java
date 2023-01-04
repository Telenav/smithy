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
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractListOrSetStrategy extends AbstractTypeStrategy<ListShape> {

    protected final Shape member;
    protected TypeStrategy memberStrategy;
    private final String collectionType;

    protected AbstractListOrSetStrategy(ListShape shape, TypeStrategies strategies, Shape member, String collectionType) {
        super(shape, strategies);
        this.collectionType = collectionType;
        this.member = member;
        memberStrategy = strategies.strategy(member);
    }

    @Override
    public final TsSimpleType rawVarType() {
        return TsPrimitiveTypes.bestMatch(strategies.model(), shape);
    }

    protected final String tsCollectionTypeName() {
        return collectionType;
    }

    @Override
    public final String targetType() {
        boolean prim = TypeStrategies.isNotUserType(shape);
        if (prim) {
            return tsCollectionTypeName() + "<" + memberStrategy.targetType() + ">";
        } else {
            return strategies.tsTypeName(shape);
        }
    }

    @Override
    public <T, B extends TypescriptSource.TSBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
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

    @Override
    public <A> A populateHttpHeader(Assignment<A> assig, String fieldName) {
        return assig.assignedToInvocationOf("toString").onField(fieldName).ofThis();
    }
}
