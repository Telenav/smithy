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
package com.telenav.smithy.ts.generator;

import com.mastfrog.code.generation.common.LinesBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.mastfrog.util.strings.Strings.decapitalize;
import com.telenav.smithy.extensions.FuzzyNameMatchingTrait;
import com.telenav.smithy.ts.generator.type.MemberStrategy;
import com.telenav.smithy.ts.generator.type.TsPrimitiveTypes;
import com.telenav.smithy.ts.generator.type.TsTypeUtils;
import com.telenav.smithy.ts.generator.type.TypeStrategies;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.PropertyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TypeIntersectionBuilder;
import com.telenav.smithy.utils.ConstraintsChecker;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import static java.util.Collections.sort;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import static software.amazon.smithy.model.shapes.ShapeType.STRUCTURE;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * Generates Smithy union types as or'd types, e.g.
 * <code>type Baz = Foo | Bar</code>, and generates an identification function
 * that can determine what type is wanted from a raw JSON object and instantiate
 * it.
 *
 * @author Tim Boudreau
 */
public final class UnionTypeGenerator extends AbstractTypescriptGenerator<UnionShape> {

    UnionTypeGenerator(UnionShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();

        PropertyBuilder<TypeIntersectionBuilder<TypescriptSource>> type = src
                .declareType(tsTypeName(shape))
                .or();

        // Pending:  We should check for repeated types that do not have
        // mutually exclusive constraints, and fail on those
        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            MemberStrategy mem = strategies.memberStrategy(e.getValue());
            String tn = mem.targetType();
            type = type.withType(tn);
        }

        PropertyBuilder<TypeIntersectionBuilder<TypescriptSource>> ft = type;
        TypeIntersectionBuilder<TypescriptSource> res = type.inferringType().exported();
        shape.getTrait(DocumentationTrait.class).ifPresent(dox -> res.docComment(dox.getValue()));
        res.close();

        generateDecodeFunction(src);
        generateBulkRawJsonConversion(src);

