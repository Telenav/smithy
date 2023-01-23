import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.archetype.ArchetypeGenerationRequest;

// This script is the only way to run post-generation code in an archetype.
// It allows us to:
//   * Remove the -model-typescript project and typescript generation related
//     stuff from the model pom.xml if run with typescript=no
//   * Delete the nonsensical null/com/whatever folder that maven-artifact-plugin
//     mysteriously spits out, which seems simply to be a bug in it

new PostGenerate(request).go()

class PostGenerate {

    private final ArchetypeGenerationRequest request;
    private final Path dir;
    private TYPESCRIPT_DEP='''                        <!-- Generates a typescript client SDK -->\n                        <dependency>\n                            <groupId>com.telenav.smithy</groupId>\n                            <artifactId>smithy-ts-generator</artifactId>\n                            <version>${smithy-tools.version}</version>\n"                        </dependency>\n''';

    PostGenerate(ArchetypeGenerationRequest request) {
        this.request = request;
        dir = Paths.get(request.getOutputDirectory(), request.getArtifactId());
    }

    public void go() throws IOException {
        // No idea why or how, but the archetype plugin creates a
        // directory named "null" with the package directories under it
        deltree(dir.resolve("null"));
        if ("no".equals(request.getProperties().getProperty("typescript"))) {
            replaceInPom(null, TYPESCRIPT_DEP, null);
            replaceInPom("model", "<languages>java,typescript</languages>",
                    "<languages>java</languages>");
            replaceInPom("model", "<typescript.model>*", null);
            deltree(projectPath("model-typescript"));
        } else {
            makeExecutable(projectFile("model-typescript", "rebuild"));
            makeExecutable(projectFile("model-typescript", "test.sh"));
            makeExecutable(projectFile("model-typescript", "copy-markup"));
            makeExecutable(projectFile(null, "build-all"));
        }
    }

    private void makeExecutable(Path path) throws IOException {
        if (path != null && Files.exists(path)) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr--"));
        }
    }

    private Path projectFile(String projectSuffix, String relativePath) {
        Path result = projectPath(projectSuffix);
        if (result != null) {
            Path fl = result.resolve(relativePath);
            if (Files.exists(fl)) {
                return fl;
            }
        }
        return null;
    }

    private Path projectPath(String projectSuffix) {
        Path result;
        if (projectSuffix == null) {
            result = dir;
        } else {
            result = dir.resolve(request.getArtifactId() + "-" + projectSuffix);
        }
        if (!Files.exists(result)) {
            return null;
        }
        return result;
    }

    private Path pomPath(String projectSuffix) {
        Path result = projectPath(projectSuffix);
        if (result != null) {
            result = result.resolve("pom.xml");
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public void replaceInPom(String projectSuffix, String before, String after) throws IOException {
        Path pomPath = pomPath(projectSuffix);
        if (pomPath != null) {
            replaceInFile(before, after, pomPath);
        }
    }

    private void replaceInFile(String before, String after, Path path) throws IOException {
        if (before != null && before.indexOf('\n') >= 0) {
            replaceExactInFile(path, before, after);
        } else {
            replaceLineInFile(path, before, after);
        }
    }

    private void replaceExactInFile(Path path, String before, String after) throws IOException {
        String text = Files.readString(path);
        int ix = text.indexOf(before);
        if (ix < 0) {
            return;
        }
        String head = text.substring(0, ix);
        String tail = text.substring(ix + before.length() + 1, text.length());
        Files.writeString(path, head + (after == null ? "" : after) + tail, UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void replaceLineInFile(Path path, String before, String after) throws IOException {
        StringBuilder nue = new StringBuilder();
        String lineHead = null;
        if (before.endsWith("*")) {
            lineHead = before.substring(0, before.length() - 1);
        }
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (lineHead != null) {
                if (trimmed.startsWith(lineHead)) {
                    if (after == null || after.isEmpty()) {
                        continue;
                    } else {
                        int ix = line.indexOf(before);
                        for (int i=0; i < ix; i++) {
                            nue.append(" ");
                        }
                        nue.append(after);
                    }
                } else {
                    nue.append(line).append('\n');
                }
            } else {
                if (trimmed.equals(before)) {
                    if (after == null || after.isEmpty()) {
                        continue;
                    }
                    int ix = line.indexOf(before);
                    for (int i=0; i < ix; i++) {
                        nue.append(" ");
                    }
                    nue.append(after).append('\n');
                } else {
                    nue.append(line).append('\n');
                }
            }
        }
        Files.writeString(path, nue, UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void deltree(Path path) throws IOException {
        if (path != null && Files.exists(path)) {
            List<Path> files = new ArrayList<>();
            List<Path> dirs = new ArrayList<>();
            Stream<Path> str = Files.walk(path, 256);
            try {
                def clo = {Path file ->
                    if (Files.isDirectory(file)) {
                        dirs.add(file);
                    } else {
                        files.add(file);
                    }
                };
                str.forEach(clo);
            } finally {
                str.close();
            }
            for (Path p : files) {
                Files.delete(p);
            }
            def sorter = {Path a, Path b ->
                return Integer.compare(b.getNameCount(), a.getNameCount());
            }
            dirs.sort(sorter);
            for (Path p : dirs) {
                Files.delete(p);
            }
        }
    }
}
