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
package com.telenav.smithy.ts.vogon;

import com.mastfrog.code.generation.common.CodeGenerator;
import com.mastfrog.code.generation.common.LinesBuilder;

/**
 * Base class for typescript generators.
 *
 * @author Tim Boudreau
 */
public abstract class TypescriptCodeGenerator implements CodeGenerator {

    @Override
    public String toString() {
        LinesBuilder lb = newLinesBuilder();
        generateInto(lb);
        return lb.toString();
    }
}