        c.accept(src);
    }

    private String rawJsonConversionMethodName() {
        return rawJsonConversionFunctionName(shape, strategies.types());
    }

    public static String rawJsonConversionFunctionName(UnionShape shape, TsTypeUtils types) {
        return escape(decapitalize(types.tsTypeName(shape)) + "toJSON");
    }

    static class ObjectTypesAndReturnTypes {

        final Set<String> returnTypes = new TreeSet<>();
        final Set<String> objectTypes = new TreeSet<>();
        private final int memberCount;

        ObjectTypesAndReturnTypes(UnionShape shape, TypeStrategies strategies) {
            int memberCount = 0;
            for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                memberCount++;
                MemberStrategy<?> strat = strategies.memberStrategy(e.getValue());
                returnTypes.add(strat.rawVarType().typeName());
                if (strat.isTsObject()) {
                    objectTypes.add(strat.targetType());
                }
            }
            this.memberCount = memberCount;
        }

        boolean allMembersAreObjectTypes() {
            return memberCount == objectTypes.size();
        }
    }

    public static boolean allUnionMembersAreObjectTypes(UnionShape shape, TypeStrategies strats) {
        return new ObjectTypesAndReturnTypes(shape, strats).allMembersAreObjectTypes();
    }

    private void generateBulkRawJsonConversion(TypescriptSource src) {

        ObjectTypesAndReturnTypes ot = new ObjectTypesAndReturnTypes(shape, strategies);

        if (ot.allMembersAreObjectTypes()) {
            return;
        }

        ot.returnTypes.add("undefined");
        ot.returnTypes.add("void");
        src.function(rawJsonConversionMethodName(), f -> {
            f.withArgument("val").ofType(tsTypeName(shape));
            f.returning(Strings.join(" | ", ot.returnTypes));

            f.body(bb -> {
                if (!ot.objectTypes.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String type : ot.objectTypes) {
                        if (sb.length() > 0) {
                            sb.append("|| ");
                        }
                        sb.append("val instanceof ").append(type);
                    }
//                    if (shape.getAllMembers().size() == ot.objectTypes.size()) {
//                        bb.lineComment("All types if " + tsTypeName(shape) + " map to")
//                                .lineComment("'object' at the javascript level, so they all")
//                                .lineComment("have a toJSON method - we can stop here");
//                        bb.returningInvocationOf(TO_JSON)
//                                .on("val");
//                        return;
//                    }
                    bb.iff(sb.toString())
                            .returningInvocationOf(TO_JSON)
                            .on("val");
                }
                for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                    MemberStrategy<?> strat = strategies.memberStrategy(e.getValue());
                    if (ot.objectTypes.contains(strat.targetType())) {
                        continue;
                    }
                    bb.blankLine().lineComment(strat.toString());
                    String typeTest = strat.typeTest().test("val", strat.targetType(), strat.shape());
                    ConditionalClauseBuilder<?> test = bb.iff(typeTest);
                    String resultVar = escape(decapitalize(strat.targetType() + "Result"));
                    strat.convertToRawJsonObject(test,
                            strat.rawVarType().optional(strat.canBeAbsent()).variable("val"),
                            resultVar,
                            true);
                    test.returning(resultVar);
                }
            });
        });
    }

    private void generateDecodeFunction(TypescriptSource src) {
        // This is fairly complex:  We need to drill through the structure of
        // the object, finding combinations of fields that uniquely identify
        // a raw JSON object as being a particular union variant (a union
        // type is generated as, e.g. `type Baz = Foo | Bar`, so we need to
        // differentiate the two in order to have something to return.
        String methodName = decodeMethodName(shape, tsTypeName(shape));
        List<TypeIdentificationStrategy> typeIdentificationStrategies = new ArrayList<>();

        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
            ConstraintsChecker.check(model, e.getValue());
            Shape target = model.expectShape(e.getValue().getTarget());
            boolean prim = "smithy.api".equals(target.getId().getNamespace());
            switch (target.getType()) {
                case BIG_DECIMAL:
                case BIG_INTEGER:
                case DOUBLE:
                case FLOAT:
                case INTEGER:
                case LONG:
                case SHORT:
                case BYTE:
                    typeIdentificationStrategies.add(
                            new PrimitiveTypeIdentificationStrategy<Shape>("number", target));
                    break;
                case BOOLEAN:
                    typeIdentificationStrategies.add(
                            new PrimitiveTypeIdentificationStrategy<Shape>("boolean", target));
                    break;
                case TIMESTAMP:
                    typeIdentificationStrategies.add(
                            new DateIdentificationStrategy(target));
                    break;
                case LIST:
                case SET:
                    typeIdentificationStrategies.add(
                            new ArrayLikeIdentificationStrategy(target));
                    break;
                case MAP:
                    typeIdentificationStrategies.add(
                            new PrimitiveTypeIdentificationStrategy<>("object", target));
                    break;
                case STRING:
                    typeIdentificationStrategies.add(
                            new PrimitiveTypeIdentificationStrategy<>("string", target));
                    break;
                case INT_ENUM:
                    typeIdentificationStrategies.add(
                            new IntEnumIdentificationStrategy(target.asIntEnumShape().get()));
                    break;
                case ENUM:
                    switch (characterizeEnum(target.asEnumShape().get())) {
                        case HETEROGENOUS:
                        case NONE:
                        case STRING_VALUED:
                        case STRING_VALUED_MATCHING_NAMES:
                            typeIdentificationStrategies.add(new StringEnumIdentificationStrategy(target.asEnumShape().get()));
                            break;
                        case INT_VALUED:
                            typeIdentificationStrategies.add(new StringOrNumberIdentificationStrategy(target));
                            break;
                    }
                    break;
                case DOCUMENT:
                    typeIdentificationStrategies.add(
                            new PrimitiveTypeIdentificationStrategy("object", target));
                    break;
                case BLOB:
                    typeIdentificationStrategies.add(
                            new ArrayLikeIdentificationStrategy(target));
                    break;
                case UNION:
                    typeIdentificationStrategies.add(
                            new UnionTypeIdentificationStrategy(model, target));
                    break;
                case STRUCTURE:
                    typeIdentificationStrategies.add(
                            new MembersTypeIdentificationStrategy(model, target));
                    break;
            }
        }
        // Now let each identification strategy at the top level compare notes
        // with each of the others, and winnow down the set of non-identical
        // property name+type combinations that are present
        for (TypeIdentificationStrategy s : typeIdentificationStrategies) {
            for (TypeIdentificationStrategy s1 : typeIdentificationStrategies) {
                s.examine(s1);
            }
        }
        // Sort them so the most specific are tried first
        sort(typeIdentificationStrategies);
        src.function(methodName, f -> {
            f.docComment("Decodes a raw JSON object into a specific variant of "
                    + "a " + shape.getId().getName()
                    + " (or'd union type) based on the pattern of properties present on it.");
            f.withArgument("val").ofType("any")
                    .returning().or().withType(tsTypeName(shape)).ofType("undefined");
            f.body(bb -> {
                bb.ifTypeOf("val", "undefined").statement("return").endIf();
                for (TypeIdentificationStrategy strat : typeIdentificationStrategies) {
                    bb.lineComment(strat.toString());
                    ConditionalClauseBuilder<TsBlockBuilder<Void>> test = bb.iff(strat.test("val"));
                    test.lineComment("Recognize" + strat.shape().getId().getName() + "?");

                    TypeStrategy<?> ts = this.strategies.strategy(strat.shape());
                    ts.instantiateFromRawJsonObject(test, TsPrimitiveTypes.ANY.variable("val"),
                            "result", true, false);

                    boolean exhaustive = ts.typeTest().isExhaustive();
                    if (exhaustive) {
                        test.returning("result");
                    } else {
                        test.ifDefined("result")
                                .returning("result").endIf();
                    }
                }
                bb.returning("undefined");
            });
        });
    }

    private interface TypeIdentificationStrategy extends Comparable<TypeIdentificationStrategy> {

        String test(String varName);

        Shape shape();

        /**
         * Sort order - identifiers that are more specific will return a higher
         * value.
         */
        default int priority() {
            return 0;
        }

        /**
         * Compare notes with another strategy to winnow the set of properties
         * that uniquely identify this strategy.
         */
        default void examine(TypeIdentificationStrategy other) {

        }

        @Override
        default int compareTo(TypeIdentificationStrategy other) {
            return Integer.compare(other.priority(), priority());
        }
    }

    /**
     * Matches a js/ts primitive type by name and does nothing further.
     *
     */
    private static class PrimitiveTypeIdentificationStrategy<S extends Shape> implements TypeIdentificationStrategy {

        protected final String primitiveType;
        protected final S shape;

        PrimitiveTypeIdentificationStrategy(String primitiveType, S shape) {
            this.primitiveType = primitiveType;
            this.shape = shape;
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String test(String varName) {
            StringBuilder result = new StringBuilder()
                    .append("typeof ")
                    .append(varName).append(" === '")
                    .append(primitiveType).append("'");
            shape.getTrait(RangeTrait.class).ifPresent(rt -> {
                rt.getMin().ifPresent(min -> {
                    result.append(" && ")
                            .append(varName)
                            .append(" >= ")
                            .append(min.longValue());
                });
                rt.getMax().ifPresent(max -> {
                    result.append(" && ")
                            .append(varName)
                            .append(" <= ")
                            .append(max.longValue());
                });
            });
            shape.getTrait(LengthTrait.class).ifPresent(lt -> {
                boolean isMap = shape.getType() == ShapeType.MAP;
                if (isMap) {
                    lt.getMin().ifPresent(min -> {
                        result.append(" && Object.keys(").append(varName)
                                .append(").length")
                                .append(" >= ")
                                .append(min.longValue());
                    });
                    lt.getMax().ifPresent(max -> {
                        result.append(" && Object.keys(").append(varName)
                                .append(").length")
                                .append(" <= ")
                                .append(max.longValue());
                    });

                } else {
                    lt.getMin().ifPresent(min -> {
                        result.append(" && ").append(varName)
                                .append(".length")
                                .append(" >= ")
                                .append(min.longValue());
                    });
                    lt.getMax().ifPresent(max -> {
                        result.append(" && ").append(varName)
                                .append(".length")
                                .append(" <= ")
                                .append(max.longValue());
                    });
                }
            });
            shape.getTrait(PatternTrait.class).ifPresent(pat -> {
                String regex = pat.getValue();
                result.append(" && ").append(" new RegExp(\"")
                        .append(LinesBuilder.escape(regex))
                        .append("\").test(").append(varName).append(")");
            });
            return result.toString();
        }

        @SafeVarargs
        private int countTraits(Class<? extends Trait>... c) {
            int result = 0;
            for (Class<? extends Trait> trait : c) {
                if (!Trait.class.isAssignableFrom(trait)) {
                    throw new IllegalArgumentException("Not a trait type: " + c);
                }
                if (shape.getTrait(trait).isPresent()) {
                    result++;
                }
            }
            return result;
        }

        @Override
        public int priority() {
            return countTraits(PatternTrait.class, LengthTrait.class, RangeTrait.class);
        }

        @Override
        public String toString() {
            return "primitive " + primitiveType + " for " + shape.getId().getName();
        }
    }

    private static final class StringEnumIdentificationStrategy extends PrimitiveTypeIdentificationStrategy<EnumShape> {

        public StringEnumIdentificationStrategy(EnumShape shape) {
            super("string", shape);
        }

        public String test(String varName) {
            StringBuilder base = new StringBuilder();

            Set<String> list = new TreeSet<>(this.shape.getEnumValues().values());
            if (shape.getTrait(FuzzyNameMatchingTrait.class).isPresent()) {
                for (String s : this.shape.getEnumValues().values()) {
                    list.add(s.toUpperCase());
                    list.add(capitalize(s.toLowerCase()));
                }
            }
            for (String s : list) {
                if (base.length() > 0) {
                    base.append(" || ");
                }
                base.append(varName).append(" === \"").append(LinesBuilder.escape(s)).append("\"");
            }
            base.insert(0, super.test(varName) + " && (");
            base.append(")");
            return base.toString();
        }

        @Override
        public int priority() {
            return shape.getEnumValues().size() + 1;
        }
    }

    private static final class IntEnumIdentificationStrategy extends PrimitiveTypeIdentificationStrategy<IntEnumShape> {

        public IntEnumIdentificationStrategy(IntEnumShape shape) {
            super("number", shape);
        }

        public String test(String varName) {
            StringBuilder base = new StringBuilder();

            List<Integer> list = new ArrayList<>(this.shape.getEnumValues().values());
            Collections.sort(list);
            for (Integer s : list) {
                if (base.length() > 0) {
                    base.append(" || ");
                }
                base.append(varName).append(" === ").append(s);
            }
            base.insert(0, super.test(varName) + " && (");
            base.append(")");
            return base.toString();
        }

        @Override
        public int priority() {
            return shape.getEnumValues().size();
        }
    }

    private static class StringOrNumberIdentificationStrategy implements TypeIdentificationStrategy {

        private final Shape shape;

        StringOrNumberIdentificationStrategy(Shape shape) {
            this.shape = shape;
        }

        @Override
        public String test(String varName) {
            return "typeof " + varName + " === 'number' || typeof " + varName + " === 'string'";
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String toString() {
            return "stringOrNumber";
        }

    }

    private static class DateIdentificationStrategy implements TypeIdentificationStrategy {

        private final Shape shape;

        DateIdentificationStrategy(Shape shape) {
            this.shape = shape;
        }

        @Override
        public String test(String varName) {
            // A crude but good enough regex that will match any ISO timestamp
            return "typeof varName === 'string' && /\\S*T\\S*/.test(" + varName + ")";
        }

        @Override
        public int priority() {
            return 1;
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String toString() {
            return "dates as string w/ regex";
        }

    }

    /**
     * Matches lists and sets.
     */
    private static class ArrayLikeIdentificationStrategy implements TypeIdentificationStrategy {

        private final Shape shape;

        ArrayLikeIdentificationStrategy(Shape shape) {
            this.shape = shape;
        }

        @Override
        public String test(String varName) {
            return "Array.isArray(" + varName + ")";
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String toString() {
            return "array-like";
        }
    }

    private static class MembersTypeIdentificationStrategy implements TypeIdentificationStrategy {

        final Map<String, Shape> requiredProperties = new TreeMap<>();
        final Map<String, Shape> optionalProperties = new TreeMap<>();
        private final Set<String> nonDistinct = new HashSet<>();
        private final Shape shape;

        MembersTypeIdentificationStrategy(Model model, Shape shape) {
            this.shape = shape;
            for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                String json = e.getValue().getMemberTrait(model, JsonNameTrait.class)
                        .map(JsonNameTrait::getValue).orElse(e.getKey());
                Shape target = model.expectShape(e.getValue().getTarget());
                boolean required = e.getValue().getTrait(RequiredTrait.class).isPresent();
                if (required) {
                    requiredProperties.put(json, target);
                } else {
                    optionalProperties.put(json, target);
                }
            }
        }

        private Set<String> distinctProperties() {
            Set<String> result = new TreeSet<>(requiredProperties.keySet());
            result.removeAll(nonDistinct);
            if (result.isEmpty()) {
                result.addAll(optionalProperties.keySet());
                result.removeAll(nonDistinct);
            }
            return result;
        }

        @Override
        public String test(String varName) {
            StringBuilder sb = new StringBuilder("typeof ").append(varName).append(" === 'object' && (");
            Set<String> props = distinctProperties();
            for (Iterator<String> it = props.iterator(); it.hasNext();) {
                String dp = it.next();
                sb.append(" typeof ").append(varName).append("['").append(dp).append("'] !== 'undefined'");
                if (it.hasNext()) {
                    sb.append(" ||");
                }
            }
            if (props.isEmpty()) {
                sb.append("true");
            }
            sb.append(")");
            return sb.toString();
        }

        public int priority() {
            return distinctProperties().size() + 1;
        }

        @Override
        public void examine(TypeIdentificationStrategy other) {
            if (other == this) {
                return;
            }
            if (other instanceof MembersTypeIdentificationStrategy) {
                MembersTypeIdentificationStrategy mem = (MembersTypeIdentificationStrategy) other;
                for (String k : mem.requiredProperties.keySet()) {
                    if (requiredProperties.containsKey(k) || optionalProperties.containsKey(k)) {
                        nonDistinct.add(k);
                    }
                }
            } else if (other instanceof UnionTypeIdentificationStrategy) {
                UnionTypeIdentificationStrategy u = (UnionTypeIdentificationStrategy) other;
                for (String k : u.requiredProperties.keySet()) {
                    if (requiredProperties.containsKey(k) || optionalProperties.containsKey(k)) {
                        nonDistinct.add(k);
                    }
                }
            }
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String toString() {
            return "members " + Strings.join(", ", distinctProperties()) + " for " + shape.getId().getName();
        }
    }

    private static class UnionTypeIdentificationStrategy implements TypeIdentificationStrategy {

        final Map<String, Shape> requiredProperties = new TreeMap<>();
        final Map<String, Shape> optionalProperties = new TreeMap<>();
        private final Set<String> nonDistinct = new HashSet<>();
        private final Shape shape;

        UnionTypeIdentificationStrategy(Model model, Shape shape) {
            this.shape = shape;
            for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
                String json = e.getValue().getMemberTrait(model, JsonNameTrait.class)
                        .map(JsonNameTrait::getValue).orElse(e.getKey());
                Shape target = model.expectShape(e.getValue().getTarget());

                // XXX handle the case target is not a Structure type?
                for (Map.Entry<String, MemberShape> e1 : target.getAllMembers().entrySet()) {
                    boolean required = e.getValue().getTrait(RequiredTrait.class).isPresent();
                    if (required) {
                        requiredProperties.put(json, target);
                    } else {
                        optionalProperties.put(json, target);
                    }
                }
            }
        }

        private Set<String> distinctProperties() {
            Set<String> result = new TreeSet<>(requiredProperties.keySet());
            result.removeAll(nonDistinct);
            if (result.isEmpty()) {
                result.addAll(optionalProperties.keySet());
                result.removeAll(nonDistinct);
            }
            return result;
        }

        @Override
        public String test(String varName) {
            StringBuilder sb = new StringBuilder("typeof ").append(varName).append(" === 'object' && (");
            for (Iterator<String> it = distinctProperties().iterator(); it.hasNext();) {
                String dp = it.next();
                sb.append(" typeof ").append(varName).append("['").append(dp).append("'] !== 'undefined'");
                if (it.hasNext()) {
                    sb.append(" ||");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        public int priority() {
            return distinctProperties().size() + 1;
        }

        @Override
        public void examine(TypeIdentificationStrategy other) {
            if (other == this) {
                return;
            }
            if (other instanceof MembersTypeIdentificationStrategy) {
                MembersTypeIdentificationStrategy mem = (MembersTypeIdentificationStrategy) other;
                for (String k : mem.requiredProperties.keySet()) {
                    if (requiredProperties.containsKey(k) || optionalProperties.containsKey(k)) {
                        nonDistinct.add(k);
                    }
                }
            } else if (other instanceof UnionTypeIdentificationStrategy) {
                UnionTypeIdentificationStrategy u = (UnionTypeIdentificationStrategy) other;
                for (String k : u.requiredProperties.keySet()) {
                    if (requiredProperties.containsKey(k) || optionalProperties.containsKey(k)) {
                        nonDistinct.add(k);
                    }
                }
            }
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String toString() {
            return "union-members " + Strings.join(", ", distinctProperties()) + " for " + shape.getId().getName();
        }
    }

    /**
     * Returns that name of the generated raw json decoder function that can be
     * used to identify the specific type of a union (or'd) type and instantiate
     * the right sub-variant from a raw JSON object.
     *
     * @param shape A shape
     * @param baseName The generated type name of it
     * @return A string
     */
    public static String decodeMethodName(Shape shape, String baseName) {
        return escape("infer" + baseName + "FromJSONObject");
    }
}
