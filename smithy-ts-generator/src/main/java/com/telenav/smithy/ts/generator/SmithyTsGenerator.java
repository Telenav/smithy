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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.MAP;
import static software.amazon.smithy.model.shapes.ShapeType.SET;

/**
 *
 * @author timb
 */
@ServiceProvider(SmithyGenerator.class)
public class SmithyTsGenerator implements SmithyGenerator {

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(GenerationTarget.MODEL)
                || target.equals(GenerationTarget.MODEL_TEST);
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
        } else if (GenerationTarget.MODEL_TEST.equals(targets)) {
            return modelTestGeneratorsFor(shape, model, destSourceRoot, targets, language, settings, logger);
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

    public Collection<? extends ModelElementGenerator> modelTestGeneratorsFor(Shape shape, Model model,
            Path destSourceRoot, GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        List<ModelElementGenerator> result = new ArrayList<>();
        switch (shape.getType()) {
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case BOOLEAN:
            case DOUBLE:
            case ENUM:
            case INTEGER:
            case FLOAT:
            case LONG:
            case BYTE:
            case INT_ENUM:
            case SHORT:
            case STRING:
            case STRUCTURE:
            case LIST:
            case MAP:
            case SET:
            case TIMESTAMP:
                result.add(new GeneralTestGenerator<>(shape, model, language,
                        destSourceRoot, target));
                break;
            case BLOB:
            case DOCUMENT:
            case UNION:
                break;
        }
        return result;
    }
}
