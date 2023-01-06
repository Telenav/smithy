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
package com.telenav.smithy.vertx.adapter;

import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.ConnectionHeaderData;
import com.mastfrog.acteur.header.entities.FrameOptions;
import com.mastfrog.acteur.header.entities.StrictTransportSecurity;
import com.mastfrog.mime.MimeType;
import com.telenav.smithy.http.HeaderSpec;
import com.telenav.smithy.http.HeaderTypes;
import com.mastfrog.util.service.ServiceProvider;
import com.mastfrog.util.time.TimeUtil;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Implementation of HeaderTypes for VertX so we can parse headers into
 * appropriate types.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(HeaderTypes.class)
public class VertxHeaderTypes extends HeaderTypes {

    // This is a rough copy of the Headers class in Acteur, using
    // only the payload data types split out into their own library
    // to eliminate any dependency on the framework.
    //
    // Note the invalid value handling is better there - could use some
    // polishing
    @Override
    public HeaderSpec<Instant> dateHeader(CharSequence headerName) {
        return timeHeader(headerName.toString());
    }

    @Override
    public HeaderSpec<Instant> lastModified() {
        return timeHeader("last-modified");
    }

    @Override
    public HeaderSpec<Instant> ifModifiedSince() {
        return timeHeader("if-modified-since");
    }

    @Override
    public HeaderSpec<Instant> ifUnmodifiedSince() {
        return timeHeader("if-unmodified-since");
    }

    @SuppressWarnings("deprecation")
    private HeaderSpec<Instant> timeHeader(String name) {
        return charSequenceHeader(name)
                .convert(Instant.class,
                        v -> {
                            String s = v.toString();
                            try {
                                // DateTimeFormatter will complain if, say Tuesday
                                // should be Wednesday - we don't want that here -
                                // as they say, be liberal in what you accept and
                                // strict in what you emit.
                                return TimeUtil.fromHttpHeaderFormat(s).toInstant();
                            } catch (DateTimeParseException ex) {
                                return Instant.ofEpochMilli(java.util.Date.parse(s));
                            }
                        },
                        t -> TimeUtil.toHttpHeaderFormat(ZonedDateTime.ofInstant(t, ZoneId.of("Z")))
                );
    }

    @Override
    public HeaderSpec<String> stringHeader(CharSequence headerName) {
        return charSequenceHeader(headerName.toString())
                .convert(String.class,
                        Object::toString,
                        Object::toString);
    }

    @Override
    public HeaderSpec<ConnectionHeaderData> connection() {
        return charSequenceHeader("connection")
                .convert(ConnectionHeaderData.class,
                        ConnectionHeaderData::fromString,
                        Object::toString);
    }

    @Override
    public HeaderSpec<Long> contentLength() {
        return charSequenceHeader("content-length")
                .convert(Long.class,
                        v -> Long.parseLong(v.toString()),
                        Object::toString);
    }

    @Override
    public HeaderSpec<Locale> contentLanguage() {
        return charSequenceHeader("content-language")
                .convert(Locale.class, v -> Locale.forLanguageTag(v.toString()),
                        Locale::toLanguageTag);
    }

    @Override
    public HeaderSpec<Charset> acceptCharset() {
        return charSequenceHeader("accept-charset")
                .convert(Charset.class,
                        v -> Charset.forName(v.toString()),
                        Object::toString);
    }

    @Override
    public HeaderSpec<Duration> retryAfter() {
        return charSequenceHeader("retry-after")
                .convert(Duration.class,
                        val -> Duration.ofSeconds(Long.parseLong(val.toString())),
                        dur -> Long.toString(dur.toSeconds()));
    }

    @Override
    public HeaderSpec<CharSequence> ifNoneMatch() {
        return charSequenceHeader("if-none-match")
                .convert(CharSequence.class,
                        VertxHeaderTypes::requote, VertxHeaderTypes::dequote);
    }

    @Override
    public HeaderSpec<CharSequence> ifMatch() {
        return charSequenceHeader("if-match")
                .convert(CharSequence.class,
                        VertxHeaderTypes::requote, VertxHeaderTypes::dequote);
    }

    @Override
    public HeaderSpec<CharSequence> etag() {
        // ETag-like headers are a special case in that they are sometimes
        // quoted, sometimes not, and we need to match on either
        return charSequenceHeader("etag")
                .convert(CharSequence.class,
                        VertxHeaderTypes::requote, VertxHeaderTypes::dequote);
    }

    static CharSequence dequote(CharSequence seq) {
        if (seq == null || seq.length() <= 1) {
            return seq;
        }
        int end = seq.length() - 1;
        if (seq.charAt(0) == '"' && seq.charAt(end) == '"') {
            return seq.subSequence(1, end);
        }
        return seq;
    }

