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
package com.telenav.smithy.vertx.probe;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * @author Tim Boudreau
 */
final class EndRequestResponseCycleHandler<Ops extends Enum<Ops>> implements Handler<AsyncResult<Void>> {

    private final Probe<Ops> probe;
    private final RoutingContext ctx;
    private final Ops operation;

    EndRequestResponseCycleHandler(Ops operation, Probe<Ops> probe, RoutingContext ctx) {
        this.probe = probe;
        this.ctx = ctx;
        this.operation = operation;
    }

    @Override
    public void handle(AsyncResult<Void> event) {
        Probe.fixAge(ctx);
        if (event.cause() != null) {
            probe.onFailure(operation, ctx, event.cause());
        }
        probe.onResponseCompleted(operation, ctx, ctx.response().getStatusCode());
    }

}
