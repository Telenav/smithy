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
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.ModelElementGenerator;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;

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
    public Collection<? extends ModelElementGenerator> generatorsFor(Shape shape, Model model,
            Path destSourceRoot, GenerationTarget targets, LanguageWithVersion language, SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        if (GenerationTarget.MODEL.equals(targets)) {
            return modelGeneratorsFor(shape, model, destSourceRoot, targets, language, settings, logger);
        } else if (GenerationTarget.CLIENT.equals(targets)) {
            
        }
        return emptyList();
    }

    public Collection<? extends ModelElementGenerator> modelGeneratorsFor(Shape shape, Model model,
            Path destSourceRoot, GenerationTarget target, LanguageWithVersion language, SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        List<ModelElementGenerator> result = new ArrayList<>();
        switch (shape.getType()) {
            case BOOLEAN:
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
            case TIMESTAMP:
                break;
            case UNION:
                break;
            case LIST:
            case SET:
                result.add(new ListGenerator(shape.asListShape().get(),
                        model, language, destSourceRoot, target));
                break;

        }
        return result;
    }

}
