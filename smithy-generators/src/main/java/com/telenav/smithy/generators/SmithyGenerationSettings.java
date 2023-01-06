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
package com.telenav.smithy.generators;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import java.util.Set;

/**
 * General purpose settings for code generation, which generators for specific
 * languages can participate in.
 *
 * @author Tim Boudreau
 */
public final class SmithyGenerationSettings {

    private final Map<Class<? extends Enum<?>>, Set<Enum<?>>> enumSettings
            = new HashMap<>();
    private final Map<SettingsKey<?>, Object> keySettings = new HashMap<>();
    private final Map<String, String> stringSettings = new HashMap<>();

    public static GenerationSettingsBuilder builder() {
        return new GenerationSettingsBuilder();
    }

    public GenerationSettingsBuilder toBuilder() {
        return new GenerationSettingsBuilder(this);
    }

    SmithyGenerationSettings(
            Map<Class<? extends Enum<?>>, Set<Enum<?>>> enumSettings,
            Map<SettingsKey<?>, Object> keySettings,
            Map<String, String> stringSettings
    ) {
        this.enumSettings.putAll(enumSettings);
        this.keySettings.putAll(keySettings);
        this.stringSettings.putAll(stringSettings);
    }

    public SmithyGenerationSession createSession(
            Set<LanguageWithVersion> languages,
            SmithyDestinations destinations,
            Set<GenerationTarget> generationTargets
    ) {
        return new SmithyGenerationSession(this, languages, destinations, generationTargets);
    }

    public Optional<String> getString(String key) {
        return ofNullable(stringSettings.get(key));
    }

    public Optional<Boolean> getBoolean(String key) {
        return getString(key).map(Boolean::parseBoolean);
    }
    
    public Optional<Integer> getInt(String key) {
        return getString(key).map(Integer::valueOf);
    }

    public Optional<Long> getLong(String key) {
        return getString(key).map(Long::valueOf);
    }

    public <E extends Enum<E>> boolean is(E value) {
        Set<? extends Enum<?>> set = enumSettings.get(value.getDeclaringClass());
        if (set == null) {
            return false;
        }
        return set.contains(value);
    }

    public <T> Optional<T> get(SettingsKey<T> k) {
        Object o = keySettings.get(notNull("k", k));
        if (o == null) {
            return empty();
        }
        return of(k.cast(o));
    }

    @Override
    public String toString() {
        return "SmithyGenerationSettings{" + "enumSettings="
                + enumSettings + ", keySettings=" + keySettings
                + ", stringSettings=" + stringSettings + '}';
    }

    public static final class GenerationSettingsBuilder {

        private final Map<Class<? extends Enum<?>>, Set<Enum<?>>> enumSettings
                = new HashMap<>();
        private final Map<SettingsKey<?>, Object> keySettings = new HashMap<>();
        private final Map<String, String> stringSettings = new HashMap<>();

        private GenerationSettingsBuilder() {

        }

        private GenerationSettingsBuilder(SmithyGenerationSettings orig) {
            this.enumSettings.putAll(orig.enumSettings);
            this.keySettings.putAll(orig.keySettings);
            this.stringSettings.putAll(orig.stringSettings);
        }

        public SmithyGenerationSettings build() {
            return new SmithyGenerationSettings(enumSettings, keySettings,
                    stringSettings);
        }

        public GenerationSettingsBuilder with(String name, String val) {
            stringSettings.put(notNull("name", name).trim(),
                    notNull("val", val).trim());
            return this;
        }

        public GenerationSettingsBuilder with(String name, long val) {
            return with(name, Long.toString(val));
        }

        public GenerationSettingsBuilder with(String name, int val) {
            return with(name, Integer.toString(val));
        }

        public <T> GenerationSettingsBuilder with(SettingsKey<T> key, T val) {
            keySettings.put(key, val);
            return this;
        }

        public GenerationSettingsBuilder withStringSettings(
                Map<? extends String, ? extends String> m) {
            m.forEach(this::with);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <E extends Enum<E>> GenerationSettingsBuilder with(E val) {
            Set<E> set;
            if (!enumSettings.containsKey(val.getDeclaringClass())) {
                set = EnumSet.noneOf(val.getDeclaringClass());
                enumSettings.put(val.getDeclaringClass(), (Set<Enum<?>>) set);
            } else {
                set = (Set<E>) enumSettings.get(val.getDeclaringClass());
            }
            set.add(val);
            return this;
        }
    }
}
