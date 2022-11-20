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
package com.mastfrog.smithy.client.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.smithy.client.base.ClientConfig.debugLog;
import com.mastfrog.smithy.client.state.CompletionReason;
import com.mastfrog.smithy.client.state.HttpOperation;
import com.mastfrog.smithy.client.state.HttpOperationState;
import com.mastfrog.smithy.client.state.HttpOperationStateHolder;
import com.mastfrog.smithy.client.state.State;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Delayed;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.mastfrog.smithy.client.result.ServiceResult;
import static com.mastfrog.smithy.client.state.CompletionReason.TIMED_OUT;
import java.util.function.Supplier;

/**
 * Caches bytes from a JSON response and notifies a caller when appropriate.
 *
 * @author Tim Boudreau
 */
final class JacksonBodyHandler<T>
        implements HttpResponse.BodyHandler<ServiceResult<T>>, HttpResponse.BodySubscriber<ServiceResult<T>>, Delayed {

    private static final Pattern CHARSET_PATTERN_IN_CONTENT_TYPE_HEADER
            = Pattern.compile("charset=(\\S+)[;]?");
    private final Duration timeoutAfter;

    private final ObjectMapper mapper;
    private final Class<T> type;
    final CompletableFuture<ServiceResult<T>> bodyFuture = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private volatile Flow.Subscription subscription;
    private volatile HttpResponse.ResponseInfo info;
    private final com.mastfrog.smithy.client.state.HttpOperationStateHolder state;

    public JacksonBodyHandler(Duration timeoutAfter, ObjectMapper mapper, Class<T> type) {
        state = new HttpOperationStateHolder(HttpOperationState.INITIAL
                .withInitiatedSeconds(ClientConfig.currentSecond())
                .withState(State.INITIAL), this::onChange);
        this.timeoutAfter = timeoutAfter;
        this.mapper = mapper;
        this.type = type;
    }

    private void onChange(HttpOperationState previousState, HttpOperationState changedToState, Supplier<HttpOperationState> currentState) {
        debugLog("State change\nFROM: " + previousState + "\n  TO: "
                + changedToState);
        if (changedToState.reason() == TIMED_OUT) {
            Thread.dumpStack();
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long now = System.currentTimeMillis();
        long exp = expiresAt().toEpochMilli();
        return unit.convert(Math.max(0, exp - now), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(getDelay(TimeUnit.NANOSECONDS),
                o.getDelay(TimeUnit.NANOSECONDS));
    }

    Instant expiresAt() {
        return Instant.ofEpochMilli(
                (state.state().initiatedSeconds() * 1000L)
                + (ClientConfig.STARTUP * 1000L)
                + timeoutAfter.toMillis());
    }

    boolean checkTimeout() {
        if (age().compareTo(timeoutAfter) > 0) {
            HttpOperation oldState = state.getAndUpdate(old -> {
                return old.withReason(CompletionReason.TIMED_OUT)
                        .withState(State.DONE)
                        .withFutureCompleted(true);
            });
            if (!oldState.futureCompleted()) {
                completeFuture(ServiceResult.timeout());
            }
            return true;
        }
        return false;
    }

    public Duration age() {
        return state.state().age();
    }

    public HttpOperation state() {
        return state.state();
    }

    public boolean cancel() {
        debugLog("Cancel");
        boolean result = bodyFuture.cancel(true);
        subscription().ifPresent(sub -> sub.cancel());
        state.getAndUpdate(old -> old.withReason(CompletionReason.CANCELLED));
        return result;
    }

    @Override
    public CompletionStage<ServiceResult<T>> getBody() {
        return bodyFuture;
    }

    synchronized Optional<Flow.Subscription> subscription() {
        return Optional.ofNullable(subscription);
    }

    private void completeFuture(ServiceResult<T> result) {
        HttpOperation oldState = state.getAndUpdate(old -> old.withFutureCompleted(true));
        if (!oldState.futureCompleted()) {
            bodyFuture.complete(result);
        }
    }

    @Override
    public synchronized void onSubscribe(Flow.Subscription subscription) {
        if (bodyFuture.isCancelled() || bodyFuture.isCompletedExceptionally() || checkTimeout()) {
            debugLog("OnSubscribe but is cancelled");
            subscription.cancel();
            HttpOperation oldState = state.getAndUpdate(old -> {
                if (old.reason() == CompletionReason.NOT_COMPLETED) {
                    return old.withReason(CompletionReason.CANCELLED);
                }
                return old;
            });
            if (!oldState.futureCompleted()) {
                completeFuture(ServiceResult.cancelled());
            }
            return;
        }
        debugLog("onSubscribe");
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        if (checkTimeout()) {
            debugLog("Timed out");
            return;
        }
        if (!item.isEmpty()) {
            state.getAndUpdate(old -> old.withState(State.BODY_RECEIVED));
        }
        debugLog("ON NEXT WITH " + item.size() + " buffers");
        synchronized (bytes) {
            for (ByteBuffer buf : item) {
                try {
                    // DO NOT FLIP THE BUFFER HERE.  LOOKS LIKE YOU SHOULD, BUT NO.
                    // The JDK's HTTP client does *not* use Buffer.slice() to give
                    // you a view of just what you need - if you flip the first
                    // buffer, you get the headers and not your content.
                    byte[] all = new byte[buf.remaining()];
                    buf.get(all);
                    bytes.write(all);
                } catch (IOException ex) {
                    throw new Error(ex);
                }
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        debugLog(throwable.toString());
        HttpOperationState prevState = state.getAndUpdate(old -> old
                .withState(State.DONE)
                .withReason(CompletionReason.ERRORED)
        );
        if (!prevState.futureCompleted()) {
            completeFuture(ServiceResult.thrown(throwable));
        }
    }

    @Override
    public void onComplete() {
        debugLog("ON COMPLETE - info " + (info == null ? "--" : info.statusCode()));
        if (info != null) {
            if (info.statusCode() > 399) {
                String body = "";
                try {
                    Charset charset = info.headers().firstValue("content-type")
                            .map(ct -> {
                                Matcher m = CHARSET_PATTERN_IN_CONTENT_TYPE_HEADER.matcher(ct);
                                if (m.find()) {
                                    try {
                                        return Charset.forName(m.group(1));
                                    } catch (Exception ex) {
                                        Logger.getLogger(JacksonBodyHandler.class.getName()).log(Level.WARNING, "Bad charset " + m.group(1), ex);
                                        return UTF_8;
                                    }
                                }
                                return null;
                            }).orElse(UTF_8);

                    synchronized (bytes) {
                        body = new String(bytes.toByteArray(), charset);
                    }
                } finally {
                    completeFuture(ServiceResult.failed(info, body));
                    state.getAndUpdate(old -> old.withReason(CompletionReason.FAILED));
                }
            } else {
                T obj;
                try {
                    synchronized (bytes) {
                        if (bytes.size() > 0) {
                            obj = mapper.readValue(bytes.toByteArray(), type);
                        } else {
                            obj = null;
                        }
                    }
                    completeFuture(ServiceResult.success(info, obj));
                    state.getAndUpdate(old -> old.withState(State.DONE).withReason(CompletionReason.COMPLETED));
                } catch (IOException ex) {
                    String body;
                    synchronized (bytes) {
                        body = new String(bytes.toByteArray(), UTF_8);
                    }
                    completeFuture(ServiceResult.decodingError(body, ex));
                    state.getAndUpdate(old -> old.withState(State.DONE)
                            .withReason(CompletionReason.INVALID_RESPONSE));

                } catch (Throwable th) {
                    bodyFuture.completeExceptionally(th);
                    state.getAndUpdate(old -> old.withState(State.DONE).withReason(CompletionReason.ERRORED));
                    return;
                }
            }
        }
    }

    @Override
    public HttpResponse.BodySubscriber<ServiceResult<T>> apply(HttpResponse.ResponseInfo responseInfo) {
        if (checkTimeout()) {
            return this;
        }
        System.out.println("APPLY. " + responseInfo);
        synchronized (this) {
            info = responseInfo;
        }
        state.getAndUpdate(old -> {
            HttpOperationState result = old;
            if (old.state().ordinal() < State.HEADERS_RECEIVED.ordinal()) {
                result = old.withState(State.HEADERS_RECEIVED);
            }
            if (responseInfo.statusCode() > 399) {
                if (old.reason() == CompletionReason.NOT_COMPLETED) {
                    result = result.withReason(CompletionReason.FAILED);
                }
            }
            return result;
        });
        return this;
    }

}
