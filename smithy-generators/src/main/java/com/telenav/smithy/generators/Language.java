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

import java.util.Objects;

/**
 * A programming language (case-sensitive name) that generators can match
 * against to decide if they should participate in generating code.
 *
 * @author Tim Boudreau
 */
public class Language {

    private final String language;

    Language(String language) {
        this.language = language;
    }

    public String name() {
        return language;
    }

    public static Language language(String name) {
        return new Language(name);
    }

    public boolean is(String languageName) {
        return language.equalsIgnoreCase(languageName);
    }

    public LanguageWithVersion withVersion(LanguageVersion ver) {
        return new LanguageWithVersion(this, ver);
    }

    public LanguageWithVersion withWildcardVersion() {
        return new LanguageWithVersion(this, LanguageVersion.ANY);
    }

    public LanguageWithVersion withVersion(long majorVersion) {
        return new LanguageWithVersion(this, new LanguageVersion(
                majorVersion));
    }

    public LanguageWithVersion withVersion(long majorVersion, long minorVersion) {
        return new LanguageWithVersion(this, new LanguageVersion(
                majorVersion, minorVersion));
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.language);
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
        final Language other = (Language) obj;
        return Objects.equals(this.language, other.language);
    }
}
