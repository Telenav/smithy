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
package com.telenav.smithy.utils.path;

import com.mastfrog.java.vogon.ClassBuilder;
import static java.util.Collections.emptyMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;

/**
 * Generates the constructor annotatation that tells the framework the URI path
 * pattern the generated code will respond to. This may use the Path or
 * PathRegex annotation depending on whether a pattern is associated with any
 * given member being used as a URI path element.
 */
public final class PathInformationExtractor {

    private final Model model;
    private final UriPattern pattern;
    private boolean leadingSlash;

    public PathInformationExtractor(Model model, UriPattern pattern) {
        this.model = model;
        this.pattern = pattern;
    }

    public PathInformationExtractor withLeadingSlashInRegex() {
        leadingSlash = true;
        return this;
    }

    public void assembleActeurPathAnnotation(StructureShape input, ClassBuilder<String> cb) {
        PathInfo info = extractPathInfo(input);
        if (info.isRegex) {
            cb.importing("com.mastfrog.acteur.preconditions.PathRegex");
            cb.annotatedWith("PathRegex").withValue(info.text);
        } else {
            cb.importing("com.mastfrog.acteur.preconditions.Path");
            cb.annotatedWith("Path").withValue(info.text);
        }
    }

    public PathInfo extractPathInfo() {
        return extractPathInfo(null);
    }

    public PathInfo extractPathInfo(StructureShape input) {
        StringBuilder uriPath = new StringBuilder();
        // Get all the named URL labels - entries in the URL pattern
        // that look like /foo/{someId}/bar/{otherId}.  We will need to
        // map them to integer positions within the URL path in order to
        // generate code to find a specific one and decode it.
        List<SmithyPattern.Segment> labels = pattern.getLabels();
        Map<String, SmithyPattern.Segment> labelForName = createLabelForNameMap(labels);
        // getSegments() gets us the complete content of the url path, tenplated
        // and not.
        List<SmithyPattern.Segment> segs = pattern.getSegments();
        // Iterate all the members, and find any @pattern traits on the member
        // or the member's referent that can be used to generate a more specific
        // regular expression for path matching
        Map<Integer, String> patternForIndex
                = extractRegexPatternsForPathElements(labels.size(), input, labelForName, segs);
        if (patternForIndex.isEmpty()) {
            // If there are no patterns, we will use the @Path annotation which is
            // easier to read and just uses shell glob-style * matching (under the hood,
            // that gets translated into a regular expression)
            for (SmithyPattern.Segment seg : pattern.getSegments()) {
                uriPath.append('/');
                if (seg.isLabel()) {
                    uriPath.append('*');
                } else {
                    uriPath.append(seg.toString());
                }
            }
            return new PathInfo(false, uriPath.toString(), patternForIndex);
        } else {
            // Use regexen and @PathRegex
            //
            // The trailing segment can be "greedy" meaning that / is a legal
            // character within it and the path element should match on the
            // entire trailing path portion.
            //
            // PENDING: Implement support for greedy path elements in code
            // generation
            boolean hasGreedy = false;
            uriPath.append('^');
            if (leadingSlash) {
                uriPath.append("\\/");
            }
            for (int i = 0; i < segs.size(); i++) {
                boolean isLast = i == segs.size() - 1;
                if (i > 0) {
                    // We do NOT *prepend* \\/ to our regular expression - the
                    // first element should be unqualified, or it will break
                    // using --basepath to "mount" a web application on a base
                    // URL without that affecting acteurs that attribute semantics
                    // to path element positions
                    uriPath.append("\\/");
                }
                SmithyPattern.Segment seg = segs.get(i);
                if (seg.isLabel()) {
                    String pat = patternForIndex.get(i);
                    if (pat == null) {
                        if (isLast && seg.isGreedyLabel()) {
                            hasGreedy = true;
                            uriPath.append(".*");
                        } else {
                            uriPath.append("[^\\/]+");
                        }
                    } else {
                        uriPath.append(pat);
                    }
                } else {
                    uriPath.append(seg.toString());
                }
            }
            if (!hasGreedy) {
                uriPath.append('$');
            }
            return new PathInfo(true, uriPath.toString(), patternForIndex);
        }
    }

    private Map<String, SmithyPattern.Segment> createLabelForNameMap(List<SmithyPattern.Segment> labels) {
        Map<String, SmithyPattern.Segment> labelForName = new HashMap<>(labels.size());
        for (SmithyPattern.Segment lbl : labels) {
            labelForName.put(lbl.getContent(), lbl);
        }
        return labelForName;
    }

    private Map<Integer, String> extractRegexPatternsForPathElements(int maxSize, StructureShape input, Map<String, SmithyPattern.Segment> labelForName, List<SmithyPattern.Segment> segs) {
        if (input == null) {
            return emptyMap();
        }
        Map<Integer, String> patternForIndex = new HashMap<>(maxSize);
        // Iterate the path once for each member
        input.getAllMembers().forEach((memberName, memberShape) -> {
            SmithyPattern.Segment seg = labelForName.get(memberName);
            if (seg != null) {
                int ix = segs.indexOf(seg);
                Shape target = model.expectShape(memberShape.getTarget());
                Optional<PatternTrait> pt = memberShape.getTrait(PatternTrait.class).or(() -> target.getTrait(PatternTrait.class));
                if (pt.isPresent()) {
                    // We have a pattern.  Strip any leading ^ and trailing $ which
                    // would result in a broken path regular expression and map it
                    String pat = pt.get().getValue();
                    if (pat.charAt(0) == '^') {
                        pat = pat.substring(1);
                    }
                    if (pat.charAt(pat.length() - 1) == '$') {
                        pat = pat.substring(0, pat.length() - 1);
                    }
                    patternForIndex.put(ix, pat);
                } else {
                    // If we don't have a regular expression, we may still have a length
                    // limit, and can generate a regular expression like [^\\/]{1, 24} - i.e.
                    // "one to twenty four of any character except a slash" to filter out
                    // requests with invalid URL path elements
                    Optional<LengthTrait> lt = memberShape.getTrait(LengthTrait.class).or(() -> target.getTrait(LengthTrait.class));
                    if (lt.isPresent()) {
                        int min = lt.get().getMin().map(Long::intValue).orElse(1);
                        Integer max = lt.get().getMax().map(Long::intValue).orElse(null);
                        String rexHead = seg.isGreedyLabel() ? ".{" : "[^\\/]{";
                        StringBuilder rex = new StringBuilder(rexHead).append(min).append(',');
                        if (max != null) {
                            rex.append(max);
                        }
                        rex.append('}');
                        patternForIndex.put(ix, rex.toString());
                    }
                }
            }
        });
        return patternForIndex;
    }

}
