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

import com.mastfrog.acteur.header.entities.BasicCredentials;
import com.mastfrog.acteur.header.entities.CacheControl;
import com.mastfrog.acteur.header.entities.ConnectionHeaderData;
import com.mastfrog.acteur.header.entities.FrameOptions;
import com.mastfrog.acteur.header.entities.StrictTransportSecurity;
import com.mastfrog.mime.MimeType;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 *
 * @author Tim Boudreau
 */
public abstract class HeaderTypes {

    private static HeaderTypes INSTANCE;

    protected HeaderTypes() {

    }

    public static HeaderTypes headerTypes() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        Optional<HeaderTypes> it = ServiceLoader.load(HeaderTypes.class).findFirst();
        return INSTANCE = it.orElseThrow(() -> new Error(
                "No instance of HeaderTypes in META-INF/services"));
    }
    
    public abstract HeaderSpec<Instant> dateHeader(CharSequence headerName);

    public abstract HeaderSpec<String> stringHeader(CharSequence headerName);

    public abstract HeaderSpec<Instant> lastModified();

    public abstract HeaderSpec<Instant> ifModifiedSince();

    public abstract HeaderSpec<Instant> ifUnmodifiedSince();

    public abstract HeaderSpec<ConnectionHeaderData> connection();

    public abstract HeaderSpec<Long> contentLength();

    public abstract HeaderSpec<Locale> contentLanguage();

    public abstract HeaderSpec<Charset> acceptCharset();

    public abstract HeaderSpec<Duration> retryAfter();

    public abstract HeaderSpec<CharSequence> ifNoneMatch();

    public abstract HeaderSpec<CharSequence> ifMatch();

    public abstract HeaderSpec<CharSequence> etag();

    public abstract HeaderSpec<CharSequence> contentDisposition();

    public abstract HeaderSpec<CharSequence> referrer();

    public abstract HeaderSpec<CharSequence> contentMd5();

    public abstract HeaderSpec<CharSequence> wwwAuthenticate();

    public abstract HeaderSpec<URI> location();

    public abstract HeaderSpec<MimeType> contentType();

    public abstract HeaderSpec<CacheControl> cacheControl();

    public abstract HeaderSpec<BasicCredentials> basicAuth();

    public abstract HeaderSpec<StrictTransportSecurity> strictTransportSecurity();

    public abstract HeaderSpec<FrameOptions> xFrameOptions();

    public abstract HeaderSpec<Boolean> xAccelBuffering();

    public abstract HeaderSpec<CharSequence> xRequestedWith();

    public abstract HeaderSpec<CharSequence> xForwardedProto();

    public abstract HeaderSpec<CharSequence> host();
}
