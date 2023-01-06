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
package com.telenav.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.spi.StructureExtensions;
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
