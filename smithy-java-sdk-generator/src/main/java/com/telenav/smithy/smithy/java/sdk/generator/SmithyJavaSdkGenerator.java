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
package com.telenav.smithy.smithy.java.sdk.generator;

import com.mastfrog.util.service.ServiceProvider;
import com.telenav.smithy.generators.GenerationTarget;
import static com.telenav.smithy.generators.GenerationTarget.CLIENT;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.telenav.smithy.generators.SmithyGenerator;
import java.nio.file.Path;
import java.util.Collection;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates a self-contained Java client library for a smithy service.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public final class SmithyJavaSdkGenerator implements SmithyGenerator {

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(CLIENT);
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
