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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.mastfrog.giulius.annotations.Setting;
import static com.mastfrog.giulius.annotations.Setting.Tier.PRIMARY;
import com.mastfrog.giulius.bunyan.java.v2.LoggingModule;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import static java.util.Arrays.asList;
import java.util.HashSet;
import java.util.Set;

/**
 * Configures bunyan-java logging for an application which uses an enum to
 * identify its operations.
 *
 * @author Tim Boudreau
 */
public final class Logging<Op extends Enum<Op>> implements Module {

    @Setting(value = "Prefix for the log file name (if unset, will be derived from the operation enum class name)")
    public static final String SETTINGS_KEY_LOG_FILE_PREFIX = "log.file.prefix";
    @Setting(value = "The directory in which log files should be placed.", tier = PRIMARY, defaultValue = "./", shortcut = 'd')
    public static final String SETTINGS_KEY_LOG_DIR = "log.dir";
    private final Set<String> loggers = new HashSet<>(Arrays.asList("ops", "metrics", "startup"));
    private final Class<Op> opType;
    private final Settings settings;

    public Logging(Settings settings, Class<Op> opType, String... loggersToBind) {
        this.opType = opType;
        this.settings = settings;
        loggers.addAll(asList(loggersToBind));
    }

    public Logging bindLogger(String logger, String... more) {
        loggers.add(logger);
        loggers.addAll(asList(more));
        return this;
    }

    private String logFilePrefix() {
        return settings.getString(SETTINGS_KEY_LOG_FILE_PREFIX, defaultLogFilePrefix());
    }

    private String defaultLogFilePrefix() {
        String nm = opType.getSimpleName();
        if (nm.endsWith("Operations")) {
            nm = nm.substring(0, nm.length() - "Operations".length());
        }
        return Strings.camelCaseToDashes(nm).toLowerCase() + "-";
    }

    private String timestampedLogFileName() {
        ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Z"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS");
        return logFilePrefix() + fmt.format(now) + ".log";
    }

    private void configureLogFile(Binder binder, LoggingModule m) {
        m.configuringLoggingWith(config -> {
            try {
                if (!Boolean.getBoolean("unit.test")) {
                    String logFileName = timestampedLogFileName();
                    String logDir = settings.getString(SETTINGS_KEY_LOG_DIR, ".");
                    if (!".".equals(logDir)) {
                        Path dir = Paths.get(logDir);
                        if (!Files.exists(dir)) {
                            Files.createDirectories(dir);
                        }
                    }
                    Path logFile = Paths.get(logDir).toRealPath().resolve(logFileName);
                    System.out.println("Logging to " + logFile);
                    config.logToFile(logFile);
                    if (settings.getBoolean("log.console", false)) {
                        System.out.println("LOG CONSOLE TRUE");
                        config.logToConsole();
                    }
                } else {
                    config.logToConsole();
                }
            } catch (IOException ioe) {
                throw new ConfigurationError("Error configuring logging", ioe);
            }
        });
    }

    @Override
    public void configure(Binder binder) {
        LoggingModule lm = new LoggingModule(true);
        for (String lg : loggers) {
            lm.bindLogger(lg);
        }
        configureLogFile(binder, lm);
        binder.install(lm);
    }
}
