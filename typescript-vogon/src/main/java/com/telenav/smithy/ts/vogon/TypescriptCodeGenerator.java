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
package com.telenav.smithy.ts.vogon;

import com.mastfrog.code.generation.common.CodeGenerator;
import com.mastfrog.code.generation.common.LinesBuilder;
import static java.util.Collections.emptySet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base class for typescript generators.
 *
 * @author Tim Boudreau
 */
public abstract class TypescriptCodeGenerator implements CodeGenerator {

    @Override
    public String toString() {
        LinesBuilder lb = newLinesBuilder();
        generateInto(lb);
        return lb.toString();
    }

    protected void visitReferencedTypes(BiConsumer<? super CodeGenerator, ? super String> gen) {
        directlyReferencedTypeNames().forEach(name -> {
            gen.accept(this, name);
        });
        visitContents(child -> {
            if (child instanceof TypescriptCodeGenerator) {
                ((TypescriptCodeGenerator) child).visitReferencedTypes(gen);
            }
        });
    }

    protected abstract void visitContents(Consumer<? super CodeGenerator> c);

    Set<String> directlyReferencedTypeNames() {
        Set<String> names = new HashSet<>(8);
        visitContents(child -> {
            if (child instanceof TypeName t) {
                names.add(t.name());
            }
        });
        return emptySet();
    }

    public String structure() {
        StringBuilder sb = new StringBuilder();
        class C implements Consumer<CodeGenerator> {

            LinkedList<String> path = new LinkedList<>();

            @Override
            public void accept(CodeGenerator t) {
                if (t instanceof NamedTypescriptCodeGenerator n) {
                    path.push(n.name());
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    for (Iterator<String> it = path.iterator(); it.hasNext();) {
                        String s = it.next();
                        sb.append(s);
                        if (it.hasNext()) {
                            sb.append('.');
                        }
                    }
                }
                if (t instanceof TypescriptCodeGenerator ty) {
                    ty.visitContents(this);
                }
                if (t instanceof NamedTypescriptCodeGenerator) {
                    path.pop();
                }
            }
        }
        return sb.toString();
    }

    public static abstract class ChildlessTypescriptCodeGenerator extends TypescriptCodeGenerator {

        Set<String> directlyReferencedTypeNames() {
            return emptySet();
        }

        @Override
        protected void visitContents(Consumer<? super CodeGenerator> c) {
            // do nothing
        }
    }

    public static abstract class NamedTypescriptCodeGenerator extends TypescriptCodeGenerator {

        abstract String name();
    }
}
