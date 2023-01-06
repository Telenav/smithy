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
