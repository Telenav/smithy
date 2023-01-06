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
package com.telenav.smithy.blog.demo.vertx;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;
import java.text.MessageFormat;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public class VertxLogging implements LogDelegateFactory {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LogDelegate createDelegate(String name) {
        System.out.println("create logger " + name);
        Lev target = name.contains("RoutingContext") ? Lev.TRACE : Lev.WARN;
        return new LE(target, name);
    }

    enum Lev {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL;

        boolean isEnabledIn(Lev other) {
            return ordinal() >= other.ordinal();
        }
    }

    static final class LE implements LogDelegate {

        private final Lev enabled;
        private final String name;
        static final Object[] NONE = new Object[0];

        LE(Lev enabled, String name) {
            int ix = name.lastIndexOf('.');
            if (ix >= 0) {
                name = name.substring(ix + 1);
            }
            this.enabled = enabled;
            this.name = name;
        }

        void out(Lev level, Object what, Throwable t) {
            out(level, what, t, NONE);
        }

        void out(Lev level, Object what) {
            out(level, what, null, NONE);
        }

        void out(Lev level, Object what, Object... params) {
            out(level, what, null, params);
        }

        void out(Lev level, Object what, Throwable t, Object... params) {
            if (enabled.ordinal() <= level.ordinal()) {
                if (params != null && params.length > 0) {
                    what = MessageFormat.format(Objects.toString(what), params);
                }
                System.out.println(level + "\t" + name + ":\t" + Objects.toString(what));
                if (t != null) {
                    t.printStackTrace(System.out);
                }
            }
        }

        @Override
        public boolean isWarnEnabled() {
            return Lev.WARN.isEnabledIn(enabled);
        }

        @Override
        public boolean isInfoEnabled() {
            return Lev.INFO.isEnabledIn(enabled);
        }

        @Override
        public boolean isDebugEnabled() {
            return Lev.DEBUG.isEnabledIn(enabled);
        }

        @Override
        public boolean isTraceEnabled() {
            return Lev.TRACE.isEnabledIn(enabled);
        }

        @Override
        public void fatal(Object message) {
            out(Lev.FATAL, message);
        }

        @Override
        public void fatal(Object message, Throwable t) {
            out(Lev.FATAL, message, t);
        }

        @Override
        public void error(Object message) {
            out(Lev.ERROR, message);
        }

        @Override
        public void error(Object message, Object... params) {
            out(Lev.ERROR, message, params);
        }

        @Override
        public void error(Object message, Throwable t) {
            out(Lev.ERROR, message, t);
        }

        @Override
        public void error(Object message, Throwable t, Object... params) {
            out(Lev.ERROR, message, t, params);
        }

        @Override
        public void warn(Object message) {
            out(Lev.WARN, message);
        }

        @Override
        public void warn(Object message, Object... params) {
            out(Lev.WARN, message, params);
        }

        @Override
        public void warn(Object message, Throwable t) {
            out(Lev.WARN, message, t);
        }

        @Override
        public void warn(Object message, Throwable t, Object... params) {
            out(Lev.WARN, message, t, params);
        }

        @Override
        public void info(Object message) {
            out(Lev.INFO, message);
        }

        @Override
        public void info(Object message, Object... params) {
            out(Lev.INFO, message, params);
        }

        @Override
        public void info(Object message, Throwable t) {
            out(Lev.INFO, message, t);
        }

        @Override
        public void info(Object message, Throwable t, Object... params) {
            out(Lev.INFO, message, t, params);
        }

        @Override
        public void debug(Object message) {
            out(Lev.DEBUG, message);
        }

        @Override
        public void debug(Object message, Object... params) {
            out(Lev.DEBUG, message, params);
        }

        @Override
        public void debug(Object message, Throwable t) {
            out(Lev.DEBUG, message, t);
        }

        @Override
        public void debug(Object message, Throwable t, Object... params) {
            out(Lev.DEBUG, message, t, params);
        }

        @Override
        public void trace(Object message) {
            out(Lev.TRACE, message);
        }

        @Override
        public void trace(Object message, Object... params) {
            out(Lev.TRACE, message, params);
        }

        @Override
        public void trace(Object message, Throwable t) {
            out(Lev.TRACE, message, t);
        }

        @Override
        public void trace(Object message, Throwable t, Object... params) {
            out(Lev.TRACE, message, t, params);
        }
    }
}
