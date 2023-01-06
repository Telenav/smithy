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
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerator;
import static com.telenav.smithy.java.generators.builtin.struct.impl.DefaultStructureGenerators.MAVEN_ARTIFACT_ID;
import static com.telenav.smithy.java.generators.builtin.struct.impl.DefaultStructureGenerators.MAVEN_GROUP_ID;
import com.mastfrog.util.libversion.VersionInfo;
import javax.annotation.processing.Generated;

/**
 *
 * @author Tim Boudreau
 */
public final class GeneratedAnnotationContributor implements StructureContributor {

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
                        arr.literal(vi.groupId).literal(vi.artifactId)
                                .literal(vi.version);
                    }
                    if (vi.foundGitMetadata) {
                        arr.literal(vi.shortCommitHash).literal(
                                vi.commitDate.toInstant().toString());
                        if (vi.dirty) {
                            arr.literal("dirty");
                        } else {
                            arr.literal("clean");
                        }
                    }
                    arr.literal(type.getSimpleName());
                    ab.addArgument("comments", type.getSimpleName() + " in "
                            + type.getPackageName());
                });
            });
        }
    }

}
