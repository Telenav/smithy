/*
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.smithy.blog.demo.vertx;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
public final class VertxJULLogging implements LogDelegateFactory {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LogDelegate createDelegate(String name) {
        return new LD(Logger.getLogger(name));
    }

    static class LD implements LogDelegate {

        private final Logger logger;

        public LD(Logger logger) {
            this.logger = logger;
//            logger.setLevel(Level.FINEST);
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isLoggable(Level.WARNING);
        }

        @Override
        public boolean isInfoEnabled() {
            return logger.isLoggable(Level.INFO);
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isLoggable(Level.FINE);
        }

        @Override
        public boolean isTraceEnabled() {
            return logger.isLoggable(Level.FINEST);
        }

        private void logIt(Level level, Object msg, Throwable t, Object... args) {
            level = Level.SEVERE;
            String message;
            if (t != null) {
                if (args != null && args.length > 0) {
                    message = MessageFormat.format(Objects.toString(msg), args);
                } else {
                    message = msg == null ? "" : msg.toString();
                }
                logger.log(level, message, t);
            } else {
                message = Objects.toString(msg);
                if (args != null && args.length > 0) {
                    logger.log(level, message, args);
                } else {
                    logger.log(level, message);
                }
            }
        }

        @Override
        public void fatal(Object message) {
            logIt(Level.SEVERE, message, null);
        }

        @Override
        public void fatal(Object message, Throwable t) {
            logIt(Level.SEVERE, message, t);
        }

        @Override
        public void error(Object message) {
            logIt(Level.SEVERE, message, null);
        }

        @Override
        public void error(Object message, Object... params) {
            logIt(Level.SEVERE, message, null, params);
        }

        @Override
        public void error(Object message, Throwable t) {
            logIt(Level.SEVERE, message, t);
        }

        @Override
        public void error(Object message, Throwable t, Object... params) {
            logIt(Level.SEVERE, message, t, params);
        }

        @Override
        public void warn(Object message) {
            logIt(Level.WARNING, message, null);
        }

        @Override
        public void warn(Object message, Object... params) {
            logIt(Level.WARNING, message, null, params);
        }

        @Override
        public void warn(Object message, Throwable t) {
            logIt(Level.WARNING, message, t);
        }

        @Override
        public void warn(Object message, Throwable t, Object... params) {
            logIt(Level.WARNING, message, t, params);
        }

        @Override
        public void info(Object message) {
            logIt(Level.INFO, message, null);
        }

        @Override
        public void info(Object message, Object... params) {
            logIt(Level.INFO, message, null, params);
        }

        @Override
        public void info(Object message, Throwable t) {
            logIt(Level.INFO, message, t);
        }

        @Override
        public void info(Object message, Throwable t, Object... params) {
            logIt(Level.INFO, message, t, params);
        }

        @Override
        public void debug(Object message) {
            logIt(Level.FINE, message, null);
        }

        @Override
        public void debug(Object message, Object... params) {
            logIt(Level.FINE, message, null, params);
        }

        @Override
        public void debug(Object message, Throwable t) {
            logIt(Level.FINE, message, t);
        }

        @Override
        public void debug(Object message, Throwable t, Object... params) {
            logIt(Level.FINE, message, t, params);
        }

        @Override
        public void trace(Object message) {
            logIt(Level.FINEST, message, null);
        }

        @Override
        public void trace(Object message, Object... params) {
            logIt(Level.FINEST, message, null, params);
        }

        @Override
        public void trace(Object message, Throwable t) {
            logIt(Level.FINEST, message, t);
        }

        @Override
        public void trace(Object message, Throwable t, Object... params) {
            logIt(Level.FINEST, message, t, params);
        }

    }
}
