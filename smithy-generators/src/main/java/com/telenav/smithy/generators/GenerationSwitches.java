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

/**
 * Built in configuration switches for code generation, which can be set on the
 * SmithyGenerationSettings.
 *
 * @author Tim Boudreau
 */
public enum GenerationSwitches {

    /**
     * If true, don't delete the contents of source roots that are going to be
     * generated into before running generation.
     */
    DONT_CLEAN_SOURCE_ROOTS,
    /**
     * If true, don't write files, just log what would be done.
     */
    DRY_RUN,
    /**
     * If true, increase the verbosity of logging.
     */
    VERBOSE,
    /**
     * If set, instruct generators to include debugging comments indicating the
     * lines of generator code that caused the lines of generator code to be
     * emitted.
     */
    DEBUG,
    
    DONT_GENERATE_WARNING_FILES;
}
