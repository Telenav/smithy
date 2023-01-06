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

import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.ts.generator.type.TsPrimitiveTypes;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.PropertyBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TypeIntersectionBuilder;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import java.nio.file.Path;
import java.util.ArrayList;
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
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
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

        PropertyBuilder<TypeIntersectionBuilder<TypescriptSource>> type = src.declareType(tsTypeName(shape)).or();

        for (Iterator<Map.Entry<String, MemberShape>> it = shape.getAllMembers().entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, MemberShape> e = it.next();
            Shape target = model.expectShape(e.getValue().getTarget());
            String tn = typeNameOf(target, true);
            type = type.withType(tn);
        }

        PropertyBuilder<TypeIntersectionBuilder<TypescriptSource>> ft = type;
        TypeIntersectionBuilder<TypescriptSource> res = type.inferringType();
        shape.getTrait(DocumentationTrait.class).ifPresent(dox -> res.docComment(dox.getValue()));
        res.close();

        generateDecodeFunction(src);

        c.accept(src);
    }

    private void generateDecodeFunction(TypescriptSource src) {
        String methodName = decodeMethodName(shape, tsTypeName(shape));
        List<TypeIdentificationStrategy> strategies = new ArrayList<>();

        for (Map.Entry<String, MemberShape> e : shape.getAllMembers().entrySet()) {
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
                    strategies.add(new PrimitiveTypeIdentificationStrategy("number", target));
                    break;
                case BOOLEAN:
                    strategies.add(new PrimitiveTypeIdentificationStrategy("boolean", target));
                    break;
                case TIMESTAMP:
                    strategies.add(new DateIdentificationStrategy(target));
                    break;
                case LIST:
                case SET:
                    strategies.add(new ArrayLikeIdentificationStrategy(target));
                    break;
                case MAP:
                    strategies.add(new PrimitiveTypeIdentificationStrategy("object", target));
                    break;
                case STRING:
                    strategies.add(new PrimitiveTypeIdentificationStrategy("string", target));
                    break;
                case INT_ENUM:
                    strategies.add(new PrimitiveTypeIdentificationStrategy("number", target));
                    break;
                case ENUM:
                    switch (characterizeEnum(target.asEnumShape().get())) {
                        case HETEROGENOUS:
                        case NONE:
                        case STRING_VALUED:
                        case STRING_VALUED_MATCHING_NAMES:
                            strategies.add(new PrimitiveTypeIdentificationStrategy("string", target));
                            break;
                        case INT_VALUED:
                            strategies.add(new StringOrNumberIdentificationStrategy(target));
                            break;
                    }
                    break;
                case DOCUMENT:
                    strategies.add(new PrimitiveTypeIdentificationStrategy("object", target));
                    break;
                case BLOB:
                    strategies.add(new ArrayLikeIdentificationStrategy(target));
                    break;
                case UNION:
                    strategies.add(new UnionTypeIdentificationStrategy(model, target));
                    break;
                case STRUCTURE:
                    strategies.add(new MembersTypeIdentificationStrategy(model, target));
                    break;
            }
        }
        for (TypeIdentificationStrategy s : strategies) {
            for (TypeIdentificationStrategy s1 : strategies) {
                s.examine(s1);
            }
        }
        sort(strategies);
        src.function(methodName, f -> {
            f.withArgument("val").ofType("any")
                    .returning().or().withType(tsTypeName(shape)).ofType("undefined");
            f.body(bb -> {
                bb.ifTypeOf("val", "undefined").returning("undefined");
                
                bb.lineComment("Have " + strategies.size() + " strategies.");
                for (TypeIdentificationStrategy strat : strategies) {
                    bb.lineComment(strat.toString());
                    ConditionalClauseBuilder<TsBlockBuilder<Void>> test = bb.iff(strat.test("val"));
                    test.lineComment("pending - " + strat.shape().getId().getName());
                    
                    TypeStrategy ts = this.strategies.strategy(strat.shape());
                    ts.instantiateFromRawJsonObject(test, TsPrimitiveTypes.ANY.variable("val"), "result", true);
                    test.returning("result");
//                    test.endIf();
                }
                bb.returning("undefined");
            });
        });
    }

    interface TypeIdentificationStrategy extends Comparable<TypeIdentificationStrategy> {

        String test(String varName);

        Shape shape();

        default int priority() {
            return 0;
        }

        default void examine(TypeIdentificationStrategy other) {

        }

        default int compareTo(TypeIdentificationStrategy other) {
            return Integer.compare(other.priority(), priority());
        }
    }

    static class PrimitiveTypeIdentificationStrategy implements TypeIdentificationStrategy {

        private final String primitiveType;
        private final Shape shape;

        public PrimitiveTypeIdentificationStrategy(String primitiveType, Shape shape) {
            this.primitiveType = primitiveType;
            this.shape = shape;
        }

        @Override
        public Shape shape() {
            return shape;
        }

        @Override
        public String test(String varName) {
            return "typeof " + varName + " === '" + primitiveType + "'";
        }

        public String toString() {
            return "primitive " + primitiveType + " for " + shape.getId().getName();
        }
    }

    static class StringOrNumberIdentificationStrategy implements TypeIdentificationStrategy {

        private final Shape shape;

        public StringOrNumberIdentificationStrategy(Shape shape) {
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

        public String toString() {
            return "stringOrNumber";
        }

    }

    static class DateIdentificationStrategy implements TypeIdentificationStrategy {

        private final Shape shape;

        public DateIdentificationStrategy(Shape shape) {
            this.shape = shape;
        }

        @Override
        public String test(String varName) {
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

    static class ArrayLikeIdentificationStrategy implements TypeIdentificationStrategy {

        private final Shape shape;

        public ArrayLikeIdentificationStrategy(Shape shape) {
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

        public String toString() {
            return "array-like";
        }
    }

    static class MembersTypeIdentificationStrategy implements TypeIdentificationStrategy {

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

    static class UnionTypeIdentificationStrategy implements TypeIdentificationStrategy {

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

        public String toString() {
            return "union-members " + Strings.join(", ", distinctProperties()) + " for " + shape.getId().getName();
        }
    }

    public static String decodeMethodName(Shape shape, String baseName) {
        return escape("infer" + baseName + "FromJSONObject");
    }
}
