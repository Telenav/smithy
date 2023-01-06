/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.smithy.generators;

import static com.mastfrog.util.streams.Streams.copy;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import static java.nio.file.Files.newOutputStream;

import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import software.amazon.smithy.model.Model;

/**
 * A task, which can be registered with the session, which will run after all
 * generators have completed and before their output has been committed to disk.
 * Many will want to then use GenerationTarget.onAfterCommit() to write after
 * the commit.
 *
 * @author Tim Boudreau
 */
public interface PostGenerateTask {

    void onAfterGenerate(SmithyGenerationContext session, Model model,
            GenerationResults uncommittedResults,
            Function<? super String, ? extends Set<? extends Path>> pathRegistry,
            SmithyGenerationLogger logger) throws Exception;

    public static PostGenerateTask copyFile(Path from, Path to) {
        return (SmithyGenerationContext session, Model model,
                GenerationResults uncommittedResults,
                Function<? super String, ? extends Set<? extends Path>> pathRegistry,
                SmithyGenerationLogger logger) -> {
            uncommittedResults.onAfterCommit(paths -> {
                if (paths.contains(from)) {
                    Files.copy(from, to, COPY_ATTRIBUTES);
                }
            });
        };
    }

    public static PostGenerateTask zipCategory(String category, Path toFile) {
        return zipCategory(category, toFile, Optional.empty());
    }

    public static PostGenerateTask zipCategory(String category, Path toFile, Optional<Path> zipBase) {
        return (SmithyGenerationContext session, Model model,
                GenerationResults uncommittedResults,
                Function<? super String, ? extends Set<? extends Path>> pathRegistry,
                SmithyGenerationLogger logger) -> {
            uncommittedResults.onAfterCommit(paths -> {
                Set<? extends Path> all = pathRegistry.apply(category);
                if (!all.isEmpty()) {
                    if (!Files.exists(toFile.getParent())) {
                        Files.createDirectories(toFile.getParent());
                    }
                    logger.info("Zip category " + category + " to " + toFile);
                    try (ZipOutputStream out = new ZipOutputStream(newOutputStream(toFile,
                            WRITE, TRUNCATE_EXISTING, CREATE), UTF_8)) {
                        out.setLevel(9);
                        for (Path p : all) {
                            if (Files.isDirectory(p)) {
                                System.err.println("MARKUP DIRECTORY? " + p);
                                continue;
                            }
                            String target = zipBase.map(base -> base.resolve(
                                    p.getFileName()).toString())
                                    .orElseGet(() -> p.getFileName().toString());
                            ZipEntry en = new ZipEntry(target);
                            FileTime time = Files.getLastModifiedTime(p);
                            en.setLastModifiedTime(time);
                            en.setLastAccessTime(time);
                            en.setCreationTime(time);
                            out.putNextEntry(en);
                            copy(Files.newInputStream(p, READ), out);
                        }
                    }
                }
            });
        };
    }

    /**
     * Copies all files registered with the generation context as being in a
     * particular category into a particular directory.
     *
     * @param category The category name
     * @param toDir
     * @return a task
     */
    public static PostGenerateTask copyCategory(String category, Path toDir) {
        return (SmithyGenerationContext session, Model model,
                GenerationResults uncommittedResults,
                Function<? super String, ? extends Set<? extends Path>> pathRegistry,
                SmithyGenerationLogger logger) -> {
            uncommittedResults.onAfterCommit(paths -> {
                Set<? extends Path> toCopy = pathRegistry.apply(category);
                if (!toCopy.isEmpty()) {
                    if (!Files.exists(toDir)) {
                        Files.createDirectories(toDir);
                    }
                    logger.info("Copy " + toCopy.size() + " files for category "
                            + category + " to " + toDir);
                    for (Path p : toCopy) {
                        if (Files.isDirectory(p)) {
                            List<Path> files = Files.walk(p, 200, FileVisitOption.FOLLOW_LINKS)
                                    .filter(f -> !Files.isDirectory(f))
                                    .collect(Collectors.toCollection(ArrayList::new));
                            files.sort((a, b) -> {
                                int result = Integer.compare(a.getNameCount(), b.getNameCount());
                                if (result == 0) {
                                    result = a.compareTo(b);
                                }
                                return result;
                            });
                            for (Path sub : files) {
                                Path rel = p.relativize(sub);
                                Path target = toDir.resolve(rel);
                                if (!Files.exists(target.getParent())) {
                                    Files.createDirectories(target.getParent());
                                }
                                Files.copy(sub, target);
                            }
                        } else {
                            Path nue = toDir.resolve(p.getFileName());
                            Files.copy(p, nue);
                        }
                    }
                }
            });
        };

    }

    /**
     * Creates a relativized symbolic link.
     *
     * @param from The file which should already exist.
     * @param to The file it should be linked to - this will be translated into
     * a relative path for linking
     * @return A task
     */
    public static PostGenerateTask symlinkFile(Path from, Path to) {
        return (SmithyGenerationContext session, Model model,
                GenerationResults uncommittedResults,
                Function<? super String, ? extends Set<? extends Path>> pathRegistry,
                SmithyGenerationLogger logger) -> {
            uncommittedResults.onAfterCommit(paths -> {
                if (paths.contains(from) && Files.exists(from)) {
                    if (to.getParent() != null && !Files.exists(to.getParent())) {
                        Files.createDirectories(to.getParent());
                    }
                    Path destination = to;
                    if (destination.isAbsolute()) {
                        destination = to.relativize(from);
                    } else {
                        destination = from;
                    }
                    if (Files.exists(destination)) {
                        Files.delete(destination);
                    }
                    logger.info("Symlink " + from + " to " + to + " as " + destination);
                    Files.createSymbolicLink(destination, to);
                }
            });
        };
    }

    default PostGenerateTask andThen(PostGenerateTask next) {
        return (SmithyGenerationContext session, Model model,
                GenerationResults uncommittedResults,
                Function<? super String, ? extends Set<? extends Path>> pathRegistry,
                SmithyGenerationLogger logger) -> {
            PostGenerateTask.this.onAfterGenerate(session, model, uncommittedResults, pathRegistry, logger);
            next.onAfterGenerate(session, model, uncommittedResults, pathRegistry, logger);
        };
    }
}
