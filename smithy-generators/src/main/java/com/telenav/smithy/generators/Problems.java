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
