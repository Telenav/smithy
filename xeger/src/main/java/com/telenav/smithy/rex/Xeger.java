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
package com.telenav.smithy.rex;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.antlr.v4.runtime.CharStreams.fromString;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 *
 * @author Telenav
 */
public class Xeger {

    static final int MAX_LENGTH = 32;

    private final List<CaptureGroup> groups = new ArrayList<>();
    private final Pattern pattern;
    private final boolean confounded;
    final GeneralBag root;

    /**
     * Create a new Xeger.
     *
     * @param regex A regular expression (must be valid)
     */
    public Xeger(String regex) {
        this(regex, null);
    }

    Xeger(String regex, Consumer<String> parseTreeLogger) {
        regex = trim(regex);
        confounded = false;
        pattern = Pattern.compile(regex);
        XegerLexer lex = new XegerLexer(fromString(regex));
        XegerParser parser = new XegerParser(new CommonTokenStream(lex));
        RegexDissectingVisitor v = new RegexDissectingVisitor(groups, parseTreeLogger);
        root = v.root.prune();
        parser.parse().accept(v);
    }

    Xeger(GeneralBag root, Pattern pattern) {
        this.root = root;
        confounded = true;
        this.pattern = pattern;
        root.traverse(0, (d, el)
                -> el.as(CaptureGroup.class).ifPresent(groups::add));
    }

    public Optional<Xeger> confound() {
        return root.confound().map(rt -> new Xeger(rt, pattern));
    }

    public boolean isConfounded() {
        return confounded;
    }

    /**
     * Get the regular expression this Xeger was initialized with.
     *
     * @return A string
     */
    public String pattern() {
        return pattern.pattern();
    }

    @Override
    public String toString() {
        return (confounded ? "!" : "") + "/" + pattern.pattern() + "/ -> " + root;
    }

    /**
     * Determine if some text matches the regular expression.
     *
     * @param text Some text
     * @return true if it is a match
     */
    public boolean matches(String text) {
        return pattern.matcher(text).matches() != confounded;
    }

    /**
     * Emit a set of strings which match the regular expression, if possible. It
     * may be impossible either if all branches of the regular expression are
     * ones this library cannot handle, or if the total size of possible matches
     * to the regular expression is smaller than the requested set size.
     *
     * @param size The requested size
     * @param rnd A random
     * @param attempts The number of attempts to make, for cases where some
     * branches of the regular expression may not be able to be handled and some
     * are
     * @return A set of matching strings of the requested size, if one could be
     * created
     */
    public Optional<Set<String>> emitSet(int size, Random rnd, int attempts) {
        Set<String> result = new TreeSet<>();
        for (int i = 0; i < attempts && result.size() < size; i++) {
            emitChecked(rnd).ifPresent(result::add);
        }
        if (result.size() == size) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * Emit a set of strings which match the regular expression, if possible. It
     * may be impossible either if all branches of the regular expression are
     * ones this library cannot handle, or if the total size of possible matches
     * to the regular expression is smaller than the requested set size.
     *
     * @param size The requested size
     * @param rnd A random
     * @param attempts The number of attempts to make, for cases where some
     * branches of the regular expression may not be able to be handled and some
     * are
     * @return A set of matching strings of the requested size, if one could be
     * created
     */
    public Optional<List<String>> emitList(int size, Random rnd, int attempts) {
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < attempts && result.size() < size; i++) {
            emitChecked(rnd).ifPresent(result::add);
        }
        if (result.size() == size) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    private CaptureGroup resolveBackref(int val) {
        int v = val - 1;
        if (v >= groups.size()) {
            throw new IllegalArgumentException("No capture group \\" + val);
        }
        return groups.get(v);
    }

    /**
     * Emit a string which matches the regular expression passed to the
     * constructor, if possible.
     *
     * @param rnd A random
     * @return A string
     */
    public Optional<String> emitChecked(Random rnd) {
        return emitChecked(rnd, 15);
    }

    /**
     * Emit a string which matches the regular expression passed to the
     * constructor, if possible.
     *
     * @param rnd A random
     * @param attempts The number of attempts to make before giving up (useful
     * in the case of a regular expression, some branches of which cannot be
     * handled where others can).
     * @return A string
     */
    public Optional<String> emitChecked(Random rnd, int attempts) {
        for (int i = 0; i < attempts; i++) {
            String result = emit(rnd);
            Matcher m = pattern.matcher(result);
            if (m.find()) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    /**
     * Emit a string which <i>should</i> match the passed regular expression,
     * but is not guaranteed to (some exotic backref constructs may result in
     * failing results).
     *
     * @param rnd A random
     * @return A string
     */
    public String emit(Random rnd) {
        StringBuilder sb = new StringBuilder();
        root.emit(sb, rnd, this::resolveBackref);
        return sb.toString();
    }

    private static String trim(String regex) {
        if (regex.isEmpty()) {
            return regex;
        }
        if (regex.charAt(0) == '^') {
            regex = regex.substring(1);
        }
        if (regex.isEmpty()) {
            return regex;
        }
        if (regex.charAt(regex.length() - 1) == '$') {
            regex = regex.substring(0, regex.length() - 1);
        }
        return regex;
    }

    /**
     * Runs the demo code from README.md
     *
     * @param _ignored ignored
     */
    public static void main(String... _ignored) {
        // Matches a UUID
        Xeger xeger = new Xeger("^[0-9a-f]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");

        Random rnd = new Random(812831047810431L);
        // Use 5 attempts - we know it will work
        Set<String> uuids = xeger.emitSet(5, rnd, 5).get();

        uuids.forEach(System.out::println);

        xeger.confound().ifPresent(confounded -> {
            for (int i = 0; i < 5; i++) {
                System.out.println(confounded.emit(rnd));
            }
//          Output like
//            d.e.b.8.e66b4
//            0ba0...2b.cdc64
//           16.e...ac8807d6
//           dbb0.e6.11..4d7c46a
        });

    }
}
