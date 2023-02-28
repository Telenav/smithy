/*
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.smithy.server.spi.generator;

import com.mastfrog.util.service.ServiceProvider;
import com.telenav.smithy.generators.GenerationTarget;
import static com.telenav.smithy.generators.GenerationTarget.SERVER;
import static com.telenav.smithy.generators.GenerationTarget.SERVER_SPI;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.telenav.smithy.generators.SmithyGenerator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;
import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;

/**
 * Generates service interfaces for smithy servers.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public final class SmithyServerSpiGenerator implements SmithyGenerator {

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(SERVER_SPI);
    }

    @Override
    public boolean supportsLanguage(LanguageWithVersion lv) {
        return lv.isLanguage("java");
    }

    @Override
    public Collection<? extends ModelElementGenerator> generatorsFor(Shape shape, Model model,
            Path destSourceRoot, GenerationTarget targets, LanguageWithVersion language, SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        List<ModelElementGenerator> generators = new ArrayList<>();
        collectServerSpiGeneratorsFor(shape, model, destSourceRoot, targets, language, settings, logger,
                generators::add);
        return generators;

    }

    private void collectServerSpiGeneratorsFor(Shape shape,
            Model model,
            Path destSourceRoot,
            GenerationTarget targets,
            LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger,
            Consumer<ModelElementGenerator> c) {
        switch (shape.getType()) {
            case SERVICE:
                ServiceShape svc = shape.asServiceShape().get();
                c.accept(new ServiceOperationAuthGenerator(svc,
                        model, destSourceRoot, targets, language));
                break;
            case OPERATION:
                c.accept(new OperationInterfaceGenerator(
                        shape.asOperationShape().get(),
                        model, destSourceRoot, targets, language));
                break;
        }
    }

}
