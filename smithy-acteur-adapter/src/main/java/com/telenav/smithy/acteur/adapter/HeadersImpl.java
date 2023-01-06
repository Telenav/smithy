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
package com.telenav.smithy.acteur.adapter;

import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.ConnectionHeaderData;
import com.mastfrog.acteur.header.entities.FrameOptions;
import com.mastfrog.acteur.header.entities.StrictTransportSecurity;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.ACCEPT_CHARSET;
import static com.mastfrog.acteur.headers.Headers.AUTHORIZATION;
import static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL;
import static com.mastfrog.acteur.headers.Headers.CONNECTION;
import static com.mastfrog.acteur.headers.Headers.CONTENT_DISPOSITION;
import static com.mastfrog.acteur.headers.Headers.CONTENT_LANGUAGE;
import static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH;
import static com.mastfrog.acteur.headers.Headers.CONTENT_MD5;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.ETAG;
import static com.mastfrog.acteur.headers.Headers.IF_MATCH;
import static com.mastfrog.acteur.headers.Headers.IF_MODIFIED_SINCE;
import static com.mastfrog.acteur.headers.Headers.IF_NONE_MATCH;
import static com.mastfrog.acteur.headers.Headers.IF_UNMODIFIED_SINCE;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Headers.LOCATION;
import static com.mastfrog.acteur.headers.Headers.REFERRER;
import static com.mastfrog.acteur.headers.Headers.RETRY_AFTER;
import static com.mastfrog.acteur.headers.Headers.STRICT_TRANSPORT_SECURITY;
import static com.mastfrog.acteur.headers.Headers.WWW_AUTHENTICATE;
import static com.mastfrog.acteur.headers.Headers.X_ACCEL_BUFFERING;
import static com.mastfrog.acteur.headers.Headers.X_FRAME_OPTIONS;
import static com.mastfrog.acteur.headers.Headers.X_REQUESTED_WITH;
import com.mastfrog.mime.MimeType;
import com.telenav.smithy.http.HeaderSpec;
import com.telenav.smithy.http.HeaderTypes;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.service.ServiceProvider;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(HeaderTypes.class)
public final class HeadersImpl extends HeaderTypes {

    @Override
    public HeaderSpec<String> stringHeader(CharSequence headerName) {
        return wrap(Headers.stringHeader(notNull("headerName", headerName)));
    }

    @Override
    public HeaderSpec<CharSequence> host() {
        return wrap(Headers.HOST);
    }

    @Override
    public HeaderSpec<Instant> lastModified() {
        return wrap(LAST_MODIFIED.toInstantHeader());
    }

    @Override
    public HeaderSpec<Instant> ifModifiedSince() {
        return wrap(IF_MODIFIED_SINCE.toInstantHeader());
    }

    @Override
    public HeaderSpec<Instant> ifUnmodifiedSince() {
        return wrap(IF_UNMODIFIED_SINCE.toInstantHeader());
    }

    @Override
    public HeaderSpec<ConnectionHeaderData> connection() {
        return wrap(CONNECTION);
    }

    @Override
    public HeaderSpec<Long> contentLength() {
        return wrap(new LongHeader(CONTENT_LENGTH));
    }

    @Override
    public HeaderSpec<Locale> contentLanguage() {
        return wrap(CONTENT_LANGUAGE);
    }

    @Override
    public HeaderSpec<Charset> acceptCharset() {
        return wrap(ACCEPT_CHARSET);
    }

    @Override
    public HeaderSpec<Duration> retryAfter() {
        return wrap(RETRY_AFTER);
    }

    @Override
    public HeaderSpec<CharSequence> ifNoneMatch() {
        return wrap(IF_NONE_MATCH);
    }

    @Override
    public HeaderSpec<CharSequence> ifMatch() {
        return wrap(IF_MATCH);
    }

    @Override
    public HeaderSpec<CharSequence> etag() {
        return wrap(ETAG);
    }

    @Override
    public HeaderSpec<CharSequence> contentDisposition() {
        return wrap(CONTENT_DISPOSITION);
    }

