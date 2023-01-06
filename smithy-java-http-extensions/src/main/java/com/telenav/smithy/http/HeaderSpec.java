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
package com.telenav.smithy.http;

import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
public interface HeaderSpec<T> extends Comparable<HeaderSpec<?>>, Function<CharSequence, T> {

    public static HeaderSpec<CharSequence> stringHeader(CharSequence seq) {
        return new StringHeaderSpec(seq);
    }

    /**
     * The Java type
     *
     * @return A type
     */
    public Class<T> type();

    /**
     * The header name as it should appear in HTTP headers
     *
     * @return The name
     */
    public CharSequence name();

    /**
     * Convert an object to a CharSequence suitable for inclusion in headers,
     * typically using Netty's AsciiString.
     *
     * @param value A value
     * @return A header value
     */
    public CharSequence toCharSequence(T value);

    /**
     * Parse the value of a header of this type, returning an appropriate Java
     * object or null
     *
     * @param value A header
     * @return An object that represents the header appropriately, such as a
     * <code>ZonedDateTime</code> for a date header.
     */
    public T toValue(CharSequence value);

    public default HeaderSpec<CharSequence> toStringHeader() {
        return new StringHeaderSpec(name());
    }

    @Override
    public default T apply(CharSequence t) {
        return toValue(t);
    }

    @Override
    public default int compareTo(HeaderSpec<?> o) {
        CharSequence a = name();
        CharSequence b = o.name();
        return Strings.compareCharSequences(a, b, true);
    }

    /**
     * Test if this header's name is the same as the passed name (case
     * insensitive).
     *
     * @param name A name
     * @return True if it matches
     */
    public default boolean is(CharSequence name) {
        return Strings.charSequencesEqual(name(), name, true);
    }

    public static abstract class Base<T> implements HeaderSpec<T> {

        private final CharSequence name;
        private final Class<T> type;

        protected Base(CharSequence name, Class<T> type) {
            this.name = notNull("name", name);
            this.type = notNull("type", type);
        }

        @Override
        public final Class<T> type() {
            return type;
        }

        @Override
        public final CharSequence name() {
            return name;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final HeaderSpec<CharSequence> toStringHeader() {
            if (type == CharSequence.class || type == String.class) {
                return (HeaderSpec<CharSequence>) this;
            }
            return HeaderSpec.super.toStringHeader();
        }

        @Override
        public final int compareTo(HeaderSpec<?> o) {
            return HeaderSpec.super.compareTo(o);
        }

        @Override
        public final boolean is(CharSequence name) {
            return HeaderSpec.super.is(name);
        }

        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || !(o instanceof HeaderSpec<?>)) {
                return false;
            }
            HeaderSpec<?> other = (HeaderSpec<?>) o;
            return is(other.name());
        }

        public final int hashCode() {
            return Strings.charSequenceHashCode(name);
        }

        public final String toString() {
            return name.toString();
        }
    }

}
