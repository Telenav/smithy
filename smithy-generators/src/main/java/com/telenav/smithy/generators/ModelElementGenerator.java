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
import java.util.Collection;
import software.amazon.smithy.model.Model;

/**
 *
 * @author Tim Boudreau
 */
public interface ModelElementGenerator {

    default void prepare(
            GenerationTarget target,
            Model model,
            SmithyGenerationContext ctx,
            Problems problems) {

    }

    Collection<? extends GeneratedCode> generate(
            SmithyGenerationContext ctx,
            SmithyGenerationLogger log);

    public interface GeneratedCode {

        Path destination();

        void write(boolean dryRun) throws IOException;
    }
}
