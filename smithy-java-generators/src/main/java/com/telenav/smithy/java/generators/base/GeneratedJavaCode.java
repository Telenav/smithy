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
package com.telenav.smithy.java.generators.base;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.generators.ModelElementGenerator.GeneratedCode;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds a ClassBuilder and writes its output when the time comes.
 *
 * @author Tim Boudreau
 */
final class GeneratedJavaCode implements GeneratedCode {

    private final Path destDir;

    private final ClassBuilder<String> classBuilder;
    private final SmithyGenerationLogger log;

    GeneratedJavaCode(Path destDir, ClassBuilder<String> classBuilder, SmithyGenerationLogger log) {
        this.destDir = destDir;
        this.classBuilder = classBuilder;
        this.log = log;
    }

    @Override
    public Path destination() {
        return destDir.resolve(classBuilder.packageName().replace('.', '/'))
                .resolve(classBuilder.className() + ".java");
    }

    @Override
    public void write(boolean dryRun) throws IOException {
        if (!dryRun) {
            if (!Files.exists(destDir)) {
                Files.createDirectories(destDir);
            }
        }
        Path dest = destination().toAbsolutePath();
        log.debug((dryRun ? "(dry-run) " : "") + "Save " + dest);
        if (!dryRun) {
            if (!Files.exists(dest.getParent())) {
                Files.createDirectories(dest.getParent());
            }
            classBuilder.save(dest.getParent());
        }
    }

    @Override
    public String toString() {
        return classBuilder.fqn() + " at " + destination();
    }

}
