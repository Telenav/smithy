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
