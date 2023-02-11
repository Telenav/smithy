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

import com.mastfrog.code.generation.common.LinesBuilder;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.capitalize;
import com.telenav.smithy.extensions.FuzzyNameMatchingTrait;
import static com.telenav.smithy.ts.generator.type.TsPrimitiveTypes.STRING;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.CaseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ExpressionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.SwitchBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilderBase;
import com.telenav.smithy.utils.EnumCharacteristics;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
class DefaultEnumStrategy extends AbstractEnumStrategy {

    private final EnumCharacteristics chars;
    private final boolean fuzzy;

    DefaultEnumStrategy(EnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
        chars = characterizeEnum(shape);
        fuzzy = shape.getTrait(FuzzyNameMatchingTrait.class).isPresent();
    }

    @Override
    public <T, B extends TsBlockBuilderBase<T, B>> void
            instantiateFromRawJsonObject(B bb, TsVariable rawVar, String instantiatedVar, boolean declare,
                    boolean generateThrowIfUnrecognized) {
        if (!generateThrowIfUnrecognized) {
            // If we're generating for no throw, the value MUST be optional
            rawVar = rawVar.asOptional();
        }
        Assignment<B> assig = super.createTargetAssignment(rawVar, declare, bb, instantiatedVar);
        applyInstantiate(assig.assignedTo(), rawVar, generateThrowIfUnrecognized);
    }

    private static void permutationsOf(String what, Set<String> into) {
        into.add(what);
        into.add(what.toUpperCase());
        into.add(what.toLowerCase());
        into.add(Strings.capitalize(what.toLowerCase()));
    }

    private static Set<String> fuzzyPermutations(String name, String val) {
        Set<String> result = new TreeSet<>();
        permutationsOf(name, result);
        permutationsOf(val, result);
        return result;
    }

    private <B> void applyInstantiate(ExpressionBuilder<B> x, TsVariable rawVar, boolean generateThrowIfUnrecognized) {
//            x.element().expression(rawVar.name()).of(targetType());
        x.selfExecutingFunction(bb -> {
            SwitchBuilder<TsBlockBuilder<Void>> bl = null;
            Set<String> values = new TreeSet<>();
            for (Map.Entry<String, String> e : shape.getEnumValues().entrySet()) {
                values.add(e.getValue());
                if (bl == null) {
                    CaseBuilder<SwitchBuilder<TsBlockBuilder<Void>>> cs
                            = bb.switchStringLiteralCase(e.getValue());
                    if (fuzzy) {
                        cs = applyFuzzyPermutations(e, cs);
                    }
                    bl = cs.returningField(e.getKey()).of(targetType());
                } else {
                    CaseBuilder<SwitchBuilder<TsBlockBuilder<Void>>> cs
                            = bl.inStringLiteralCase(e.getValue());
                    if (fuzzy) {
                        cs = applyFuzzyPermutations(e, cs);
                    }
                    bl = cs.returningField(e.getKey()).of(targetType());
                }
            }
            if (bl == null) {
                if (!rawVar.optional()) {
                    bb.throwing(nb -> {
                        nb.withStringLiteralArgument(shape.getId().getName() + " is an enum "
                                + "with no enum constants and cannot ever be instantiated.");
                    });
                }
            } else {
                if (generateThrowIfUnrecognized) {
                    bl.inDefaultCase(cs -> {
                        if (rawVar.optional()) {
                            cs.lineComment("Fall through on invalid value - this field is optional");
                            cs.invoke("log")
                                    .withStringConcatenation("Not a valid value for "
                                            + targetType() + ": '")
                                    .appendExpression(rawVar.name())
                                    .append("'")
                                    .append(".  Possible values: ")
                                    .append(Strings.join(", ", values))
                                    .endConcatenation()
                                    .on("console");

                        } else {
                            cs.throwing(nb -> {
                                nb.withStringConcatenation("Not a valid value for " + targetType() + ": '")
                                        .appendExpression(rawVar.name())
                                        .append("'")
                                        .append(".  Possible values: ")
                                        .append(Strings.join(", ", values))
                                        .endConcatenation();
                            });
                        }
                    }).on(rawVar.name());
                } else {
                    bl.inDefaultCase().endBlock().on(rawVar.name());
                }
            }
        });
    }

    private CaseBuilder<SwitchBuilder<TsBlockBuilder<Void>>> applyFuzzyPermutations(Map.Entry<String, String> e, CaseBuilder<SwitchBuilder<TsBlockBuilder<Void>>> cs) {
        for (String p : fuzzyPermutations(e.getKey(), e.getValue())) {
            if (p.equals(e.getValue())) {
                continue;
            }
            cs = cs.endBlock().inStringLiteralCase(p);
        }
        return cs;
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(
            B bb, TsVariable rawVar, String instantiatedVar, boolean declare) {
        String type = rawVar.optional() ? rawVarType().typeName() + " | undefined" : rawVarType().typeName();
        Assignment<B> assig = (declare ? bb.declareConst(instantiatedVar).ofType(type) : bb.assign(instantiatedVar));
        if (rawVar.optional()) {
            assig.assignedToUndefinedIfUndefinedOr(rawVar.name() + ".toString() as " + targetType());
        } else {
            assig.assignedTo(rawVar.name() + ".toString() as " + targetType());
        }
    }

    @Override
    public TsSimpleType rawVarType() {
        return STRING;
    }

    @Override
    public TsSimpleType shapeType() {
        return new TsShapeType(shape, strategies.types(), false, false);
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, TypescriptSource.ExpressionBuilder<T> ex) {
        switch (chars) {
            case NONE:
                return ex.field(defaultValue(def)).of(targetType());
            case STRING_VALUED:
                String value = def.toNode().asStringNode().get().getValue();
                Map.Entry<String, String> targetValue = null;
                for (Map.Entry<String, String> e : shape.getEnumValues().entrySet()) {
                    if (e.getValue().equals(value)) {
                        targetValue = e;
                        break;
                    }
                }
                if (targetValue == null) {
                    throw new ExpectationNotMetException(
                            "Could not find a constant matching the default '"
                            + value + "' on " + shape.getId(), def);
                }
                return ex.field(targetValue.getKey()).of(targetType());
            case HETEROGENOUS:
                throw new UnsupportedOperationException();
            default:
                throw new AssertionError(getClass().getName() + " should not be used for an enum of " + chars
                        + ": " + shape.getId());
        }
//        return ex.element().literal(defaultValue(def)).of(targetType());
    }

    @Override
    public TypeMatchingStrategy typeTest() {
        return new EnumTypeMatchingStrategy();
    }

    static class EnumTypeMatchingStrategy implements TypeMatchingStrategy {

        @Override
        public String test(String varName, String typeName, Shape shape) {
            StringBuilder sb = new StringBuilder("typeof " + varName + " === 'string' && (");
            EnumShape es = shape.asEnumShape().get();
            Set<String> names = new TreeSet<>(es.getEnumValues().values());
            if (es.getTrait(FuzzyNameMatchingTrait.class).isPresent()) {
                for (String s : es.getEnumValues().values()) {
                    names.add(s.toUpperCase());
                    names.add(capitalize(s.toLowerCase()));
                }
            }
            for (Iterator<String> it = names.iterator(); it.hasNext();) {
                sb.append(varName).append(" === ").append("\"").append(
                        LinesBuilder.escape(it.next())).append("\"");
                if (it.hasNext()) {
                    sb.append(" || ");
                }
            }
            return sb.append(')').toString();
        }
    }
}
