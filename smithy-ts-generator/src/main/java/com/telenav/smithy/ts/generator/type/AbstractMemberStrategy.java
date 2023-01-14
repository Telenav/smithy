
package com.telenav.smithy.ts.generator.type;

import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;

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

}
