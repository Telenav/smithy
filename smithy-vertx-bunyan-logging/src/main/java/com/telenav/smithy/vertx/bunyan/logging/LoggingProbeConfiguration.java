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

import com.google.inject.ImplementedBy;
import com.mastfrog.bunyan.java.v2.Log;
import com.mastfrog.giulius.help.GiuliusHelp;
import com.mastfrog.giulius.help.Tier;
import static com.telenav.smithy.vertx.bunyan.logging.AbstractLoggingProbe.PW_PATTERN;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Configuration for the probe which is notified of events in the application
 * and logs and emits metrics into the periodic metrics sink.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultLoggingProbeConfiguration.class)
public interface LoggingProbeConfiguration {

    /**
     * Obfuscate strings which should not be logged in the clear in the startup
     * configuration logging. The default implementation obfuscates passwords in
     * values that look like URL, and any value where the key name
     * case-insensitively contains the string "password".
     *
     * @param settingsKey A key
     * @param settingsValue The value for that key
     * @return An obfuscated string if obfuscating was needed
     */
    default String obfuscate(String settingsKey, String settingsValue) {
        if (settingsValue == null) {
            return "-null-";
        }
        if (settingsValue.contains("://")) {
            Matcher m = PW_PATTERN.matcher(settingsValue);
            if (m.find()) {
                return m.group(1) + "://" + m.group(2) + ":****@" + m.group(4);
            }
        }
        if (settingsKey.toLowerCase().contains("password")) {
            return "****";
        }
        return settingsValue;
    }

    /**
     * Get the set of configuration keys that should be logged on startup to
     * ensure an operator has sufficient information about the configuration of
     * the process to diagnose any configuration problems.
     *
     * @return An array of strings
     */
    default String[] configurationKeysToLog() {
        List<String> result = new ArrayList<>(64);
        GiuliusHelp.visitSettings(rec -> {
            if (rec.tier() == Tier.PRIMARY) {
                result.add(rec.key());
            }
        });
        return result.toArray(String[]::new);
    }

    default void onStartupLog(Log logRecord) {

    }

    /**
     * Determine if a thrown exception should be logged, and how.
     *
     * @param thrown A thrown exception
     * @return True if it should be logged
     */
    default Loggability loggability(Throwable thrown) {
        return Loggability.ERROR;
    }
}
