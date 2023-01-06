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
