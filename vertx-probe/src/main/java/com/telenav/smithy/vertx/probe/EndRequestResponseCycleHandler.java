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
package com.telenav.smithy.vertx.probe;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;

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
        } else {
            probe.onResponseCompleted(operation, ctx, ctx.response().getStatusCode());
        }
    }

}
