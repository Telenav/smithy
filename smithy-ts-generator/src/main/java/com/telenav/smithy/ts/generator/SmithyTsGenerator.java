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

import static com.telenav.smithy.generators.FeatureBridge.MARKUP_GENERATION_PRESENT;
import com.telenav.smithy.utils.EnumCharacteristics;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.Problems;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.telenav.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
import static com.telenav.smithy.utils.EnumCharacteristics.characterizeEnum;
import com.telenav.smithy.utils.ResourceGraph;
import com.telenav.smithy.utils.ResourceGraphs;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author timb
 */
@ServiceProvider(SmithyGenerator.class)
public class SmithyTsGenerator implements SmithyGenerator {

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(GenerationTarget.MODEL);
    }

    @Override
    public boolean supportsLanguage(LanguageWithVersion lv) {
        return lv.isLanguage("typescript") || lv.isLanguage("javascript");
    }

    @Override
    public void prepare(Model model, SmithyGenerationContext ctx, Problems problems) {
        ctx.computeIfAbsent(MARKUP_GENERATION_PRESENT, () -> true);
    }

    @Override
    public Collection<? extends ModelElementGenerator> generatorsFor(Shape shape, Model model,
            Path destSourceRoot, GenerationTarget targets, LanguageWithVersion language, SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        if (GenerationTarget.MODEL.equals(targets)) {
            return modelGeneratorsFor(shape, model, destSourceRoot, targets, language, settings, logger);
        } else if (GenerationTarget.CLIENT.equals(targets)) {

        }
        return emptyList();
    }

    public Collection<? extends ModelElementGenerator> modelGeneratorsFor(Shape shape, Model model,
            Path destSourceRoot, GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        List<ModelElementGenerator> result = new ArrayList<>();
        switch (shape.getType()) {
            case SERVICE:
                result.add(new BrowserSDKGenerator(shape.asServiceShape().get(),
                        model, language, destSourceRoot, target));
                result.add(new BrowserUIDomGenerator(shape.asServiceShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case BOOLEAN:
                result.add(new BooleanWrapperGenerator(shape.asBooleanShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case STRING:
                result.add(new StringWrapperGenerator(shape.asStringShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case STRUCTURE:
                result.add(new SimpleStructureGenerator(shape.asStructureShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case BYTE:
                result.add(new NumberWrapperGenerator(shape.asByteShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case INTEGER:
                result.add(new NumberWrapperGenerator(shape.asIntegerShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case DOUBLE:
                result.add(new NumberWrapperGenerator(shape.asDoubleShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case FLOAT:
                result.add(new NumberWrapperGenerator(shape.asFloatShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case LONG:
                result.add(new NumberWrapperGenerator(shape.asLongShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case SHORT:
                result.add(new NumberWrapperGenerator(shape.asShortShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case BIG_DECIMAL:
                result.add(new NumberWrapperGenerator(shape.asBigDecimalShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case BIG_INTEGER:
                result.add(new NumberWrapperGenerator(shape.asBigIntegerShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case INT_ENUM:
                result.add(new IntEnumGenerator(shape.asIntEnumShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case LIST:
            case SET:
                result.add(new ListGenerator(shape.asListShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case ENUM:
                EnumCharacteristics chars = characterizeEnum(shape.asEnumShape().get());
                switch (chars) {
                    case STRING_VALUED_MATCHING_NAMES:
                        result.add(new StringEnumGenerator(shape.asEnumShape().get(),
                                model, language, destSourceRoot, target));
                        break;
                    case STRING_VALUED:
                    case INT_VALUED: // these don't actually exist in smithy - delete
//                        throw new UnsupportedOperationException("Huh? " + chars
//                               + " for " + shape.getId().getName() );
                    case HETEROGENOUS:
                    case NONE:
                        result.add(new GeneralEnumGenerator(shape.asEnumShape().get(),
                                model, language, destSourceRoot, target));
                }
                break;
            case MAP:
                result.add(new MapGenerator(shape.asMapShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case TIMESTAMP:
                result.add(new TimestampWrapperGenerator(shape.asTimestampShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case UNION:
                result.add(new UnionTypeGenerator(shape.asUnionShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case DOCUMENT:
                result.add(new DocumentGenerator(shape.asDocumentShape().get(),
                        model, language, destSourceRoot, target));
                break;
            case BLOB:
                throw new UnsupportedOperationException("Type not supported: " + shape.getType()
                        + " for " + shape.getId());
        }
        return result;
    }

    @Override
    public List<? extends ModelElementGenerator> subsortGenerators(Collection<? extends ModelElementGenerator> gens) {
        List<AbstractTypescriptGenerator<?>> ours = new ArrayList<>();
        for (ModelElementGenerator g : gens) {
            if (g instanceof AbstractTypescriptGenerator<?>) {
                ours.add((AbstractTypescriptGenerator<?>) g);
            }
        }
        ours.sort(SmithyTsGenerator::compareTypescriptGenerators);
        System.out.println("RE-SORT SORTED " + ours.size() + " generators");
        ours.forEach(gen -> System.out.println(" * " + gen));
        return ours;
    }

    private static <A extends Shape, B extends Shape> int compareTypescriptGenerators(AbstractTypescriptGenerator<A> a, AbstractTypescriptGenerator<B> b) {
        if (a.model() != b.model()) {
            return 0;
        }
        ResourceGraph graph = ResourceGraphs.graph(a.model());
        if (graph == null) {
            System.out.println("NO MODEL FOR " + a.shape().getId());
            return 0;
        }
        if (graph.children(a.shape()).contains(b.shape())) {
            return -1;
        } else if (graph.children(b.shape()).contains(a.shape())) {
            return 1;
        }
//        if (graph.closure(a.shape()).contains(b.shape())) {
//            return 1;
//        } else if (graph.closure(b.shape()).contains(a.shape())) {
//            return -1;
//        }
        return 0;
    }

}
