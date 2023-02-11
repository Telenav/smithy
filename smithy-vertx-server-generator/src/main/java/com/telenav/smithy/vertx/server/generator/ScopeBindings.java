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
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.simpleNameOf;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static java.util.Collections.emptySet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Set of bindings that use request scope which is built up as we generate
 * operation implementations.
 */
final class ScopeBindings {

    private final Map<String, Set<String>> optionalComments = new HashMap<>();
    private final Map<String, Set<String>> directComments = new HashMap<>();
    private final Set<String> optionalBindings = new TreeSet<>();
    private final Set<String> directBindings = new TreeSet<>();

    boolean isEmpty() {
        return optionalBindings.isEmpty() && directBindings.isEmpty();
    }

    ScopeBindings bindOptional(String what, String comment) {
        if (what.indexOf('.') < 0) {
            throw new IllegalArgumentException("Not a fully qualified name: " + what);
        }
        optionalBindings.add(what);
        optionalComments.computeIfAbsent(what, w -> new LinkedHashSet<>()).add(comment);
        return this;
    }

    ScopeBindings bindDirect(String what, String comment) {
        if (what.indexOf('.') < 0) {
            throw new IllegalArgumentException("Not a fully qualified name: " + what);
        }
        directBindings.add(what);
        directComments.computeIfAbsent(what, w -> new LinkedHashSet<>()).add(comment);
        return this;
    }

    ScopeBindings bindBoth(String what, String comment) {
        return bindDirect(what, comment).bindOptional(what, comment);
    }

    <C, B extends ClassBuilder.BlockBuilderBase<C, B, ?>> String generateBindingCode(
            String binder, String vxModule, B bb, ClassBuilder<?> cb) {
        bb.blankLine().lineComment("Scoped bindings so input handling can be broken up")
                .lineComment("into logical steps");
        cb.importing("com.telenav.vertx.guice.scope.RequestScope");
        String scopeVar = "scope";
        bb.declare(scopeVar).initializedByInvoking("scope").on(vxModule).as("RequestScope");
        maybeImport(cb, directBindings.toArray(new String[0]));
        maybeImport(cb, optionalBindings.toArray(new String[0]));
        cb.importing(Optional.class);
        for (String dir : directBindings) {
            cb.importing(dir);
            Set<String> cmts = directComments.getOrDefault(dir, emptySet());
            bb.blankLine();
            for (String cmt : cmts) {
                bb.lineComment(cmt);
            }
            bb.invoke("bindType")
                    .withArgument("binder")
                    .withClassArgument(simpleNameOf(dir))
                    .on(scopeVar);
        }
        for (String ind : optionalBindings) {
            cb.importing(ind);
            Set<String> cmts = directComments.getOrDefault(ind, emptySet());
            bb.blankLine();
            for (String cmt : cmts) {
                bb.lineComment(cmt);
            }
            bb.invoke("bindOptional")
                    .withArgument("binder")
                    .withClassArgument(simpleNameOf(ind)).on(scopeVar);
        }
        return scopeVar;
    }
}
