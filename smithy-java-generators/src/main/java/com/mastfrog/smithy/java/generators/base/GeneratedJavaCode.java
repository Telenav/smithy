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
package com.mastfrog.smithy.java.generators.base;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.generators.ModelElementGenerator.GeneratedCode;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
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
