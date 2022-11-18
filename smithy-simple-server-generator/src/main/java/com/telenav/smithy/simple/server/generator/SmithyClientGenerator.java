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

import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.ModelElementGenerator;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
import java.nio.file.Path;
import java.util.Collection;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public class SmithyClientGenerator implements SmithyGenerator {

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(GenerationTarget.CLIENT);
    }

    @Override
    public boolean supportsLanguage(LanguageWithVersion lv) {
        return lv.isLanguage("java");
    }

    @Override
    public Collection<? extends ModelElementGenerator> generatorsFor(Shape shape, Model model,
            Path path, GenerationTarget gt, LanguageWithVersion lwv,
            SmithyGenerationSettings sgs, SmithyGenerationLogger sgl) {

        if (shape.isServiceShape()) {
            return singleton(new ServiceClientGenerator(
                    shape.asServiceShape().get(), model,
                    path, gt, lwv, sgs));
        }
        return emptySet();
    }
}
