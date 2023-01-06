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
