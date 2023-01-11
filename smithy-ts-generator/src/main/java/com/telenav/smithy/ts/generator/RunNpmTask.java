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
package com.telenav.smithy.ts.generator;

import com.telenav.smithy.generators.GenerationResults;
import com.telenav.smithy.generators.PostGenerateTask;
import com.telenav.smithy.generators.SmithyGenerationContext;
import static com.telenav.smithy.generators.SmithyGenerationContext.MARKUP_PATH_CATEGORY;
import com.telenav.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import software.amazon.smithy.model.Model;

/**
 *
 * @author Tim Boudreau
 */
class RunNpmTask implements PostGenerateTask {

    private static final Set<Path> WARNED = new HashSet<>();
    private final SmithyGenerationContext context;
    private final Path runIn;
    private final String[] targets;
    private final String[] scanForBuiltMarkupIn;

    RunNpmTask(SmithyGenerationContext context, Path tsSourcePath) {
        this.context = context;
        Path target = findNodeProjectRoot(tsSourcePath);
        if (target == null) {
            runIn = null;
            targets = null;
            scanForBuiltMarkupIn = null;
//            throw new IllegalArgumentException("No package.json in "
//                    + tsSourcePath + " or any of its parents");
        } else {
            this.runIn = target.toAbsolutePath();
            targets = context.settings().getString("node-targets").orElse("clean,build,dist").split("[, ]+");
            scanForBuiltMarkupIn = context.settings().getString("node-markup-relative-dests")
                    .orElse("dist").split("[, ]+");
        }
    }

    private static void warn(Path path) {
        try {
            path = path.toAbsolutePath();
            path = path.toRealPath();
        } catch (IOException e) {
            // ignore
        }
        if (WARNED.add(path)) {
            System.out.println("\n\n***************** NO package.json in typescript project ****************\n\n"
                    + "No package.json could be found in or in parents of\n"
                    + path
                    + "\n\nTypically you want to generate typescript into an npm managed project "
                    + "\nwhich will run the typescript compiler on them.  The results can then "
                    + "\n be scanned for markup which will be bundled with your server application."
                    + "\n\n*********************************************************************************\n\n");
        }
    }

    private static Path findNodeProjectRoot(Path path) {
        if (path == null) {
            return null;
        }
        if (Files.exists(path.resolve("package.json"))) {
            return path;
        }
        return findNodeProjectRoot(path.getParent());
    }

    @Override
    public void onAfterGenerate(SmithyGenerationContext session, Model model,
            GenerationResults uncommittedResults,
            Function<? super String, ? extends Set<? extends Path>> pathRegistry,
            SmithyGenerationLogger logger) throws Exception {
        if (targets == null) {
            return;
        }
        if (targets.length > 0) {
            uncommittedResults.onAfterCommit(generated -> {
                Path dir = runIn.toAbsolutePath().toRealPath();
                boolean allSucceeded = true;
                // If uninitialized, we need to run npm install
                if (!Files.exists(runIn.resolve("node_modules"))) {
                    allSucceeded &= runNpm("install",
                            logger.child("npm").child("install"), dir);
                }
                for (String target : targets) {
                    allSucceeded &= runNpm(target,
                            logger.child("npm").child(target.trim()), dir);
                }
                scanForMarkupFiles(dir).forEach(path -> {
                    logger.info("Markup file: " + path);
                    context.registerPath(MARKUP_PATH_CATEGORY, path);
                });
            });
        }
    }

    static boolean NPM_ABSENT = false;

    private boolean runNpm(String target, SmithyGenerationLogger logger, Path dir)
            throws IOException, InterruptedException, ExecutionException {

        if (NPM_ABSENT) {
            return false;
        }

        String[] args;
        String binary = npmBinary().toString();
        boolean mayNotHaveNPM = "npm".equals(binary);

        if ("install".equals(target)) {
            args = new String[]{binary, "install"};
        } else {
            args = new String[]{binary, "run", target};
        }
        int exitCode = -1;
        try {
            try {
                Process proc = new ProcessBuilder().command(args)
                        .directory(dir.toFile())
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .start();
                logger.info("Run " + Strings.join(' ', args) + " in " + dir);
                exitCode = proc.onExit().get().exitValue();
                // Don't fail the build - not everyone building may have NPM installed
                if (exitCode != 0) {
                    logger.error("Exit code for `" + Strings.join(' ', args)
                            + "` in " + dir + " was non-zero: " + exitCode);
                }
            } catch (ExecutionException ex) {
                if (ex.getCause() != null && ex.getCause() instanceof IOException) {
                    throw ((IOException) ex.getCause());
                }
            }
        } catch (IOException ioe) {
            if (mayNotHaveNPM && ioe.toString().contains("No such file")) {
                noNPMPresent();
                return false;
            } else {
                throw ioe;
            }
        }
        return exitCode == 0;
    }

    private Set<Path> scanForMarkupFiles(Path in) throws IOException {
        Set<Path> result = new HashSet<>();
        for (String relativePath : scanForBuiltMarkupIn) {
            relativePath = relativePath.trim();
            Path dir = in.resolve(relativePath);
            if (Files.exists(dir)) {
                Files.walk(dir, 200, FileVisitOption.FOLLOW_LINKS)
                        .filter(f -> !Files.isDirectory(f))
                        .forEach(result::add);
            }
        }
        return result;
    }

    private static Path npmBinary;

    private static Path npmBinary() {
        if (npmBinary != null) {
            npmBinary = FileUtils.findExecutable("npm", true, true);
        }
        return npmBinary == null ? Paths.get("npm") : npmBinary;
    }

    private static final String BIG_HONKING_WARNING
            = "\n\n\n\n\n\n"
            + "********************* NO NPM BINARY FOUND *********************\n\n"
            + "Smithy typescript generators were configured to run `npm` to generate\n"
            + "javascript from typescript, but no `npm` binary could be found on the\n"
            + "$PATH or in standard locations.\n\n"
            + "While we are not failing the build because of that, if your generated server\n"
            + "expects markup files to serve, they will not be there.\n\n"
            + "********************* NO NPM BINARY FOUND *********************"
            + "\n\n\n\n\n\n";

    static void noNPMPresent() {
        if (!NPM_ABSENT) {
            System.out.println(BIG_HONKING_WARNING);
            NPM_ABSENT = true;
        }
    }

}
