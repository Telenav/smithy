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
package com.mastfrog.smithy.smithy.maven.plugin;

import com.mastfrog.smithy.generators.GenerationResults;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import static com.mastfrog.smithy.generators.GenerationSwitches.DRY_RUN;
import static com.mastfrog.smithy.generators.GenerationSwitches.VERBOSE;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.Language;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.ModelExtensions;
import com.mastfrog.smithy.generators.SmithyDestinations;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSession;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.log.BuildLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;
import static software.amazon.smithy.model.shapes.ShapeType.RESOURCE;
import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;
import software.amazon.smithy.model.validation.Severity;
import static software.amazon.smithy.model.validation.Severity.DANGER;
import static software.amazon.smithy.model.validation.Severity.ERROR;
import static software.amazon.smithy.model.validation.Severity.NOTE;
import static software.amazon.smithy.model.validation.Severity.SUPPRESSED;
import static software.amazon.smithy.model.validation.Severity.WARNING;
import software.amazon.smithy.model.validation.ValidatedResult;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        name = "generate-smithy", threadSafe = true)
public class GenerateSourcesMojo extends AbstractMojo {

    private static final String DEFAULT_DEST = "target/generated-sources/smithy";

    @Parameter(property = "smithy.targets", required = false)
    public List<String> targets;

    @Parameter(property = "smithy.languages", defaultValue = "java")
    public List<String> languages;

    @Parameter(property = "smithy.settings", required = false)
    public Map<String, String> settings;

    /**
     * Maps source output by target and/or shape type.
     */
    @Parameter(property = "smithy.destinations", required = false)
    public Map<String, String> destinations;

    @Parameter(property = "smithy.namespaces", required = true)
    public List<String> namespaces;

    // These are magically injected by Maven:
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private volatile MavenSession mavenSession;

    @Parameter(defaultValue = "src/main/smithy")
    private String sourcePath;

    @Parameter(property = "detail", defaultValue = "false")
    private boolean verbose;

    @Parameter(property = "pretend", defaultValue = "false", alias = "dry-run")
    private boolean pretend;

    @Parameter(property = "smithy.debug", defaultValue = "false")
    private boolean debug;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        BuildLog log = new BuildLog(GenerateSourcesMojo.class);

        System.out.println("\n---------- SMITHY BUILD------------");

        System.out.println("Raw Languages: " + languages);
        System.out.println("Languages: " + languages);
        System.out.println("");
        System.out.println("Raw Targets: " + targets);
        System.out.println("Targets: " + targets());
        System.out.println("");
        System.out.println("Source Path: " + sourcePath);
        System.out.println("");
        System.out.println("Settings: " + settings);
        System.out.println("");
        System.out.println("Destinations: " + destinations);

        System.out.println("\n-----------------------------------\n");

        project.addCompileSourceRoot(sourcePath);

