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
import io.vertx.ext.web.handler.SecurityPolicyHandler;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public interface CorsHandler extends SecurityPolicyHandler {

  /**
   * Create a CORS handler using a regular expression to match origins. An origin follows rfc6454#section-7
   * and is expected to have the format: {@code <scheme> "://" <hostname> [ ":" <port> ]}
   *
   * @param allowedOriginPattern  the allowed origin pattern
   * @return  the handler
   */
  static CorsHandler create(String allowedOriginPattern) {
    return new CorsHandlerImpl(allowedOriginPattern);
  }

  /**
   * Create a empty CORS handler that allows {@code *} origin.
   * @return the handler
   */
  static CorsHandler create() {
    return new CorsHandlerImpl();
  }

  /**
   * Add an origin to the list of allowed Origins. An origin follows rfc6454#section-7
   * and is expected to have the format: {@code <scheme> "://" <hostname> [ ":" <port> ]}
   * @param origin the well formatted static origin
   * @return self
   */
  CorsHandler addOrigin(String origin);

  /**
   * Set the list of allowed origins. An origin follows rfc6454#section-7
   * and is expected to have the format: {@code <scheme> "://" <hostname> [ ":" <port> ]}
   * @param origins the well formatted static origin list
   * @return self
   */
  CorsHandler addOrigins(List<String> origins);

  /**
   * Add an allowed method
   *
   * @param method  the method to add
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler allowedMethod(HttpMethod method);

  /**
   * Add a set of  allowed methods
   *
   * @param methods the methods to add
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler allowedMethods(Set<HttpMethod> methods);

  /**
   * Add an allowed header
   *
   * @param headerName  the allowed header name
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler allowedHeader(String headerName);

  /**
   * Add a set of allowed headers
   *
   * @param headerNames  the allowed header names
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler allowedHeaders(Set<String> headerNames);

  /**
   * Add an exposed header
   *
   * @param headerName  the exposed header name
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler exposedHeader(String headerName);

  /**
   * Add a set of exposed headers
   *
   * @param headerNames  the exposed header names
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler exposedHeaders(Set<String> headerNames);

  /**
   * Set whether credentials are allowed. Note that user agents will block
   * requests that use a wildcard as origin and include credentials.
   *
   * From the MDN documentation you can read:
   *
   * <blockquote>
   * Important note: when responding to a credentialed request,
   * server must specify a domain, and cannot use wild carding.
   * </blockquote>
   *
   * @param allow true if allowed
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler allowCredentials(boolean allow);

  /**
   * Set how long the browser should cache the information
   *
   * @param maxAgeSeconds  max age in seconds
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler maxAgeSeconds(int maxAgeSeconds);

  /**
   * Set whether access from public to private networks are allowed.
   * Defaults to false
   *
   * @param allow true if allowed
   * @return a reference to this, so the API can be used fluently
   */
  CorsHandler allowPrivateNetwork(boolean allow);

}
