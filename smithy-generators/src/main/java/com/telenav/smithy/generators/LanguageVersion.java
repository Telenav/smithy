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

import java.util.ArrayList;
import java.util.List;

/**
 * A version of a language
 *
 *
 * @author Tim Boudreau
 */
public final class LanguageVersion implements Comparable<LanguageVersion> {

    private final long major;
    private final long minor;

    public static LanguageVersion ANY = new LanguageVersion(0, 0);

    LanguageVersion(long major, long minor) {
        this.major = major;
        this.minor = minor;
    }

    LanguageVersion(long major) {
        this(major, 0L);
    }

    public static LanguageVersion parseDeweyDecimal(String value) {
        StringBuilder curr = new StringBuilder();
        List<Long> values = new ArrayList<>(2);
        Runnable emitNumber = () -> {
            if (curr.length() > 0) {
                values.add(Long.parseLong(curr.toString()));
                curr.setLength(0);
            }
        };
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isDigit(c)) {
                emitNumber.run();
            } else {
                curr.append(c);
            }
            if (values.size() >= 2) {
                break;
            }
        }
        emitNumber.run();

        while (values.size() < 2) {
            values.add(0L);
        }
        return new LanguageVersion(values.get(0), values.get(1));
    }

    public long major() {
        return major;
    }

    public long minor() {
        return minor;
    }

    @Override
    public int compareTo(LanguageVersion o) {
        int result = Long.compare(major, o.major);
        if (result == 0) {
            result = Long.compare(minor, o.minor);
        }
        return result;
    }

    public boolean isEqualOrLessThan(LanguageVersion other) {
        int result = compareTo(other);
        return result <= 0;
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + (int) (this.major ^ (this.major >>> 32));
        hash = 67 * hash + (int) (this.minor ^ (this.minor >>> 32));
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
        final LanguageVersion other = (LanguageVersion) obj;
        if (this.major != other.major) {
            return false;
        }
        return this.minor == other.minor;
    }

}