        Set<LanguageWithVersion> languages = languages();
        Set<GenerationTarget> targets = targets();
        if (languages.isEmpty()) {
            System.out.println("No languages to generate for.");
            return;
        }
        if (targets.isEmpty()) {
            System.out.println("No ");
        }
        try {
            generate(languages, targets, log);
        } catch (IOException ex) {
            throw new MojoFailureException(ex);
        }
    }

    private void generate(Set<LanguageWithVersion> languages,
            Set<GenerationTarget> targets, BuildLog log) throws MojoFailureException, IOException {
        Set<Path> files = findLocalSmithyFiles();
        ModelAssembler assembler = new ModelAssembler();
        ModelExtensions.prepare(assembler);
        for (Path path : files) {
            assembler.addImport(path);
        }
        ValidatedResult<Model> result = assembler.assemble();
        result.getValidationEvents().forEach(evt -> {
            switch (evt.getSeverity()) {
                case NOTE:
                    log.info(evt.toString());
                    break;
                case WARNING:
                    log.warn(evt.toString());
                    break;
                case DANGER:
                    log.warn(evt.toString());
                    break;
                case ERROR:
                    log.error(evt.toString());
                    break;
                case SUPPRESSED:
                    log.debug(evt.toString());
                    break;
            }
        });
        if (result.isBroken()) {
            result.getValidationEvents(Severity.ERROR)
                    .forEach(ve -> {
                        log.error(ve.toString());
                    });
            throw new MojoFailureException("Smithy model is invalid: " + result);
        }
        Model model = result.unwrap();

        SmithyGenerationSettings.GenerationSettingsBuilder bldr
                = SmithyGenerationSettings.builder();
        if (verbose) {
            bldr.with(VERBOSE);
        }
        if (pretend) {
            bldr.with(DRY_RUN);
        }
        if (debug) {
            bldr.with(DEBUG);
        }
        if (settings != null && !settings.isEmpty()) {
            bldr.withStringSettings(settings);
        }

        try {
            generate(languages, targets, model, bldr.build(), log);
        } catch (Exception ex) {
            log.error("Failed generating smithy models", ex);
            throw new MojoFailureException(ex);
        }
    }

    private void generate(Set<LanguageWithVersion> languages,
            Set<GenerationTarget> targets,
            Model model, SmithyGenerationSettings settings, BuildLog log) throws Exception {
        System.out.println("Langs: " + languages);
        System.out.println("Targs: " + targets);
        System.out.println("Model: " + model.getShapeIds().size() + " ids");
        System.out.println("Namespaces: " + namespaces);
        SmithyGenerationSession session
                = settings.createSession(
                        languages,
                        new SmithyDestinationsImpl(),
                        new HashSet<>(targets));
        Predicate<ShapeId> test = id -> namespaces.contains(id.getNamespace());
        GenerationResults results = session.generate(model, test, new LogWrapper(log));
        System.out.println("Have " + results);

        results.commit();
    }

    Path smithyRoot() {
        return project.getBasedir().toPath().resolve(sourcePath);
    }

    private Set<Path> findLocalSmithyFiles() throws MojoFailureException, IOException {
        Path root = smithyRoot();
        if (!Files.exists(root)) {
            throw new MojoFailureException("Smithy root " + root
                    + " does not exist");
        }
        Set<Path> result = new HashSet<>();
        Files.walk(smithyRoot(), 128).filter(pth -> {
            return !Files.isDirectory(pth)
                    && pth.getFileName().toString().endsWith(".smithy");
        }).forEach(result::add);
        return result;
    }

    public Set<LanguageWithVersion> languages() throws MojoFailureException {
        if (languages == null) {
            return singleton(Language.language("java").withWildcardVersion());
        }
        Set<LanguageWithVersion> all = new HashSet<>();
        for (String l : languages) {
            all.add(LanguageWithVersion.parse(l));
        }
        return all;
    }

    public Set<GenerationTarget> targets() throws MojoFailureException {
        if (targets == null || targets.isEmpty()) {
            return new HashSet<>(asList(GenerationTarget.CLIENT, GenerationTarget.MODEL, GenerationTarget.SERVER));
        }
        Set<GenerationTarget> targets = new HashSet<>();
        for (String t : this.targets) {
            targets.add(new GenerationTarget(t.trim()));
        }
        return targets;
    }

    class SmithyDestinationsImpl implements SmithyDestinations {

        @Override
        public Path sourceRootFor(GenerationTarget generationTarget,
                Shape shape, LanguageWithVersion language,
                SmithyGenerationSettings settings) {
            Path root = project.getBasedir().toPath();
            String dest = destination(generationTarget, shape, language);
            return root.resolve(dest);
        }

        private String destination(GenerationTarget generationTarget, Shape shape, 
                LanguageWithVersion language) {
            String[] dests = destinations(generationTarget, shape, language);
            for (String d : dests) {
                String result = destinations.get(d);
                if (result != null) {
                    return result;
                }
            }
            if (GenerationTarget.MODEL_TEST.equals(generationTarget) && language.isLanguage("java")) {
                return project.getBasedir().toPath().resolve("src/test/java").toString();
            }
            return DEFAULT_DEST;
        }

        private String[] destinations(GenerationTarget target, Shape shape, LanguageWithVersion lang) {
            String suff = typeSuffix(shape);
            return new String[]{
                Strings.join('.', lang.language().name(), lang.version(), target.name(), suff).toString(),
                Strings.join('.', lang.language().name(), target.name(), suff),
                Strings.join('.', lang.language().name(), target.name()),
                Strings.join('.', target.name(), suff),
                Strings.join('.', target.name()),};
        }
    }

    static class LogWrapper implements SmithyGenerationLogger {

        private final BuildLog log;

        public LogWrapper(BuildLog log) {
            this.log = log;
        }

        @Override
        public SmithyGenerationLogger info(String what) {
            log.info(what);
            return this;
        }

        @Override
        public SmithyGenerationLogger error(String what) {
            log.error(what);
            return this;
        }

        @Override
        public SmithyGenerationLogger debug(String what) {
            log.debug(what);
            return this;
        }

        @Override
        public SmithyGenerationLogger warn(String what) {
            log.warn(what);
            return this;
        }
    }

    static String typeSuffix(Shape shape) {
        switch (shape.getType()) {
            case RESOURCE:
            case OPERATION:
            case SERVICE:
                return "server";
            default:
                return "model";
        }
    }

}
