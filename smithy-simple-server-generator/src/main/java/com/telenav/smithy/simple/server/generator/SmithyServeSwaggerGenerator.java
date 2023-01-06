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
package com.telenav.smithy.simple.server.generator;

import com.telenav.smithy.generators.GenerationTarget;
import static com.telenav.smithy.generators.GenerationTarget.DOCS;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.telenav.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
import java.nio.file.Path;

import static java.util.Arrays.asList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public class SmithyServeSwaggerGenerator implements SmithyGenerator {

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(DOCS);
    }

    @Override
    public boolean supportsLanguage(LanguageWithVersion lv) {
        return lv.isLanguage("java");
    }

    @Override
    public Collection<? extends ModelElementGenerator> generatorsFor(Shape shape,
            Model model, Path destSourceRoot, GenerationTarget targets,
            LanguageWithVersion language, SmithyGenerationSettings settings,
            SmithyGenerationLogger logger) {
        if (shape.isServiceShape()) {
            return asList(new ServeSwaggerGenerator(shape.asServiceShape().get(),
                    model, destSourceRoot, targets, language, settings));
        }
        return emptyList();
    }

}
