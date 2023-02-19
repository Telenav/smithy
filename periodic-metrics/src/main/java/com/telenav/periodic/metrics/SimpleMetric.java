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
package com.telenav.periodic.metrics;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Simple implementation of metric which obeys the contract required by the api.
 *
 * @author Tim Boudreau
 */
public final class SimpleMetric implements Metric {

    private final String name;
    private final String loggingName;
    private final boolean omitIfZero;
    private final boolean omitIfNegative;

    public SimpleMetric(String name, boolean omitIfNegative, boolean omitIfZero) {
        this.name = notNull("name", name).trim();
        this.omitIfNegative = omitIfNegative;
        this.omitIfZero = omitIfZero;
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Empty metric name");
        }
        this.loggingName = name.toLowerCase().replace('_', '-');
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean omitIfNegative() {
        return omitIfNegative;
    }

    @Override
    public boolean omitIfZero() {
        return omitIfZero;
    }

    @Override
    public String loggingName() {
        return loggingName;
    }

    @Override
    public String toString() {
        return loggingName;
    }

    @Override
    public int hashCode() {
        return 71 * name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof Metric)) {
            return false;
        }
        Metric other = (Metric) o;
        return name().equals(other.name());
    }
}
