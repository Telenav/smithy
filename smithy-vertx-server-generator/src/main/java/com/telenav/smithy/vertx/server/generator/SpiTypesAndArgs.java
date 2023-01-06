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
package com.telenav.smithy.vertx.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 *
 * @author Tim Boudreau
 */
class SpiTypesAndArgs {

    private final List<String> types = new ArrayList<>(5);
    final List<String> args = new ArrayList<>(5);
    private final Set<Integer> injectIntoOutputWriter = new HashSet<>(3);
    static final Set<String> additionalImports = new HashSet<>();

    public void add(String typeFqn, String varName) {
        add(typeFqn, varName, false);
    }

    private void checkIsFqn(String typeFqn) {
        if (typeFqn.indexOf('.') < 0) {
            throw new IllegalArgumentException("Not a qualified type name: " + typeFqn);
        }
    }

    public void alsoImport(String fqn) {
        additionalImports.add(fqn);
    }

    public void add(String typeFqn, String varName, boolean inject) {
        checkIsFqn(typeFqn);
        if (inject) {
            injectIntoOutputWriter.add(types.size());
        }
        types.add(typeFqn);
        args.add(varName);
    }

    void eachInjectableType(BiConsumer<String, String> c) {
        for (Integer i : injectIntoOutputWriter) {
            c.accept(types.get(i), args.get(i));
        }
    }

    public void importTypes(ClassBuilder<String> cb) {
        for (String t : types) {
            int gix = t.indexOf('<');
            if (gix > 0) {
                t = t.substring(0, gix);
            }
            cb.importing(t);
        }
        additionalImports.forEach(cb::importing);
    }

}
