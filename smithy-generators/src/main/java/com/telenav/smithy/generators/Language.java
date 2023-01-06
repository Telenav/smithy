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
