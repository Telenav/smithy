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
package com.telenav.vertx.guice.verticle;

import com.telenav.vertx.guice.util.CustomizerTypeOrInstanceList;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class VerticleInfo {

    final CustomizerTypeOrInstanceList<HttpServerOptions> httpOptionsConfigurer;
    final int port;
    final CustomizerTypeOrInstanceList<Router> routerCustomizer;
    final List<RouteEntry> routes;

    public VerticleInfo(
            CustomizerTypeOrInstanceList<HttpServerOptions> httpOptionsConfigurer, int port,
            CustomizerTypeOrInstanceList<Router> routerCustomizer, List<RouteEntry> routes) {
        this.httpOptionsConfigurer = httpOptionsConfigurer;
        this.port = port;
        this.routerCustomizer = routerCustomizer;
        this.routes = routes;
    }

}
