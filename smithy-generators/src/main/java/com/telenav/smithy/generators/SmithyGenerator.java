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

import java.nio.file.Path;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public interface SmithyGenerator {

    boolean supportsGenerationTarget(GenerationTarget target);

    boolean supportsLanguage(LanguageWithVersion lv);

    default void prepare(Model model, SmithyGenerationContext ctx, Problems problems) {
        // do nothing
    }

    Collection<? extends ModelElementGenerator> generatorsFor(
            Shape shape,
            Model model,
            Path destSourceRoot,
            GenerationTarget targets,
            LanguageWithVersion language,
            SmithyGenerationSettings settings,
            SmithyGenerationLogger logger);

    /**
     * Some languages require that elements be added in topological dependency
     * order to avoid forward references, while a Smithy model has no such
     * requirement. This method allows a SmithyGenerator to collect the subset
     * of generators it knows the type of and knows how to sort, and return
     * <i>just those generators</i> them in a new ordered list; they will then
     * be removed and replaced in the aggregate list of all generators to run,
     * in the provided order.
     *
     * @param gens A collection of model element generators, some of which were
     * presumably provided by this SmithyGenerator.
     * 
     * @return A subset of the original collection in the preferred order of
     * generation.
     */
    default List<? extends ModelElementGenerator> subsortGenerators(Collection<? extends ModelElementGenerator> gens) {
        return emptyList();
    }
}
