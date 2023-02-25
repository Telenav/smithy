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
package com.telenav.smithy.vertx.bunyan.logging;

import com.mastfrog.bunyan.java.v2.Level;
import com.mastfrog.bunyan.java.v2.Log;
import com.mastfrog.bunyan.java.v2.Logs;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Given a Throwable, a logging and detail level to use when logging it. Since
 * stack traces are large and of limited utility in an async server, lower
 * levels allow for with-and-without stack trace levels of detail.
 * <p>
 * Lower levels than error and fatal also have variants that will unwrap the
 * cause of an exception if present and ignore the outer wrapper exception,
 * which is often used to wrap a checked exception in an unchecked exception.
 * </p>
 *
 * @author Tim Boudreau
 */
public enum Loggability {
    FATAL(Level.FATAL, true),
    ERROR(Level.ERROR, true),
    WARN(Level.WARN, true),
    WARN_NOSTACK(Level.WARN, false),
    WARN_UNWRAP(Level.WARN, true, true),
    WARN_UNWRAP_NOSTACK(Level.WARN, false, true),
    INFO(Level.INFO, true),
    INFO_NOSTACK(Level.INFO, false),
    INFO_UNWRAP(Level.INFO, true, true),
    INFO_UNWRAP_NOSTACK(Level.INFO, false, true),
    DEBUG(Level.DEBUG, true),
    DEBUG_NOSTACK(Level.DEBUG, false),
    DEBUG_UNWRAP(Level.DEBUG, true, true),
    DEBUG_UNWRAP_NOSTACK(Level.DEBUG, false, true),
    TRACE(Level.TRACE, true),
    TRACE_NOSTACK(Level.TRACE, false),
    TRACE_UNWRAP(Level.TRACE, true, true),
    TRACE_UNWRAP_NOSTACK(Level.TRACE, false, true),
    NONE;
    private final boolean includeStack;
    private final Optional<Level> level;
    private final boolean unwrapCause;

    Loggability(Level level, boolean includeStack) {
        this(level, includeStack, false);
    }

    Loggability(Level level, boolean includeStack, boolean unwrapCause) {
        this.level = Optional.ofNullable(level);
        this.includeStack = includeStack;
        this.unwrapCause = unwrapCause;
    }

    Loggability() {
        this(null, false);
    }

    public boolean unwrapCause() {
        return unwrapCause;
    }

    public boolean isLogged() {
        return level.isPresent();
    }

    public void withLevel(Consumer<Level> lev) {
        level.ifPresent(lev);
    }

    public boolean isStack() {
        return includeStack;
    }

    /**
     * Performs reasonable default logging.
     *
     * @param msg The log message
     * @param logs The log factory
     * @param thrown The thrown exception
     * @param logConsumer An optional consumer which can add other detail to the
     * log record
     */
    public void apply(String msg, Logs logs, Throwable thrown, Consumer<Log> logConsumer) {
        if (unwrapCause() && thrown.getCause() != null) {
            apply(msg, logs, thrown.getCause(), logConsumer);
            return;
        }
        withLevel(lev -> {
            logs.log(msg, lev, log -> {
                if (includeStack) {
                    log.add(thrown);
                } else {
                    log.add("error", thrown.getMessage());
                    log.add("type", thrown.getClass().getSimpleName());
                    Throwable cause = thrown.getCause();
                    int ct = 0;
                    while (cause != null) {
                        String nm = ct++ == 0 ? "cause" : "cause-" + ct;
                        log.add(nm, cause.getMessage());
                        log.add(nm + ".type", cause.getClass().getSimpleName());
                        cause = cause.getCause();
                    }
                    Throwable[] supp = thrown.getSuppressed();
                    if (supp != null && supp.length > 0) {
                        for (int i = 0; i < supp.length; i++) {
                            String nm = "suppressed-" + (i + 1);
                            log.add(nm, supp[i].getMessage());
                            log.add(nm + ".type", supp[i].getClass().getSimpleName());
                        }
                    }
                }
                if (logConsumer != null) {
                    logConsumer.accept(log);
                }
            });
        });
    }
}