    @Override
    public HeaderSpec<CharSequence> referrer() {
        return wrap(REFERRER);
    }

    @Override
    public HeaderSpec<CharSequence> contentMd5() {
        return wrap(CONTENT_MD5);
    }

    @Override
    public HeaderSpec<CharSequence> wwwAuthenticate() {
        return wrap(WWW_AUTHENTICATE.toStringHeader());
    }

    @Override
    public HeaderSpec<URI> location() {
        return wrap(LOCATION);
    }

    @Override
    public HeaderSpec<MimeType> contentType() {
        return wrap(CONTENT_TYPE);
    }

    @Override
    public HeaderSpec<CacheControl> cacheControl() {
        return wrap(CACHE_CONTROL);
    }

    @Override
    public HeaderSpec<BasicCredentials> basicAuth() {
        return wrap(AUTHORIZATION);
    }

    @Override
    public HeaderSpec<StrictTransportSecurity> strictTransportSecurity() {
        return wrap(STRICT_TRANSPORT_SECURITY);
    }

    @Override
    public HeaderSpec<FrameOptions> xFrameOptions() {
        return wrap(X_FRAME_OPTIONS);
    }

    @Override
    public HeaderSpec<Boolean> xAccelBuffering() {
        return wrap(X_ACCEL_BUFFERING);
    }

    @Override
    public HeaderSpec<CharSequence> xRequestedWith() {
        return wrap(X_REQUESTED_WITH);
    }

    @Override
    public HeaderSpec<CharSequence> xForwardedProto() {
        return wrap(Headers.X_FORWARDED_PROTO);
    }

    @Override
    public HeaderSpec<Instant> dateHeader(CharSequence headerName) {
        return new DateHeaderSpec(headerName);
    }

    static <T> HeaderSpec<T> wrap(HeaderValueType<T> v) {
        return new Wrapper<>(v);
    }

    static <T> HeaderValueType<T> unwrap(HeaderSpec<T> head) {
        if (head instanceof Wrapper<?>) {
            return ((Wrapper<T>) head).header;
        }
        return null;
    }

    static class DateHeaderSpec implements HeaderSpec<Instant> {

        private final CharSequence name;

        public DateHeaderSpec(CharSequence name) {
            this.name = name;
        }

        @Override
        public Class<Instant> type() {
            return Instant.class;
        }

        @Override
        public CharSequence name() {
            return name;
        }

        @Override
        public CharSequence toCharSequence(Instant value) {
            return Headers.toISO2822Date(ZonedDateTime.ofInstant(value, ZoneId.of("GMT")));
        }

        @Override
        public Instant toValue(CharSequence value) {
            ZonedDateTime result = Headers.DATE.toValue(value);
            return result == null ? null : result.toInstant();
        }

    }

    static class Wrapper<T> implements HeaderSpec<T> {

        private final HeaderValueType<T> header;

        public Wrapper(HeaderValueType<T> header) {
            this.header = header;
        }

        @Override
        public Class<T> type() {
            return header.type();
        }

        @Override
        public CharSequence name() {
            return header.name();
        }

        @Override
        public CharSequence toCharSequence(T value) {
            return header.toCharSequence(value);
        }

        @Override
        public T toValue(CharSequence value) {
            return header.toValue(value);
        }

        public String toString() {
            return header.name() + "<" + header.type().getSimpleName() + ">";
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 11 * hash + Objects.hashCode(this.header);
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
            final Wrapper<?> other = (Wrapper<?>) obj;
            return Objects.equals(this.header, other.header);
        }
    }

    static class LongHeader implements HeaderValueType<Long> {

        private final HeaderValueType<Number> orig;

        public LongHeader(HeaderValueType<Number> orig) {
            this.orig = orig;
        }

        @Override
        public Class<Long> type() {
            return Long.class;
        }

        @Override
        public CharSequence name() {
            return orig.name();
        }

        @Override
        public Long toValue(CharSequence value) {
            Number result = orig.toValue(value);
            return result == null ? null : result.longValue();
        }
    }
}
