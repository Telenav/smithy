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
package com.telenav.requestids;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Singleton;

/**
 * Provides unique request ids for logging purposes. The default request ID
 * factory uses 128 bits of randomness, and includes an obfuscated sequence
 * number and creation timestamp.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class DefaultRequestIdFactory implements RequestIdFactory<DefaultRequestId> {

    private static final SecureRandom SEC_RAND;

    static {
        try {
            SEC_RAND = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }
    private final AtomicLong counter = new AtomicLong();
    private final Random rnd;

    /**
     * Create a new request id factory seeded from
     * <code>SecureRandom.getInstanceStrong()</code>.
     */
    public DefaultRequestIdFactory() {
        rnd = new Random(SEC_RAND.nextLong());
    }

    /**
     * Create a new DefaultRequestIdFactory with a specific random seed for
     * deterministic ids in tests.
     *
     * @param seed A random seed
     */
    public DefaultRequestIdFactory(long seed) {
        rnd = new Random(seed);
    }

    /**
     * Get the next (likely) unique, non-repeating id.
     *
     * @return An id
     */
    @Override
    public DefaultRequestId nextId() {
        return new DefaultRequestId(counter.getAndIncrement(), rnd.nextLong(), rnd.nextLong());
    }

    /**
     * Parse a request ID object from a string, if possible.
     *
     * @param txt A string, likely from an http header
     * @return A request id if possible, or an empty optional
     */
    @Override
    public Optional<DefaultRequestId> fromString(CharSequence txt) {
        return DefaultRequestId.fromString(txt);
    }

    /**
     * Returns <code>DefaultRequestId.class</code>.
     *
     * @return The class of DefaultRequestId
     */
    @Override
    public Class<DefaultRequestId> idType() {
        return DefaultRequestId.class;
    }
}
