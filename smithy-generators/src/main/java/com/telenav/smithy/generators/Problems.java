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
package com.telenav.smithy.generators;

import com.telenav.smithy.generators.Problems.Problem;
import static java.util.Collections.unmodifiableSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 *
 * @author Tim Boudreau
 */
public final class Problems implements Iterable<Problem> {

    private final Set<Problem> problems = new LinkedHashSet<>();

    public Problems add(String problem, boolean fatal) {
        problems.add(new Problem(null, problem, fatal));
        return this;
    }

    public Problems add(ShapeId shape, String problem, boolean fatal) {
        problems.add(new Problem(shape, problem, fatal));
        return this;
    }

    public boolean hasFatal() {
        for (Problem p : problems) {
            if (p.fatal) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<Problem> iterator() {
        return unmodifiableSet(problems).iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Problem p : problems) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(p);
        }
        if (problems.isEmpty()) {
            sb.append("-empty-");
        }
        return sb.toString();
    }

    public static final class Problem {

        private final ShapeId shape;

        private final String problem;
        private final boolean fatal;

        private Problem(ShapeId shape, String problem, boolean fatal) {
            this.shape = shape;
            this.problem = problem;
            this.fatal = fatal;
        }

        @Override
        public String toString() {
            return (fatal ? "(fatal) " : "") + problem
                    + (shape != null ? " " + shape : "");
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + Objects.hashCode(this.shape);
            hash = 67 * hash + Objects.hashCode(this.problem);
            hash = 67 * hash + (this.fatal ? 1 : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Problem other = (Problem) obj;
            if (this.fatal != other.fatal) {
                return false;
            }
            if (!Objects.equals(this.problem, other.problem)) {
                return false;
            }
            return Objects.equals(this.shape, other.shape);
        }

    }
}
