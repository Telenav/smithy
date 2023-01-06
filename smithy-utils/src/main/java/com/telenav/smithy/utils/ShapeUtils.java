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
package com.telenav.smithy.utils;

import com.mastfrog.java.vogon.ClassBuilder;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
public final class ShapeUtils {

    private ShapeUtils() {
        throw new AssertionError();
    }

    public static boolean requiredOrHasDefault(MemberShape mem, Shape target) {
        return mem.getTrait(DefaultTrait.class).or(() -> target.getTrait(DefaultTrait.class))
                .isPresent()
                || mem.getTrait(RequiredTrait.class).or(() -> target.getTrait(RequiredTrait.class))
                        .isPresent();
    }
    
    /**
     * Import some classes, checking if it is either in the same package, or in
     * the java.lang package and ignoring it if so.
     *
     * @param cb A class builder
     * @param fqns Fully qualified class names
     */
    public static void maybeImport(ClassBuilder<?> cb, String... fqns) {
        for (String f : fqns) {
            if (f.indexOf('.') < 0 || f.startsWith("null.")) {
                continue;
            }
            importOne(cb, f);
        }
    }

    /**
     * Import a class, checking if it is either in the same package, or in the
     * java.lang package and ignoring it if so.
     *
     * @param cb A class builder
     * @param fqn A fully qualified class name
     */
    private static void importOne(ClassBuilder<?> cb, String fqn) {
        if (fqn.startsWith("java.lang.") && fqn.lastIndexOf('.') == "java.lang.".length()-1) {
            return;
        }
        int ix = fqn.lastIndexOf('.');
        if (ix < 0) {
            return;
        }
        String sub = fqn.substring(0, ix);
        if (cb.packageName().equals(sub)) {
            return;
        }
        cb.importing(fqn);
    }
    
}
