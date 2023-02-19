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

import com.mastfrog.util.preconditions.Checks;
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static java.lang.Integer.parseInt;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import static java.time.temporal.ChronoUnit.YEARS;
import static java.util.Arrays.asList;

/**
 * Specifies a chronological unit and a count, used as the period of a
 * ClockInterval.
 */
class IntervalSpec {

    private final ChronoUnit unit;
    private final int amount;

    public IntervalSpec(ChronoUnit unit, int amount) {
        this.unit = Checks.notNull("unit", unit);
        this.amount = Checks.greaterThanZero("amount", amount);
    }

    public Duration duration() {
        Duration unitDuration = durationOf(unit);
        if (amount > 1) {
            return Duration.ofMillis(unitDuration.toMillis() * amount);
        }
        return unitDuration;
    }

    static Duration durationOf(ChronoUnit u) {
        switch (u) {
            case YEARS:
                return Duration.ofDays(365);
            default:
                return u.getDuration();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || o.getClass() != IntervalSpec.class) {
            return false;
        }
        IntervalSpec iv = (IntervalSpec) o;
        return unit == iv.unit && amount == iv.amount;
    }

    @Override
    public int hashCode() {
        return 71 * (unit.ordinal() + 1) * (amount * 59 + 1);
    }

    /**
     * Parse a spec from a string in the format "unit:N" where unit is one of
     * the (upper or lower case) names of a constant on {@link ChronoUnit}.
     *
     * @param def A string
     */
    public IntervalSpec(String def) {
        def = Checks.notNull("def", def).trim();
        int ix = def.lastIndexOf(':');
        String unitName;
        int count;
        if (ix < 0) {
            unitName = def;
            count = 1;
        } else {
            unitName = def.substring(0, ix);
            String countString = def.substring(ix + 1, def.length());
            if (countString.length() == 0) {
                count = 1;
            } else {
                try {
                    count = greaterThanZero("count", parseInt(countString));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Could not parse "
                            + "interval count from '" + def + "'");
                }
            }
        }
        ChronoUnit unitValue = null;
        for (ChronoUnit u : ChronoUnit.values()) {
            if (unitName.equalsIgnoreCase(u.name())) {
                unitValue = u;
                break;
            }
        }
        if (unitValue == null) {
            throw new IllegalArgumentException("'" + unitName + "' is not one of "
                    + asList(ChronoUnit.values()));
        }
        unit = unitValue;
        amount = count;
    }

    public ChronoUnit unit() {
        return unit;
    }

    public int amount() {
        return amount;
    }

    @Override
    public String toString() {
        return unit.name().toLowerCase() + ":" + amount;
    }

}
