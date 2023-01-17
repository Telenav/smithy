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
import java.util.Optional;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Maps shape + language + target to a source root folder.
 *
 * @author Tim Boudreau
 */
public interface SmithyDestinations {

    Destination sourceRootFor(
            GenerationTarget generationTarget,
            Shape shape,
            LanguageWithVersion ver,
            SmithyGenerationSettings settings);

    static Destination newDestination(Path path, GenerationTarget gt, LanguageWithVersion lv) {
        return new DestinationImpl(path, gt, lv);
    }

    /**
     * A source root folder for generated code for a given shape during a
     * generation session. Includes the generation target and language as
     * specified by whatever configuration spelled out the destination
     * directory. Some code generators may want to behave differently if the
     * destination is incompletely specified. If both generationTarget() and
     * language() return Optional.empty(), then the defaults are being used.
     */
    public interface Destination {

        public Path path();

        public Optional<GenerationTarget> generationTarget();

        public Optional<LanguageWithVersion> language();
    }

}
