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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

/**
 * Provides access to http request headers and URL.
 *
 * @author Tim Boudreau
 */
public interface SmithyRequest {

    default <T> Optional<T> header(HeaderSpec<T> header) {
        return httpHeader(header.name()).map(header);
    }

    /**
     * Get an HTTP header.
     *
     * @param name A name
     * @return An optional
     */
    Optional<CharSequence> httpHeader(CharSequence name);

    /**
     * Get the names of all headers in the request.
     *
     * @return
     */
    Set<? extends CharSequence> httpHeaderNames();

    /**
     * Get a path element of the URL path, if one exists.
     *
     * @param index The index of the /-delimited path element
     * @return An optional
     */
    Optional<CharSequence> uriPathElement(int index);

    /**
     * Get a query parameter, if one is present.
     *
     * @param name A name
     * @param decode If true, apply URL decoding to the result
     * @return A string or empty
     */
    Optional<CharSequence> uriQueryParameter(CharSequence name, boolean decode);

    /**
     * Resource closing - if there are resources (database connections,
     * file streams, etc.) that could leak if the client's connection is broken
     * during the process of responding, pass a method reference or similar
     * here to ensure cleanup happens when the client connection is closed,
     * regardless of <i>how</i> it is closed.
     * 
     * @param run A runnable, close or cancel method reference or similar
     */
    default void onClose(Runnable run) {
        
    }
    /**
     * Get a query parameter, applying URL decoding.
     *
     * @param name The parameter name
     * @return A string if one is matched
     */
    default Optional<CharSequence> uriQueryParameter(CharSequence name) {
        return uriQueryParameter(name, true);
    }

    /**
     * Get a query parameter as a number, which may be one of Byte, Integer,
     * Short, Long, Double, Float, BigDecimal or BigInteger.
     *
     * @param <N> The number type
     * @param name The name of the query parameter
     * @param type The type to parse it to
     * @return An optional which may contain a number of the passed type
     */
    default <N extends Number> Optional<N> uriQueryParameter(CharSequence name, Class<N> type) {
        return asNumber(uriQueryParameter(name, false), type);
    }

    /**
     * Get a URI query parameter as a boolean.
     *
     * @param name A query parameter name
     * @return A boolean, if the query parameter is present
     */
    default Optional<Boolean> booleanUriQueryParameter(CharSequence name) {
        return uriQueryParameter(name, false)
                .map(val -> Strings.charSequencesEqual("true", val)
                ? Boolean.TRUE
                : Boolean.FALSE);
    }

    /**
     * Get a path element as a number.
     *
     * @param <N> A number type
     * @param index the index within the URL path of the parameter
     * @param type The type
     * @return An optional which may contain a number of the passed type
     */
    default <N extends Number> Optional<N> uriPathElement(int index, Class<N> type) {
        return asNumber(uriPathElement(index), type);
    }

    /**
     * Get the HTTP method of the request.
     *
     * @return A method such as "GET"
     */
    String httpMethod();

    /**
     * Determine if the method matches the string value of the passed object
     * (allowing for anything that resolves case insensitively to an http method
     * name to match).
     *
     * @param o An object
     * @return True if the method name is a case insensitive match for the
     * passed object
     */
    default boolean isMethod(Object o) {
        return notNull("o", o).toString().equalsIgnoreCase(httpMethod());
    }

    /**
     * Get the request URI.
     *
     * @param preferHeaders If true, parse any of the common proxy headers
     * x-forwarded-for, etc. and use that if present
     * @return A string
     */
    String requestUri(boolean preferHeaders);

    /**
     * Get the request URI preferring that value from the headers if there is
     * one.
     *
     * @return A request uri
     */
    default String requestUri() {
        return requestUri(true);
    }

    /**
     * Some implementations may use this mechanism to provide raw-access to the
     * underlying framework's request object.
     *
     * @param <T> A type
     * @param type A type
     * @return An optional containing the type if one can be prodced
     */
    default <T> Optional<T> unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    static <N extends Number> Optional<N> asNumber(
            Optional<CharSequence> value,
            Class<N> type) {
        try {
            if (type == Long.class) {
                return value.map(v -> type.cast(Strings.parseLong(v)));
            } else if (type == Integer.class) {
                return value.map(v -> type.cast(Strings.parseInt(v)));
            } else if (type == Short.class) {
                return value.map(val -> type.cast(Short.valueOf(val.toString())));
            } else if (type == Byte.class) {
                return value.map(val -> type.cast(Byte.valueOf(val.toString())));
            } else if (type == Double.class) {
                return value.map(val -> type.cast(Double.valueOf(val.toString())));
            } else if (type == Float.class) {
                return value.map(val -> type.cast(Float.valueOf(val.toString())));
            } else if (type == BigInteger.class) {
                return value.map(val -> type.cast(new BigInteger(val.toString())));
            } else if (type == BigDecimal.class) {
                return value.map(val -> type.cast(new BigDecimal(val.toString())));
            } else {
                throw new IllegalArgumentException("Unknown numeric type " + type);
            }
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }

}
