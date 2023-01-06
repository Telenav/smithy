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
package com.telenav.smithy.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A generation session, which can find installed generators and run them for a
 * model.
 *
 * @author Tim Boudreau
 */
public final class SmithyGenerationSession {

    private final Set<LanguageWithVersion> languages;
    private final SmithyGenerationSettings settings;
    private final SmithyDestinations destinations;
    private final Set<GenerationTarget> generationTargets;
    private final Map<String, PostGenerateTask> postTasks = new TreeMap<>();

    SmithyGenerationSession(SmithyGenerationSettings settings, Set<LanguageWithVersion> languages,
            SmithyDestinations destinations, Set<GenerationTarget> generationTargets) {
        this.languages = languages;
        this.settings = settings;
        this.destinations = destinations;
        this.generationTargets = generationTargets;
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("Languages is empty - nothing will be generated");
        }
        if (generationTargets.isEmpty()) {
            throw new IllegalArgumentException("Generation targets is empty - nothing will be generated");
        }
    }

    public SmithyGenerationSession registerPostGenerationTask(String key, Supplier<? extends PostGenerateTask> supp) {
        postTasks.computeIfAbsent(key, k -> supp.get());
        return this;
    }

    public GenerationResults generate(Model model, Predicate<ShapeId> test,
            SmithyGenerationLogger logger) throws Exception {
        SmithyGenerationContext ctx
                = new SmithyGenerationContext(destinations, settings, this);
        return ctx.run(() -> {
            Collection<? extends SmithyGenerator> generators = generators(logger);
            if (generators.isEmpty()) {
                logger.error("No generators matched the set of languages "
                        + languages);
                return new GenerationResults(ctx, emptyList(), emptySet());
            }
            Problems problems = new Problems();
            for (SmithyGenerator gen : generators) {
                gen.prepare(model, ctx, problems);
            }
            if (problems.hasFatal()) {
                throw new IOException(problems.toString());
            }

            Map<LanguageWithVersion, Map<ShapeId, Set<ModelElementGenerator>>> generatorsForLanguage
                    = new HashMap<>();

            Set<Path> dests = new HashSet<>();
            Set<SmithyGenerator> initialized = new HashSet<>();

            for (ShapeId shapeId : model.getShapeIds()) {
                if (!"smithy.api".equals(shapeId.getNamespace())
                        && test.test(shapeId)) {

                    Shape shape = model.expectShape(shapeId);
                    if (shape.isMemberShape()) {
                        continue;
                    }
                    for (SmithyGenerator g : generators) {
                        if (initialized.add(g)) {
                            g.prepare(model, ctx, problems);
                        }
                        for (LanguageWithVersion lang : languages) {
                            if (g.supportsLanguage(lang)) {
                                Set<GenerationTarget> availableTargets
                                        = targetsFor(g, generationTargets);
                                for (GenerationTarget target : availableTargets) {
                                    Path root = destinations.sourceRootFor(target,
                                            shape, lang, settings);
                                    dests.add(root);

                                    Map<ShapeId, Set<ModelElementGenerator>> items
                                            = generatorsForLanguage.computeIfAbsent(
                                                    lang, l -> new LinkedHashMap<>());

                                    Collection<? extends ModelElementGenerator> gens
                                            = g.generatorsFor(shape, model, root,
                                                    target, lang, settings, logger);

                                    if (!gens.isEmpty()) {
                                        items.computeIfAbsent(shapeId,
                                                id -> new LinkedHashSet<>())
                                                .addAll(gens);
                                        for (ModelElementGenerator meg : gens) {
                                            meg.prepare(target, model, ctx, problems);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (problems.hasFatal()) {
                throw new IOException(problems.toString());
            }
            List<ModelElementGenerator.GeneratedCode> generated = new ArrayList<>();
            for (Map.Entry<LanguageWithVersion, Map<ShapeId, Set<ModelElementGenerator>>> e
                    : generatorsForLanguage.entrySet()) {
                for (Map.Entry<ShapeId, Set<ModelElementGenerator>> e1 : e.getValue().entrySet()) {
                    for (ModelElementGenerator meg : e1.getValue()) {
                        generated.addAll(meg.generate(
                                ctx,
                                logger.child(e1.getKey().toString())));
                    }
                }
            }
            GenerationResults uncommittedResults = new GenerationResults(ctx, generated, dests);
            while (!postTasks.isEmpty()) {
                // Post tasks can potentially add more post tasks
                List<PostGenerateTask> tasks = new ArrayList<>(postTasks.values());
                postTasks.clear();
                for (PostGenerateTask task : tasks) {
                    task.onAfterGenerate(ctx, model, uncommittedResults, ctx::registeredPaths,
                            logger.child("post-tasks"));
                }
            }
            return uncommittedResults;
        });
    }

    private Set<LanguageWithVersion> languagesFor(SmithyGenerator g) {
        Set<LanguageWithVersion> result = new LinkedHashSet<>();
        for (LanguageWithVersion l : languages) {
            if (g.supportsLanguage(l)) {
                result.add(l);
            }
        }
        return result;
    }

    Collection<? extends SmithyGenerator> generators(SmithyGenerationLogger logger) {
        Set<SmithyGenerator> result = new HashSet<>();
        if (languages.isEmpty()) {
            return result;
        }
        for (SmithyGenerator g : allGenerators()) {
            if (targetsFor(g, generationTargets).isEmpty()) {
                continue;
            }
            if (languagesFor(g).isEmpty()) {
                continue;
            }
            result.add(g);
        }
        return result;
    }

    public static Set<SmithyGenerator> allGenerators() {
        Set<SmithyGenerator> result = new LinkedHashSet<>();
        ServiceLoader.load(SmithyGenerator.class)
                .forEach(result::add);
        return result;
    }

    private Set<GenerationTarget> targetsFor(SmithyGenerator g,
            Set<GenerationTarget> generationTargets) {
        Set<GenerationTarget> result = new LinkedHashSet<>();
        for (GenerationTarget gt : generationTargets) {
            if (g.supportsGenerationTarget(gt)) {
                result.add(gt);
            }
        }
        return result;
    }
}
