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
