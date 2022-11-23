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
package com.mastfrog.smithy.generators;

import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public class GenerationTarget {

    public static final String NAME_MODEL = "model";
    public static final String NAME_SERVER = "server";
    public static final String NAME_SERVER_SPI = "server-spi";
    public static final String NAME_CLIENT = "client";
    public static final String NAME_MODEL_TEST = "modeltest";
    public static final String NAME_DOCS = "docs";
    public static final GenerationTarget MODEL = new GenerationTarget(NAME_MODEL);
    public static final GenerationTarget SERVER = new GenerationTarget(NAME_SERVER);
    public static final GenerationTarget SERVER_SPI = new GenerationTarget(NAME_SERVER_SPI);
    public static final GenerationTarget CLIENT = new GenerationTarget(NAME_CLIENT);
    public static final GenerationTarget MODEL_TEST = new GenerationTarget(NAME_MODEL_TEST);
    public static final GenerationTarget DOCS = new GenerationTarget(NAME_DOCS);
    private final String name;

    public GenerationTarget(String name) {
        this.name = name;
    }

    public static Set<GenerationTarget> builtIn() {
        return new HashSet<>(asList(MODEL, SERVER, CLIENT, MODEL_TEST));
    }

    public String name() {
        return name;
    }

    public static Set<GenerationTarget> targets(Collection<? extends String> names) {
        Set<GenerationTarget> result = new HashSet<>();
        for (String n : names) {
            result.add(new GenerationTarget(n));
        }
        return result;
    }

    public static Set<GenerationTarget> intersection(Collection<? extends GenerationTarget> a, Collection<? extends GenerationTarget> b) {
        Set<GenerationTarget> sa = new HashSet<>(a);
        sa.retainAll(b);
        return sa;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.name);
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
        final GenerationTarget other = (GenerationTarget) obj;
        return Objects.equals(this.name, other.name);
    }
}
