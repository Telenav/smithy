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
package com.telenav.smithy.client.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.function.misc.QuietAutoClosable;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.jackson.configuration.DurationSerializationMode;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import com.mastfrog.jackson.configuration.TimeSerializationMode;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.System.getenv;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.READ;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * General configuration for all clients generated from this library.
 *
 * @author Tim Boudreau
 */
public final class ClientConfig {

    private static final boolean DEBUG_LOG = Boolean.getBoolean("client.debug");
    static final long STARTUP = System.currentTimeMillis();
    private static final String ENV_VAR_CONFIG = "SMITHY_CLIENT_CONFIG";
    private static final String SYSPROP_CONFIG_LOCATION = "smithy.config.location";
    private static final Path DEFAULT_CONFIG_RELATIVE_PATH
            = System.getProperty("os.name", "").contains("Mac")
            ? Paths.get("Library/Application Support/SmithyClient/client.properties")
            : Paths.get(".config/SmithyClient/client.properties");
    private static final ClientConfig INSTANCE = new ClientConfig();
    private static final ThreadLocal<ServiceClientConfig> caller = new ThreadLocal<>();
    private final Exe threadPool = new Exe();
    private final HttpClient client;
    private final Map<String, String> metadata;
    private final ObjectMapper mapper;

    private ClientConfig() {
        metadata = readProperties();
        mapper = createMapper();
        client = HttpClient.newBuilder().executor(threadPool)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20)) // XXX should be configurable
                .version(HttpClient.Version.HTTP_1_1)
                .executor(threadPool)
                .build();
    }
    
    static void debugLog(String what) {
        if (DEBUG_LOG) {
            System.err.println(what);
        }
    }

    static void debugLog(Supplier<String> what) {
        if (DEBUG_LOG) {
            System.err.println(what.get());
        }
    }

    static void debugLog(String what, Object... stuff) {
        if (DEBUG_LOG) {
            System.err.println(MessageFormat.format(what, stuff));
        }
    }

    public final Optional<String> metadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    private static ObjectMapper createMapper() {
        // This gets us ISO 8601 formats for java.time time classes and durations,
        // and serialization of Optionals
        return JacksonConfigurer.apply(new ObjectMapper(),
                JacksonConfigurer.javaTimeConfigurer(
                        TimeSerializationMode.TIME_AS_ISO_STRING,
                        DurationSerializationMode.DURATION_AS_ISO_STRING),
                JacksonConfigurer.localeConfigurer(),
                JacksonConfigurer.optionalSerializer());
    }

    public static int currentSecond() {
        long elapsedSeconds = ((System.currentTimeMillis() - STARTUP) / 1000L) % (long) Integer.MAX_VALUE;
        return (int) elapsedSeconds;
    }

    Exe executor() {
        return threadPool;
    }

    ObjectMapper mapper() {
        return mapper.copy();
    }

    HttpClient client() {
        return client;
    }

    private Map<String, String> readProperties() {
        Properties result = new Properties();
        String path = getenv(ENV_VAR_CONFIG);
        if (path == null) {
            path = System.getProperty(SYSPROP_CONFIG_LOCATION);
        }
        Path file;
        if (path == null) {
            file = Paths.get(System.getProperty("user.home")).resolve(DEFAULT_CONFIG_RELATIVE_PATH);
        } else {
            file = Paths.get(path);
        }
        if (Files.exists(file) && !Files.isDirectory(file) && Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file, READ)) {
                result.load(in);
            } catch (IOException ex) {
                Logger.getLogger(ClientConfig.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String k : result.stringPropertyNames()) {
            map.put(k, result.getProperty(k));
        }
        return map;
    }

    public static ClientConfig get() {
        return INSTANCE;
    }

    public ServiceClientConfig get(String apiName, String defaultEndpoint, String version) {
        Map<String, String> md = new HashMap<>();
        String prefix = apiName.toLowerCase() + ".";
        metadata.forEach((k, v) -> {
            if (k.startsWith(prefix) && k.length() > prefix.length()) {
                String name = k.substring(prefix.length());
                md.put(name, v);
            }
        });
        return new ServiceClientConfig(apiName, this, md, defaultEndpoint, version);
    }

    Exe threadPool() {
        return threadPool;
    }

    static QuietAutoClosable inContext(ServiceClientConfig cfig) {
        ServiceClientConfig old = caller.get();
        caller.set(cfig);
        return () -> caller.set(old);
    }

    <T> T withClient(ServiceClientConfig config, ThrowingFunction<HttpClient, T> supp) throws Exception {
        try (QuietAutoClosable ac = inContext(config)) {
            return supp.apply(client);
        }
    }

    static ServiceClientConfig context() {
        return caller.get();
    }

    private static Runnable wrap(Runnable run) {
        ServiceClientConfig config = context();
        return () -> {
            String oldThreadName = Thread.currentThread().getName();
            if (config != null) {
                Thread.currentThread().setName(config.serviceName()
                        + "-" + config.serviceVersion());
            }
            try (QuietAutoClosable ac = inContext(config)) {
                run.run();
            } finally {
                if (config != null) {
                    Thread.currentThread().setName(oldThreadName);
                }
            }
        };
    }

    private static <T> Callable<T> wrap(Callable<T> run) {
        ServiceClientConfig config = context();
        return () -> {
            String oldThreadName = Thread.currentThread().getName();
            if (config != null) {
                Thread.currentThread().setName(config.serviceName()
                        + "-" + config.serviceVersion());
            }
            try (QuietAutoClosable ac = inContext(config)) {
                return run.call();
            } finally {
                if (config != null) {
                    Thread.currentThread().setName(oldThreadName);
                }
            }
        };
    }

    /**
     * A thread pool executor which will ensure the originating config is
     * available.
     */
    static final class Exe extends ThreadPoolExecutor {

        public Exe() {
            // Pending - make configurable
            super(8, 128, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(),
                    new TF(), new Rej());
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return super.submit(wrap(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return super.submit(wrap(task), result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return super.submit(wrap(task));
        }

    }

    private static class TF implements ThreadFactory {

        private volatile int count;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new ClientThread("smithy-http-client-" + count++, r);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) -> {
                System.out.println(th.getName() + " " + ex);
                ex.printStackTrace();
            });
            debugLog(() -> "Create a new thread " + count);
            return t;
        }
    }

    static boolean isClientThread() {
        return Thread.currentThread().getClass() == ClientThread.class;
    }

    private static class ClientThread extends Thread {

        ClientThread(String name, Runnable r) {
            super(r, name);
        }
    }

    private static class Rej implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            r.run();
        }

    }

}
