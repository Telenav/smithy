/*
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.requestids;

import java.util.Optional;

/**
 * Factory for request IDs. The only characteristic request IDs are required to
 * have is a string representation suitable for use in an HTTP header without
 * special quoting or other measures.
 * <p>
 * In a generated server, it is optional to use request IDs - a factory can be
 * bound on startup. Request IDs are useful for logging, particularly in complex
 * multi-step systems or chained web service calls, in order to track work done
 * on behalf of a request across a variety of subsystems.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface RequestIdFactory<T> {

    /**
     * Parse a request ID object from a string, if possible.
     *
     * @param txt A string, likely from an http header
     * @return A request id if possible, or an empty optional
     */
    Optional<T> fromString(CharSequence txt);

    /**
     * Get the next (likely) unique, non-repeating id.
     *
     * @return An id
     */
    T nextId();

    /**
     * Returns the type of id this factory returns.
     *
     * @return The class object for T
     */
    Class<T> idType();

}