    static CharSequence requote(CharSequence seq) {
        if (seq.length() <= 1) {
            return seq;
        }
        int end = seq.length() - 1;
        if (seq.charAt(0) != '"' && seq.charAt(end) != '"') {
            return "\"" + seq + "\"";
        }
        return seq;
    }

    @Override
    public HeaderSpec<CharSequence> contentDisposition() {
        return charSequenceHeader("content-disposition");
    }

    @Override
    public HeaderSpec<CharSequence> referrer() {
        return charSequenceHeader("referer");
    }

    @Override
    public HeaderSpec<CharSequence> contentMd5() {
        return charSequenceHeader("content-md5");
    }

    @Override
    public HeaderSpec<CharSequence> wwwAuthenticate() {
        return charSequenceHeader("WWW-Authenticate");
    }

    @Override
    public HeaderSpec<URI> location() {
        return charSequenceHeader("location")
                .convert(URI.class, v -> URI.create(v.toString()), Object::toString);
    }

    @Override
    public HeaderSpec<MimeType> contentType() {
        return charSequenceHeader("content-type")
                .convert(MimeType.class,
                        MimeType::parse,
                        Object::toString);
    }

    @Override
    public HeaderSpec<CacheControl> cacheControl() {
        return charSequenceHeader("cache-control")
                .convert(CacheControl.class,
                        CacheControl::fromString,
                        Object::toString);
    }

    @Override
    public HeaderSpec<BasicCredentials> basicAuth() {
        return charSequenceHeader("authorization")
                .convert(BasicCredentials.class,
                        BasicCredentials::parse,
                        Object::toString);
    }

    @Override
    public HeaderSpec<StrictTransportSecurity> strictTransportSecurity() {
        return charSequenceHeader("strict-transport-security")
                .convert(StrictTransportSecurity.class,
                        StrictTransportSecurity::parse,
                        Object::toString);
    }

    @Override
    public HeaderSpec<FrameOptions> xFrameOptions() {
        return charSequenceHeader("x-frame-options")
                .convert(FrameOptions.class,
                        FrameOptions::parse,
                        Object::toString);
    }

    @Override
    public HeaderSpec<Boolean> xAccelBuffering() {
        return charSequenceHeader("x-requested-with")
                .convert(Boolean.class,
                        v -> "on".equals(v.toString()),
                        v -> v ? "on" : "off");
    }

    @Override
    public HeaderSpec<CharSequence> xRequestedWith() {
        return charSequenceHeader("x-requested-with");
    }

    @Override
    public HeaderSpec<CharSequence> xForwardedProto() {
        return charSequenceHeader("x-forwarded-proto");
    }

    @Override
    public HeaderSpec<CharSequence> host() {
        return charSequenceHeader("host");
    }

    private CS charSequenceHeader(String name) {
        return new CS(name);
    }

    static class CS implements HeaderSpec<CharSequence> {

        private final String name;

        CS(String name) {
            this.name = name;
        }

        @Override
        public Class<CharSequence> type() {
            return CharSequence.class;
        }

        @Override
        public CharSequence name() {
            return name;
        }

        @Override
        public CharSequence toCharSequence(CharSequence value) {
            return value;
        }

        @Override
        public CharSequence toValue(CharSequence value) {
            return value;
        }

        <T> HeaderSpec<T> convert(Class<T> type, Function<CharSequence, T> toValue,
                Function<T, CharSequence> fromValue) {
            return new Converted<>(type, toValue, fromValue);
        }

        class Converted<T> implements HeaderSpec<T> {

            private final Class<T> type;
            private final Function<CharSequence, T> toValue;
            private final Function<T, CharSequence> fromValue;

            Converted(Class<T> type, Function<CharSequence, T> toValue,
                    Function<T, CharSequence> fromValue) {
                this.type = type;
                this.toValue = toValue;
                this.fromValue = fromValue;
            }

            @Override
            public Class<T> type() {
                return type;
            }

            @Override
            public CharSequence name() {
                return CS.this.name();
            }

            @Override
            public CharSequence toCharSequence(T value) {
                return fromValue.apply(value);
            }

            @Override
            public T toValue(CharSequence value) {
                return toValue.apply(value);
            }
        }
    }

    static final class HS<T> implements HeaderSpec<T> {

        private final Function<String, T> from;
        private final Function<T, String> to;
        private final String name;
        private final Class<T> type;

        public HS(String name, Class<T> type, Function<String, T> from, Function<T, String> to) {
            this.type = type;
            this.name = name;
            this.from = from;
            this.to = to;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public CharSequence name() {
            return name;
        }

        @Override
        public CharSequence toCharSequence(T value) {
            return to.apply(value);
        }

        @Override
        public T toValue(CharSequence value) {
            return from.apply(value.toString());
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 17 * hash + Objects.hashCode(this.name);
            hash = 17 * hash + Objects.hashCode(this.type);
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
            final HS<?> other = (HS<?>) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.type, other.type);
        }

    }
}
