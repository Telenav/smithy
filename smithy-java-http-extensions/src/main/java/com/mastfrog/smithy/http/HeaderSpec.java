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
package com.mastfrog.smithy.http;

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
