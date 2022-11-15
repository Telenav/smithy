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
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.DefaultStructureGenerators.MAVEN_ARTIFACT_ID;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.DefaultStructureGenerators.MAVEN_GROUP_ID;
import com.mastfrog.util.libversion.VersionInfo;
import javax.annotation.processing.Generated;

/**
 *
 * @author Tim Boudreau
 */
final class GeneratedAnnotationContributor implements StructureContributor {

    private final Class<?> type;

    GeneratedAnnotationContributor(Class<?> type) {
        this.type = type;
    }

    GeneratedAnnotationContributor() {
        this(StructureGenerator.class);
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        cb.importing(Generated.class);
        VersionInfo vi = VersionInfo.find(Registry.class, MAVEN_GROUP_ID, MAVEN_ARTIFACT_ID);
        if (vi != null) {
            cb.annotatedWith("Generated", ab -> {
                ab.addArrayArgument("value", arr -> {
                    if (vi.foundMavenMetadata) {
                        arr.literal(vi.groupId).literal(vi.artifactId).literal(vi.version);
                    }
                    if (vi.foundGitMetadata) {
                        arr.literal(vi.shortCommitHash).literal(vi.commitDate.toInstant().toString());
                        if (vi.dirty) {
                            arr.literal("dirty");
                        } else {
                            arr.literal("clean");
                        }
                    }
                    ab.addArgument("comments", type.getSimpleName() + " in " + type.getPackageName());
                });
            });
        }
    }

}
