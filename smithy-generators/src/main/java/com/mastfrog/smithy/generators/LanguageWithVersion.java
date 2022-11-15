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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public final class LanguageWithVersion {

    private final Language language;
    private final LanguageVersion version;

    LanguageWithVersion(Language language, LanguageVersion version) {
        this.language = notNull("language", language);
        this.version = version;
    }

    public static LanguageWithVersion parse(String val) {
        int ix = val.indexOf('-');
        if (ix > 0) {
            String head = val.substring(0, ix);
            String tail = val.substring(ix + 1);
            return new LanguageWithVersion(new Language(head),
                    LanguageVersion.parseDeweyDecimal(tail));
        } else {
            return new Language(val).withWildcardVersion();
        }
    }

    public boolean isLanguage(String langName) {
        return language.is(langName);
    }

    public Language language() {
        return language;
    }

    public LanguageVersion version() {
        return version;
    }

    @Override
    public String toString() {
        return language + "-" + version;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + Objects.hashCode(this.language);
        hash = 17 * hash + Objects.hashCode(this.version);
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
        final LanguageWithVersion other = (LanguageWithVersion) obj;
        if (!Objects.equals(this.language, other.language)) {
            return false;
        }
        return Objects.equals(this.version, other.version);
    }

}
