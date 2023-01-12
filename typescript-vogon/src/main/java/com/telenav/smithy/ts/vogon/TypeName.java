/*
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.smithy.ts.vogon;

import com.mastfrog.code.generation.common.CodeGenerator;
import com.mastfrog.code.generation.common.LinesBuilder;
import com.mastfrog.code.generation.common.util.Utils;
import com.mastfrog.function.state.Obj;
import java.util.ArrayList;
import static java.util.Collections.singleton;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
final class TypeName extends TypescriptCodeGenerator.NamedTypescriptCodeGenerator implements Comparable<TypeName> {

    private final String name;

    private TypeName(String name) {
        this.name = Utils.notNull("name", name);
    }

    static CodeGenerator typeName(String name) {
        if (name.indexOf('|') > 0 || name.indexOf('&') > 0) {
            StringBuilder curr = new StringBuilder(name.length());

            char[] lastDelim = new char[1];
            Obj<CompoundTypeName> comp = Obj.create();
            Runnable emit = () -> {
                String s = curr.toString().trim();
                if (s.length() == 0) {
                    return;
                }
                curr.setLength(0);
                if (lastDelim[0] != 0) {
                    comp.get().append(lastDelim[0], s);
                } else {
                    comp.set(new CompoundTypeName(new TypeName(s)));
                }
            };

            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isWhitespace(c)) {
                    emit.run();
                } else {
                    switch (c) {
                        case '|':
                        case '&':
                            emit.run();
                            lastDelim[0] = c;
                            continue;
                        default:
                            curr.append(c);
                    }
                }
            }
        }
        return new TypeName(name.trim());
    }

    static class CompoundTypeName extends TypescriptCodeGenerator {

        private final CodeGenerator initial;
        private final List<DelimitedTypeName> additional = new ArrayList<>();

        public CompoundTypeName(CodeGenerator initial) {
            this.initial = initial;
        }

        void append(char delim, String typeName) {
            additional.add(new DelimitedTypeName(delim, new TypeName(typeName.trim())));
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            c.accept(initial);
            additional.forEach(c);
        }

        @Override
        public void generateInto(LinesBuilder lines) {
            initial.generateInto(lines);
            for (DelimitedTypeName d : additional) {
                d.generateInto(lines);
            }
        }

        static class DelimitedTypeName extends TypescriptCodeGenerator {

            private final char delimiter;
            private final CodeGenerator child;

            public DelimitedTypeName(char delimiter, CodeGenerator child) {
                this.delimiter = delimiter;
                this.child = child;
            }

            @Override
            protected void visitContents(Consumer<? super CodeGenerator> c) {
                c.accept(child);
            }

            @Override
            public void generateInto(LinesBuilder lines) {
                lines.appendRaw(delimiter);
                child.generateInto(lines);
            }
        }
    }

    @Override
    protected void visitContents(Consumer<? super CodeGenerator> c) {
        // do nothing
    }

    @Override
    public String structure() {
        return name;
    }

    @Override
    Set<String> directlyReferencedTypeNames() {
        return singleton(name);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public void generateInto(LinesBuilder lines) {
        lines.word(name);
    }

    public String name() {
        return name;
    }

    @Override
    public int compareTo(TypeName o) {
        return name.compareTo(o.name());
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.name);
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
        final TypeName other = (TypeName) obj;
        return Objects.equals(this.name, other.name);
    }

}
