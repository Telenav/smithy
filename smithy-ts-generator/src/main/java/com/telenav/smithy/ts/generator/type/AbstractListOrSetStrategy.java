package com.telenav.smithy.ts.generator.type;

import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.bestMatch;
import static com.telenav.smithy.ts.generator.type.TypeStrategies.isNotUserType;
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
        return bestMatch(strategies.model(), shape);
    }

    protected final String tsCollectionTypeName() {
        return collectionType;
    }

    @Override
    public final String targetType() {
        boolean prim = isNotUserType(shape);
        if (prim) {
            return tsCollectionTypeName() + "<" + memberStrategy.targetType() + ">";
        } else {
            return strategies.tsTypeName(shape);
        }
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void populateQueryParam(
            String fieldName, boolean required, B bb, String queryParam) {
        if (isNotUserType(shape)) {
            populateQueryParamForTsCollection(fieldName, required, bb, queryParam);
        } else {
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
    }

    @Override
    public <A> A populateHttpHeader(Assignment<A> assig, String fieldName) {
        if (isNotUserType(shape)) {
            return populateHttpHeaderForTsCollection(assig, fieldName);
        }
        return assig.assignedToInvocationOf("toString").onField(fieldName).ofThis();
    }

    protected <A> A populateHttpHeaderForTsCollection(Assignment<A> assig, String fieldName) {
        return convertToArrayOfStrings(assig, fieldName, true, true);
    }

    protected <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void populateQueryParamForTsCollection(
            String fieldName, boolean required, B bb, String queryParam) {
        if (!required) {
            Assignment<TypescriptSource.ConditionalClauseBuilder<B>> assig = bb.ifFieldDefined(fieldName).ofThis()
                    .assignLiteralRawProperty(queryParam)
                    .of("obj");
            convertToArrayOfStrings(assig, fieldName, required, false).endIf();
        } else {
            Assignment<B> assig = bb.assignLiteralRawProperty(queryParam).of("obj");
            convertToArrayOfStrings(assig, fieldName, required, false);
        }
    }

    protected <A> A convertToArrayOfStrings(Assignment<A> assig,
            String fieldName, boolean required, boolean headers) {
        if (isNotUserType(member)) {
            return assig.assignedToSelfExecutingFunction(ebb -> {
                convertToRawArray(ebb, fieldName, false);
            });
        } else {
            return assig.assignedToInvocationOf("join").withStringLiteralArgument(",")
                    .onField(fieldName).ofThis();
        }
    }

    protected void convertToRawArray(TypescriptSource.TsBlockBuilder<Void> bb, String fieldName,
            boolean headers) {
        bb.declare("arr").ofType(memberStrategy.rawVarType() + "[]")
                .assignedTo("[]");
        bb.ifDefined("this." + fieldName, ifdef -> {
            ifdef.invoke("forEach").withLambda(lam -> {
                lam.withArgument("unrawItem").inferringType()
                        .body(lbb -> {
                            if (headers) {
                                Assignment<TypescriptSource.TsBlockBuilder<Void>> assig
                                        = lbb.declare("rawItem").ofType("string");
                                lbb.lineComment("Populate A from " + memberStrategy.targetType() + " " + memberStrategy.getClass().getSimpleName());
                                memberStrategy.populateHttpHeader(assig, "unrawItem");
                            } else {
                                lbb.lineComment("Populate B from " + memberStrategy.targetType() + " " + memberStrategy.getClass().getSimpleName());
                                TsVariable var = memberStrategy.rawVarType().variable("unrawItem");
                                memberStrategy.convertToRawJsonObject(lbb, var, "rawItem", true);
                            }
                            lbb.invoke("push").withArgument("rawItem").on("arr");
                        });
            }).onField(fieldName).ofThis();
            ifdef.endIf();
        });
        bb.returningInvocationOf("join").withStringLiteralArgument(",").on("arr");
    }

    protected <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void iterAdd(B bb, TsVariable rawVar, String instantiatedVar) {
        bb.invoke("forEach").withLambda().withArgument("item").inferringType()
                .body(lbb -> {
                    TsVariable itemVar = memberStrategy.shapeType().variable("item");
                    memberStrategy.instantiateFromRawJsonObject(lbb, itemVar, "realItem", true);
                    lbb.invoke("push").withArgument("realItem").on(instantiatedVar);
                }).on(rawVar.name());
    }
}
