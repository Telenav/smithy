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
import static com.telenav.smithy.generators.GenerationTarget.SERVER;
import static com.telenav.smithy.generators.GenerationTarget.SERVER_SPI;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.ModelElementGenerator;
import com.telenav.smithy.generators.SettingsKey;
import static com.telenav.smithy.generators.SettingsKey.key;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import com.telenav.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
import com.telenav.smithy.utils.ResourceGraph;
import static com.telenav.smithy.utils.ResourceGraphs.graphContaining;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public final class SmithyServerGenerator implements SmithyGenerator {

    static final SettingsKey<Map<ShapeId, ResourceGraph>> GRAPHS_KEY
            = key(Map.class);

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(SERVER)
                || target.equals(SERVER_SPI);
    }

    @Override
    public boolean supportsLanguage(LanguageWithVersion lv) {
        return lv.isLanguage("java");
    }

    @Override
    public Collection<? extends ModelElementGenerator> generatorsFor(Shape shape,
            Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        if (!language.isLanguage("java")) {
            return emptyList();
        }
        if (shape.getType() == SERVICE) {
            maybeBuildGraph(shape.asServiceShape().get(), model);
        }
        if (target.equals(SERVER)) {
            List<ModelElementGenerator> generators = new ArrayList<>();
            collectServerGeneratorsFor(shape, model, destSourceRoot, target, language, settings, logger,
                    generators::add);
            return generators;

        } else if (SERVER_SPI.equals(target)) {
            List<ModelElementGenerator> generators = new ArrayList<>();
            collectServerSpiGeneratorsFor(shape, model, destSourceRoot, target, language, settings, logger,
                    generators::add);
            return generators;
        }
        return emptyList();
    }

    static ResourceGraph maybeBuildGraph(ServiceShape svc, Model mdl) {
        Map<ShapeId, ResourceGraph> map = SmithyGenerationContext.get().computeIfAbsent(GRAPHS_KEY, ()
                -> new HashMap<>());
        return map.putIfAbsent(svc.getId(), graphContaining(mdl, svc));
    }

    static ResourceGraph graph(Shape forShape) {
        Map<ShapeId, ResourceGraph> map = SmithyGenerationContext.get().computeIfAbsent(GRAPHS_KEY, ()
                -> new HashMap<>());
        if (map.containsKey(forShape.getId())) {
            return map.get(forShape.getId());
        }
        for (Map.Entry<ShapeId, ResourceGraph> e : map.entrySet()) {
            if (e.getValue().contains(forShape)) {
                return e.getValue();
            }
        }
        return null;
    }

    private void collectServerGeneratorsFor(Shape shape,
            Model model,
            Path destSourceRoot,
            GenerationTarget targets,
            LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger,
            Consumer<ModelElementGenerator> c) {
        switch (shape.getType()) {
            case SERVICE:
                ServiceShape svc = shape.asServiceShape().get();
                c.accept(new ServiceGenerator(
                        svc,
                        model, destSourceRoot, targets, language));
//                c.accept(new ServiceOperationAuthGenerator(svc,
//                        model, destSourceRoot, targets, language));
                break;
            case RESOURCE:
                c.accept(new ResourceGenerator(
                        shape.asResourceShape().get(),
                        model, destSourceRoot, targets, language));
                break;
            case OPERATION:
                c.accept(new OperationGenerator(
                        shape.asOperationShape().get(),
                        model, destSourceRoot, targets, language));
//                c.accept(new OperationInterfaceGenerator(
//                        shape.asOperationShape().get(),
//                        model, destSourceRoot, targets, language));
                break;
        }
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
