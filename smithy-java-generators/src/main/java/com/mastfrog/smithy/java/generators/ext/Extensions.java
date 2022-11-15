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
package com.mastfrog.smithy.java.generators.ext;

import com.mastfrog.smithy.generators.GenerationTarget;
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
