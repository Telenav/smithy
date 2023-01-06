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
package com.telenav.smithy.java.generators.ext;

import com.telenav.smithy.generators.GenerationTarget;
import java.io.IOException;
import static java.util.Collections.emptySet;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
public abstract class Extensions {

    private static Set<Extensions> extensions;

    static synchronized Set<Extensions> extensions() {
        if (extensions != null) {
            return extensions;
        }
        extensions = new HashSet<>();
        ServiceLoader.load(Extensions.class)
                .forEach(extensions::add);
        return extensions;
    }
    
    public static void prepare(ModelAssembler ma) throws IOException {
        for (Extensions ext : extensions()) {
            ext.prepareModelAssembler(ma);
        }
    }

    public static Set<ConstructorParameterAnnotator> constructorParameterAnnotators(
            Model model, Shape shape, GenerationTarget target) {
        Set<Extensions> exts = extensions();
        if (exts.isEmpty()) {
            return emptySet();
        }
        Set<ConstructorParameterAnnotator> result = new HashSet<>();
        for (Extensions e : exts) {
            e.constructorParameterAnnotators(model, shape, target, result::add);
        }
        return result;
    }

    public static Set<ConstructorAnnotator> constructorAnnotators(
            Model model, Shape shape, GenerationTarget target) {
        Set<Extensions> exts = extensions();
        if (exts.isEmpty()) {
            return emptySet();
        }
        Set<ConstructorAnnotator> result = new HashSet<>();
        for (Extensions e : exts) {
            e.constructorAnnotators(model, shape, target, result::add);
        }
        return result;
    }

    protected void constructorParameterAnnotators(Model model, Shape shape,
            GenerationTarget target, Consumer<ConstructorParameterAnnotator> consumer) {

    }

    protected void constructorAnnotators(Model model, Shape shape,
            GenerationTarget target, Consumer<ConstructorAnnotator> consumer) {

    }
    
    protected void prepareModelAssembler(ModelAssembler ma) throws IOException {
        
    }

}
