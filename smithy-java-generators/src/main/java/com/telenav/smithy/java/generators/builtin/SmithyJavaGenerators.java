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
package com.telenav.smithy.java.generators.builtin;

import com.telenav.smithy.generators.GenerationTarget;
import static com.telenav.smithy.generators.GenerationTarget.MODEL;
import static com.telenav.smithy.generators.GenerationTarget.MODEL_TEST;
import com.telenav.smithy.generators.Language;
import static com.telenav.smithy.generators.Language.language;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.Problems;
import com.telenav.smithy.generators.SettingsKey;
import static com.telenav.smithy.generators.SettingsKey.key;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.telenav.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
import static com.telenav.smithy.generators.GenerationTarget.MODEL_TEST;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerator;
import com.telenav.smithy.names.TypeNames;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.DOCUMENT;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * SmithyGenerator registered in META-INF/services to provide code generation
 * for Java sources.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public class SmithyJavaGenerators implements SmithyGenerator {

    private static final Language JAVA = language("java");
    private static final LanguageWithVersion JAVA_11
            = JAVA.withVersion(11);

    public static final SettingsKey<TypeNames> TYPE_NAMES
            = key(TypeNames.class);

    @Override
    public void prepare(Model model, SmithyGenerationContext ctx, Problems problems) {
        ctx.computeIfAbsent(TYPE_NAMES, () -> new TypeNames(model));
    }

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return MODEL.equals(target) || MODEL_TEST.equals(target);
    }

    @Override
    public boolean supportsLanguage(LanguageWithVersion lv) {
        return lv.isLanguage("java");
    }

    @Override
    public Collection<? extends ModelElementGenerator> generatorsFor(
            Shape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        Set<ModelElementGenerator> result = new HashSet<>();

        if (target.equals(MODEL)) {
            findGeneratorsForModelTarget(shape, model,
                    destSourceRoot, language, settings, logger, result);
        } else if (target.equals(MODEL_TEST)) {
            findTestGeneratorsForModelTarget(shape, model,
                    destSourceRoot, language, settings, logger, result);
        }

        return result;
    }

    @SuppressWarnings("deprecation")
    private void findGeneratorsForModelTarget(Shape shape, Model model,
            Path destSourceRoot, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger,
            Set<ModelElementGenerator> result) {
        switch (shape.getType()) {
            case STRUCTURE:
                result.add(StructureGenerator.create(shape.asStructureShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case INT_ENUM:
                result.add(new IntEnumModelGenerator(shape.asIntEnumShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case ENUM:
                result.add(new EnumModelGenerator(shape.asEnumShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case STRING:
                result.add(new StringModelGenerator(shape.asStringShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case TIMESTAMP:
                result.add(new TimestampModelGenerator(shape.asTimestampShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case INTEGER:
                result.add(new IntegerModelGenerator(shape.asIntegerShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case SHORT:
                result.add(new ShortModelGenerator(shape.asShortShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case BYTE:
                result.add(new ByteModelGenerator(shape.asByteShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case LONG:
                result.add(new LongModelGenerator(shape.asLongShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case DOUBLE:
                result.add(new DoubleModelGenerator(shape.asDoubleShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case FLOAT:
                result.add(new FloatModelGenerator(shape.asFloatShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case BOOLEAN:
                result.add(new BooleanModelGenerator(shape.asBooleanShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case BIG_DECIMAL:
                result.add(new BigDecimalModelGenerator(shape.asBigDecimalShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case BIG_INTEGER:
                result.add(new BigIntegerModelGenerator(shape.asBigIntegerShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case MAP:
                result.add(new MapModelGenerator(shape.asMapShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case LIST:
                if (shape.getTrait(UniqueItemsTrait.class).isPresent()) {
                    result.add(new SetModelGenerator<>(shape.asListShape().get(),
                            model, destSourceRoot, MODEL, language));
                } else {
                    result.add(new ListModelGenerator(shape.asListShape().get(),
                            model, destSourceRoot, MODEL, language));
                }
                break;
            case SET:
                result.add(new SetModelGenerator<>(shape.asSetShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case UNION:
                result.add(new UnionTypeGenerator(shape.asUnionShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case DOCUMENT:
                result.add(new DocumentGenerator(shape.asDocumentShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case BLOB:
                result.add(new BlobModelGenerator(shape.asBlobShape().get(),
                        model, destSourceRoot, MODEL, language));
                break;
            case OPERATION:
            case RESOURCE:
            case SERVICE:
                break;
            case MEMBER:
                break;
            default:
                throw new AssertionError(shape.getType());
        }
    }

    private void findTestGeneratorsForModelTarget(Shape shape, Model model,
            Path destSourceRoot, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger,
            Set<ModelElementGenerator> result) {
        switch (shape.getType()) {
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case DOUBLE:
            case FLOAT:
            case TIMESTAMP:
            case STRING:
            case BOOLEAN:
            case BIG_INTEGER:
            case BIG_DECIMAL:
                result.add(new WrapperTypeTestGenerator(shape, model, destSourceRoot, MODEL_TEST, language));
                break;
            case STRUCTURE:
                // We implement mixin types as interfaces
                if (!shape.getTrait(MixinTrait.class).isPresent()) {
                    result.add(new StructureTestGenerator(shape.asStructureShape().get(), model, destSourceRoot, MODEL_TEST, language));
                }
                break;
            case UNION:
                result.add(new UnionTypeTestGenerator(shape.asUnionShape().get(), model, destSourceRoot, MODEL_TEST, language));
                break;
            case DOCUMENT:
                result.add(new DocumentTestGenerator(shape.asDocumentShape().get(), model, destSourceRoot, MODEL_TEST, language));
                break;
            case BLOB:
                result.add(new BlobTestGenerator(shape.asBlobShape().get(), model, destSourceRoot, MODEL_TEST, language));
                break;
        }
    }

}
