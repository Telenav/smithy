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
