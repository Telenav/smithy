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
import com.mastfrog.smithy.generators.SettingsKey;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.generators.SmithyGenerator;
import com.mastfrog.util.service.ServiceProvider;
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
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;
import static software.amazon.smithy.model.shapes.ShapeType.RESOURCE;
import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(SmithyGenerator.class)
public final class SmithyServerGenerator implements SmithyGenerator {

    static final SettingsKey<Map<ShapeId, ResourceGraph>> GRAPHS_KEY
            = SettingsKey.key(Map.class);

    @Override
    public boolean supportsGenerationTarget(GenerationTarget target) {
        return target.equals(GenerationTarget.SERVER)
                || target.equals(GenerationTarget.SERVER_SPI);
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
        if (target.equals(GenerationTarget.SERVER)) {
            List<ModelElementGenerator> generators = new ArrayList<>();
            collectServerGeneratorsFor(shape, model, destSourceRoot, target, language, settings, logger,
                    generators::add);
            return generators;

        } else if (GenerationTarget.SERVER_SPI.equals(target)) {
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
        return map.putIfAbsent(svc.getId(), ResourceGraph.create(svc, mdl));
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
