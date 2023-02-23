package com.telenav.smithy.vertx.bunyan.logging.vertxinternal;

import com.mastfrog.bunyan.java.v2.Logs;
import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Allows vertx's internal logging to use bunyan-java loggers.
 *
 * @author Tim Boudreau
 */
public final class VertxInternalLoggingDelegate implements LogDelegateFactory {

    private static boolean enabled;
    private final Map<String, Logs> delegates = new ConcurrentSkipListMap<>();

    /**
     * Enable internal logging. Must be called <i>extremely early in startup</i>
     * to be effective.
     */
    public static void enable() {
        enabled = true;
        System.setProperty("vertx.logger-delegate-factory-class-name", VertxInternalLoggingDelegate.class.getName());
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public LogDelegate createDelegate(String name) {
        String nm = "vx:" + name;
        Logs logs = delegates.computeIfAbsent(nm, n -> Logs.named(n));
        return new Del(logs);
    }

    private static final class Del implements LogDelegate {

        private final Logs logs;

        public Del(Logs logs) {
            this.logs = logs;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public void fatal(Object message) {
            logs.fatal(Objects.toString(message)).close();
        }

        @Override
        public void fatal(Object message, Throwable t) {
            logs.fatal(Objects.toString(message)).add(t).close();
        }

        @Override
        public void error(Object message) {
            logs.error(Objects.toString(message)).close();
        }

        @Override
        public void error(Object message, Object... params) {
            logs.error(Objects.toString(message)).add("p", params).close();
        }

        @Override
        public void error(Object message, Throwable t) {
            logs.error(Objects.toString(message)).add(t).close();
        }

        @Override
        public void error(Object message, Throwable t, Object... params) {
            logs.error(Objects.toString(message)).add("p", params).add(t).close();
        }

        @Override
        public void warn(Object message) {
            logs.warn(Objects.toString(message)).close();
        }

        @Override
        public void warn(Object message, Object... params) {
            logs.warn(Objects.toString(message)).add("p", params).close();
        }

        @Override
        public void warn(Object message, Throwable t) {
            logs.warn(Objects.toString(message)).add(t).close();
        }

        @Override
        public void warn(Object message, Throwable t, Object... params) {
            logs.warn(Objects.toString(message)).add("p", params).add(t).close();
        }

        @Override
        public void info(Object message) {
            logs.info(Objects.toString(message)).close();
        }

        @Override
        public void info(Object message, Object... params) {
            logs.info(Objects.toString(message)).add("p", params).close();
        }

        @Override
        public void info(Object message, Throwable t) {
            logs.info(Objects.toString(message)).add(t).close();
        }

        @Override
        public void info(Object message, Throwable t, Object... params) {
            logs.info(Objects.toString(message)).add("p", params).add(t).close();
        }

        @Override
        public void debug(Object message) {
            logs.debug(Objects.toString(message)).close();
        }

        @Override
        public void debug(Object message, Object... params) {
            logs.debug(Objects.toString(message)).add("p", params).close();
        }

        @Override
        public void debug(Object message, Throwable t) {
            logs.debug(Objects.toString(message)).add(t).close();
        }

        @Override
        public void debug(Object message, Throwable t, Object... params) {
            logs.debug(Objects.toString(message)).add("p", params).add(t).close();
        }

        @Override
        public void trace(Object message) {
            logs.trace(Objects.toString(message)).close();
        }

        @Override
        public void trace(Object message, Object... params) {
            logs.trace(Objects.toString(message)).add("p", params).close();
        }

        @Override
        public void trace(Object message, Throwable t) {
            logs.trace(Objects.toString(message)).add(t).close();
        }

        @Override
        public void trace(Object message, Throwable t, Object... params) {
            logs.trace(Objects.toString(message)).add("p", params).add(t).close();
        }
    }
}
