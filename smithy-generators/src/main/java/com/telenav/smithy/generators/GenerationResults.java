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

import com.mastfrog.function.throwing.ThrowingConsumer;

import static com.mastfrog.util.file.FileUtils.deltree;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.Collection;
import static java.util.Collections.unmodifiableSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory results of code-generation - call commit() to write them to disk.
 *
 * @author Tim Boudreau
 */
public final class GenerationResults {

    private final List<ModelElementGenerator.GeneratedCode> generated;
    private final SmithyGenerationContext ctx;
    private final Set<Path> roots;
    private final Set<ThrowingConsumer<Set<? extends Path>>> postCommitTasks = new LinkedHashSet<>();

    GenerationResults(SmithyGenerationContext ctx,
            List<ModelElementGenerator.GeneratedCode> generated,
            Set<Path> roots) {
        this.generated = generated;
        this.ctx = ctx;
        this.roots = roots;
    }

    public GenerationResults onAfterCommit(ThrowingConsumer<Set<? extends Path>> run) {
        postCommitTasks.add(run);
        return this;
    }

    private void clean(boolean dryRun) throws IOException {
        if (ctx.settings().is(GenerationSwitches.DONT_CLEAN_SOURCE_ROOTS)) {
            return;
        }
        if (!dryRun) {
            for (Path root : roots) {
                deleteChildrenOf(root);
            }
        }
    }

    private void deleteChildrenOf(Path path) throws IOException {
        if (Files.exists(path)) {
            Set<Path> all = new LinkedHashSet<>();
            Files.list(path).filter(p -> Files.isDirectory(p)).forEachOrdered(all::add);
            for (Path p : all) {
                deltree(p);
            }
        }
    }

    @Override
    public String toString() {
        return generated.size() + " source files.";
    }

    private boolean checkDestination(Path path, Collection<? super Path> problemPaths) {
        boolean underRoot = false;
        for (Path p : roots) {
            underRoot = path.startsWith(p);
            if (underRoot) {
                break;
            }
        }
        if (!underRoot) {
            problemPaths.add(path);
            return true;
        }
        return false;
    }

    public Set<Path> commit() throws Exception {
        boolean dryRun = ctx.settings().is(GenerationSwitches.DRY_RUN);
        clean(dryRun);
        Set<Path> paths = new LinkedHashSet<>();
        ctx.run(() -> {
            Set<Path> problems = new HashSet<>();
            for (ModelElementGenerator.GeneratedCode g : generated) {
                checkDestination(g.destination(), problems);
            }
            if (!problems.isEmpty()) {
                throw new IOException("Some generators want to "
                        + "generate source files outside of the source roots"
                        + " provided: " + problems + " source roots " + roots);
            }
            for (ModelElementGenerator.GeneratedCode g : generated) {
                g.write(dryRun);
                paths.add(g.destination());
            }
        });
        if (!ctx.settings().is(GenerationSwitches.DONT_GENERATE_WARNING_FILES)) {
            generateWarningFiles(paths);
        }
        for (ThrowingConsumer<Set<? extends Path>> run : postCommitTasks) {
            run.accept(unmodifiableSet(paths));
        }
        return paths;
    }

    private static void generateWarningFiles(Set<Path> paths) throws Exception {
        Set<Path> parents = new HashSet<>();
        for (Path p : paths) {
            parents.add(p.toRealPath().getParent());
        }
        byte[] txt = ("GENERATED CODE\n==============\n\n"
                + "This directory, and perhaps many of its parents, \n"
                + "will be DELETED and recreated the next time code \n"
                + "generation is run.\n\n"
                + "Do not put ANYTHING here that you want to keep.\n").getBytes(UTF_8);
        for (Path par : parents) {
            Path important = par.resolve("000-IMPORTANT.txt");
            if (!Files.exists(important)) {
                try (OutputStream out = Files.newOutputStream(important,
                        WRITE, TRUNCATE_EXISTING, CREATE)) {
                    out.write(txt);
                }
            }
        }
    }

}
