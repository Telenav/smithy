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
package com.mastfrog.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import com.mastfrog.smithy.java.generators.builtin.struct.spi.StructureExtensions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 *
 * @author Tim Boudreau
 */
public class Registry {

    private final SmithyGenerationContext ctx;
    private final StructureExtensions exts;

    public Registry(SmithyGenerationContext ctx, StructureExtensions exts) {
        this.ctx = ctx;
        this.exts = exts;
        ctx.put(StructureExtensions.KEY, exts);
    }

    public Registry(SmithyGenerationContext ctx) {
        this(ctx, extensions());
    }

    static StructureExtensions extensions() {
        DefaultStructureGenerators defaults = new DefaultStructureGenerators();
        List<StructureExtensions> all = new ArrayList<>();
        Collections.sort(all, (a, b) -> {
            return Integer.compare(a.precedence(), b.precedence());
        });
        ServiceLoader.load(StructureExtensions.class).forEach(all::add);
        return defaults.precededBy(all);
    }

    public List<StructureContributor> contributors(StructureGenerationHelper helper, List<? extends StructureMember<?>> members) {
        List<StructureContributor> result = new ArrayList<>();
        ctx.with(StructureExtensions.KEY, exts, () -> {
            exts.classDocCreator(helper).ifPresent(result::add);
            exts.collectFieldGenerators(helper, members, result::add);
            exts.collectConstructorGenerators(helper, members, result::add);
            exts.collectGetterGenerators(helper, members, result::add);
            exts.hashCodeGenerator(helper, members).ifPresent(result::add);
            exts.collectOtherGenerators(helper, members, result::add);
            exts.equalsGenerator(helper).ifPresent(result::add);
            exts.toStringGenerator(helper).ifPresent(result::add);
            exts.defaultInstanceFieldGenerator(helper).ifPresent(result::add);
        });
        return result;
    }

    public static void applyGeneratedAnnotation(Class<?> generatorClass, ClassBuilder<?> cb) {
        new GeneratedAnnotationContributor(generatorClass).generate(null, cb);
    }
}
