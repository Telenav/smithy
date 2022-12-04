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

    <C, B extends ClassBuilder.BlockBuilderBase<C, B, ?>> void generateBindingCode(
            String binder, String vxModule, B bb, ClassBuilder<?> cb) {
        bb.blankLine().lineComment("Scoped bindings so input handling can be broken up")
                .lineComment("into logical steps");
        cb.importing("com.telenav.vertx.guice.scope.RequestScope");
        bb.declare("scope").initializedByInvoking("scope").on(vxModule).as("RequestScope");
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
                    .on("scope");
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
                    .withClassArgument(simpleNameOf(ind)).on("scope");
        }
    }
}
