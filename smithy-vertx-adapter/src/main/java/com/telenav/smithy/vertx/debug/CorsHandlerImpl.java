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
package com.telenav.smithy.vertx.debug;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Origin;
import io.vertx.ext.web.impl.RoutingContextInternal;
import io.vertx.ext.web.impl.Utils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * Based partially on original authored by David Dossot
 *
 * @author <a href="david@dossot.net">David Dossot</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CorsHandlerImpl implements CorsHandler {

    private final Pattern allowedOrigin;
    private Set<Origin> allowedOrigins;

    private String allowedMethodsString;
    private String allowedHeadersString;
    private String exposedHeadersString;
    private boolean allowCredentials;
    private String maxAgeSeconds;
    private boolean allowPrivateNetwork;
    private final Set<String> allowedMethods = new LinkedHashSet<>();
    private final Set<String> allowedHeaders = new LinkedHashSet<>();
    private final Set<String> exposedHeaders = new LinkedHashSet<>();

    public CorsHandlerImpl(String allowedOriginPattern) {
        Objects.requireNonNull(allowedOriginPattern);
        if ("*".equals(allowedOriginPattern)) {
            allowedOrigin = null;
        } else {
            allowedOrigin = Pattern.compile(allowedOriginPattern);
        }
        allowedOrigins = null;
    }
    
    private void log(String what) {
        System.out.println(what);
    }

    public CorsHandlerImpl() {
        allowedOrigin = null;
        allowedOrigins = null;
    }

    private boolean starOrigin() {
        return allowedOrigin == null && allowedOrigins == null;
    }

    private boolean staticOrigin() {
        return allowedOrigin == null && allowedOrigins != null && allowedOrigins.size() == 1;
    }

    @Override
    public CorsHandler addOrigin(String origin) {
        if (allowedOrigin != null) {
            throw new IllegalStateException("Cannot mix Pattern mode and Origin List mode");
        }
        if (allowedOrigins == null) {
            if (origin.equals("*")) {
                // we signal any as null
                return this;
            }
            allowedOrigins = new LinkedHashSet<>();
        } else {
            if (origin.equals("*")) {
                // we signal any as null
                throw new IllegalStateException("Cannot mix '*' with explicit origins");
            }
        }
        allowedOrigins.add(Origin.parse(origin));
        return this;
    }

    @Override
    public CorsHandler addOrigins(List<String> origins) {
        if (allowedOrigin != null) {
            throw new IllegalStateException("Cannot mix Pattern mode and Origin List mode");
        }
        if (allowedOrigins == null) {
            allowedOrigins = new LinkedHashSet<>();
        }
        for (String origin : origins) {
            allowedOrigins.add(Origin.parse(origin));
        }
        return this;
    }

    @Override
    public CorsHandler allowedMethod(HttpMethod method) {
        allowedMethods.add(method.name());
        allowedMethodsString = String.join(",", allowedMethods);
        return this;
    }

    @Override
    public CorsHandler allowedMethods(Set<HttpMethod> methods) {
        for (HttpMethod method : methods) {
            allowedMethods.add(method.name());
        }
        allowedMethodsString = String.join(",", allowedMethods);
        return this;
    }

    @Override
    public CorsHandler allowedHeader(String headerName) {
        allowedHeaders.add(headerName);
        allowedHeadersString = String.join(",", allowedHeaders);
        return this;
    }

    @Override
    public CorsHandler allowedHeaders(Set<String> headerNames) {
        allowedHeaders.addAll(headerNames);
        allowedHeadersString = String.join(",", allowedHeaders);
        return this;
    }

    @Override
    public CorsHandler exposedHeader(String headerName) {
        exposedHeaders.add(headerName);
        exposedHeadersString = String.join(",", exposedHeaders);
        return this;
    }

    @Override
    public CorsHandler exposedHeaders(Set<String> headerNames) {
        exposedHeaders.addAll(headerNames);
        exposedHeadersString = String.join(",", exposedHeaders);
        return this;
    }

    @Override
    public CorsHandler allowCredentials(boolean allow) {
        this.allowCredentials = allow;
        return this;
    }

    @Override
    public CorsHandler maxAgeSeconds(int maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds == -1 ? null : String.valueOf(maxAgeSeconds);
        return this;
    }

    @Override
    public CorsHandler allowPrivateNetwork(boolean allow) {
        this.allowPrivateNetwork = allow;
        return this;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        log("CorsHandler.handle " + request.method() + " " + request.path() + " ori " + allowedOrigins);
        HttpServerResponse response = context.response();
        String origin = context.request().headers().get(ORIGIN);
        if (origin == null) {
            origin = "null";
        }
        if (origin == null) {
            log("  ORIGIN IS NULL");
            // https://fetch.spec.whatwg.org/#cors-protocol-and-http-caches
            // If CORS protocol requirements are more complicated than setting `Access-Control-Allow-Origin` to *
            // or a static origin, `Vary` is to be used.
            if (!starOrigin() && !staticOrigin()) {
                Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ORIGIN);
            }
            // Not a CORS request - we don't set any headers and just call the next handler
            context.next();
        } else if (isValidOrigin(origin)) {
            String accessControlRequestMethod = request.headers().get(ACCESS_CONTROL_REQUEST_METHOD);
            log("  IS VALID ORIGIN WITH " + accessControlRequestMethod);
            if (request.method() == HttpMethod.OPTIONS && accessControlRequestMethod != null) {
                // Pre-flight request
                addCredentialsAndOriginHeader(response, origin);
                if (allowedMethodsString != null) {
                    response.putHeader(ACCESS_CONTROL_ALLOW_METHODS, allowedMethodsString);
                }
                if (allowedHeadersString != null) {
                    response.putHeader(ACCESS_CONTROL_ALLOW_HEADERS, allowedHeadersString);
                } else {
                    if (request.headers().contains(ACCESS_CONTROL_REQUEST_HEADERS)) {
                        // echo back the request headers
                        response.putHeader(ACCESS_CONTROL_ALLOW_HEADERS, request.getHeader(ACCESS_CONTROL_REQUEST_HEADERS));
                        // in this case we need to vary on this header
                        Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ACCESS_CONTROL_REQUEST_HEADERS);
                    }
                }
                if (maxAgeSeconds != null) {
                    response.putHeader(ACCESS_CONTROL_MAX_AGE, maxAgeSeconds);
                }
                if (request.headers().contains(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK) && allowPrivateNetwork) {
                    response.putHeader(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, "true");
                }
                response
                        // for old Safari
                        .putHeader(CONTENT_LENGTH, "0")
                        .setStatusCode(204)
                        .end();

            } else {
                log("  ADD VARY HEADER");
                Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ORIGIN);
                addCredentialsAndOriginHeader(response, origin);
                if (exposedHeadersString != null) {
                    response.putHeader(ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeadersString);
                }
                ((RoutingContextInternal) context).visitHandler(RoutingContextInternal.CORS_HANDLER);
                context.next();
            }
        } else {
            log("  INVALID ORIGIN");
            context
                    .response()
                    .setStatusMessage("CORS Rejected - Invalid origin");
            context
                    .fail(403, new IllegalStateException("CORS Rejected - Invalid origin"));
        }
    }

    private void addCredentialsAndOriginHeader(HttpServerResponse response, String origin) {
        log("    add header");
        if (allowCredentials) {
            response.putHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            // Must be exact origin (not '*') in case of credentials
            response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        } else {
            // Can be '*' too
            response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, getAllowedOrigin(origin));
        }
    }

    private boolean isValidOrigin(String origin) {

        // * means accept all origins
        if (starOrigin()) {
            return Origin.isValid(origin);
        }

        if (allowedOrigin != null) {
            // check for allowed origin pattern match
            return allowedOrigin.matcher(origin).matches();
        }

        // check whether origin is contained within allowed origin set
        for (Origin allowedOrigin : allowedOrigins) {
            if (allowedOrigin.sameOrigin(origin)) {
                return true;
            }
        }

        return false;
    }

    private String getAllowedOrigin(String origin) {
        if (starOrigin()) {
            return "*";
        }
        return origin;
    }
}
