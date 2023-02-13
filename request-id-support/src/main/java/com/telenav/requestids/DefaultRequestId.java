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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * A loggable request id for tracing requests, which consists of 128 bits of
 * randomness, a creation timestamp and a server-process-local request index
 * (these are obfuscated in the string value).
 */
public final class DefaultRequestId implements Comparable<DefaultRequestId> {

    private static final long EPOCH = 1676003165472L;
    private static final DefaultRequestId NONE = new DefaultRequestId();
    private final long created;
    private final long uid1;
    private final long uid2;
    private final long index;
    private String stringValue;

    DefaultRequestId(long index, long uid1, long uid2) {
        this.index = index;
        this.uid1 = uid1;
        this.uid2 = uid2;
        this.created = timestamp();
    }

    private DefaultRequestId() {
        this.created = 0;
        this.uid1 = 0;
        this.uid2 = 0;
        this.index = 0;
        this.stringValue = "-none-";
    }

    DefaultRequestId(long created, long uid1, long uid2, long index, String stringValue) {
        this.created = created;
        this.uid1 = uid1;
        this.uid2 = uid2;
        this.index = index;
        this.stringValue = stringValue;
    }

    /**
     * Parse a request id back from a string if possible.
     *
     * @param txt A base64 string.
     *
     * @return An optional containing the result if it parses
     */
    public static Optional<DefaultRequestId> fromString(CharSequence txt) {
        StringBuilder sb = new StringBuilder(txt);
        while (sb.length() % 4 != 0) {
            sb.append('=');
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(sb.toString());
            if (bytes.length != Long.BYTES * 4) {
                return Optional.empty();
            }
            LongBuffer buf = ByteBuffer.wrap(bytes).asLongBuffer();
            long uid1 = buf.get();
            long createdMasked = buf.get();
            long indexMasked = buf.get();
            long uid2 = buf.get();
            long created = createdMasked ^ uid1;
            long index = indexMasked ^ uid2;
            return Optional.of(new DefaultRequestId(created, uid1, uid2, index, txt.toString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @JsonCreator
    public static DefaultRequestId fromStringUnsafe(String val) {
        return fromString(val).get();
    }

    /**
     * Get a null-object instance for use in logging where no request id is
     * visible in scope.
     *
     * @return A zeroed out request id
     */
    public static DefaultRequestId none() {
        return NONE;
    }

    /**
     * Determine if this is the null instance.
     *
     * @return True if this is the return value of
     * <code>RequestId.none()</code>.
     */
    public boolean isNone() {
        return created == 0 && uid1 == 0 && uid2 == 0 && index == 0;
    }

    /**
     * Get a timestamp using our epoch (for brevity).
     *
     * @return A long
     */
    private static long timestamp() {
        return System.currentTimeMillis() - EPOCH;
    }

    /**
     * Get the time at which this request id was created.
     *
     * @return An instant
     */
    public Instant initiatedAt() {
        return Instant.ofEpochMilli(created + EPOCH);
    }

    /**
     * Get the duration since this id was created.
     *
     * @return The age of this id
     */
    public Duration age() {
        return Duration.ofMillis(timestamp() - created);
    }

    private String stripPadding(StringBuilder sb) {
        while (sb.charAt(sb.length() - 1) == '=') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private String stringValue() {
        byte[] bytes = new byte[4 * Long.BYTES];
        ByteBuffer.wrap(bytes).asLongBuffer().put(uid1).put(created ^ uid1).put(index ^ uid2).put(uid2);
        return stripPadding(new StringBuilder(Base64.getEncoder().encodeToString(bytes)));
    }

    @Override
    @JsonValue
    public String toString() {
        return stringValue == null ? stringValue = stringValue() : stringValue;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (int) (this.created ^ (this.created >>> 32));
        hash = 53 * hash + (int) (this.uid1 ^ (this.uid1 >>> 32));
        hash = 53 * hash + (int) (this.uid2 ^ (this.uid2 >>> 32));
        hash = 53 * hash + (int) (this.index ^ (this.index >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != DefaultRequestId.class) {
            return false;
        }
        final DefaultRequestId other = (DefaultRequestId) obj;
        return this.created == other.created && this.index == other.index && this.uid1 == other.uid1 && this.uid2 == other.uid2;
    }

    @Override
    public int compareTo(DefaultRequestId other) {
        int result = Long.compare(created, other.created);
        if (result == 0) {
            result = Long.compare(index, other.index);
        }
        return result;
    }

}
