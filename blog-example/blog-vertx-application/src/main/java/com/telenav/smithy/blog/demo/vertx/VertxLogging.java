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
