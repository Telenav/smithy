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

import static com.mastfrog.util.collections.CollectionUtils.map;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class OperationStatsMetricTest {

    @Test
    public void testAddTime() {
        System.setProperty("unit.test", "false");
        OperationStatsMetric<Things> met = new OperationStatsMetric<>(Things.ONE, 400, null, PercentileMethod.INTERPOLATED);
        Set<String> found = new HashSet<>();
        // Ensure the metric logging names are what we expect
        for (Metric m : met.metrics()) {
            found.add(m.toString());
        }
        assertEquals(setOf("one.min", "one.max", "one.median", "one.requests",
                "one.p90", "one.mean", "one.p99", "one.p10"), found);

        // Now add some samples, just doing 1:1 counter values
        for (int i = 0; i < 200; i++) {
            met.add(i + 1);
        }

        // Collect our statistics, storing the logging name of each metric
        Map<String, Object> metrix = new LinkedHashMap<>();
        boolean anyFound = met.get((metric, val) -> {
            metrix.put(metric.toString(), val);
        });
        assertTrue(anyFound, "Should have found some metrics");
        assertFalse(metrix.isEmpty(), "Reported to emit metrics, but none are present");

        Map<String, Object> expected = map("one.min").to(1l)
                .map("one.max").to(200L)
                .map("one.median").to(99L)
                .map("one.p10").to(19L)
                .map("one.p99").to(199L)
                .map("one.mean").to(100L)
                .map("one.requests").to(200L)
                .map("one.p90").finallyTo(179L);

        // Make sure we got what we expect
        assertEquals(expected, metrix, "Emitted metrics do not match expected set");

        // Empty our map
        metrix.clear();
        anyFound = met.get((metric, val) -> {
            metrix.put(metric.toString(), val);
        });

        // Now cycle a few times without adding any data, to make sure we cannot
        // possibly retrieve stale samples
        assertFalse(anyFound, "After reset, no values should be found");
//        assertTrue(metrix.isEmpty(), "Got metrics but was told there were none");
        anyFound = met.get((metric, val) -> {
            metrix.put(metric.toString(), val);
        });
        assertFalse(anyFound, "After second reset, still no values should be found");
//        assertTrue(metrix.isEmpty(), "Got metrics but was told there were none");
        anyFound = met.get((metric, val) -> {
            metrix.put(metric.toString(), val);
        });
        assertFalse(anyFound, "After third reset, still no values should be found");
//        assertTrue(metrix.isEmpty(), "Got metrics but was told there were none");

        // Now add the same data again, and check that we get the same results
        for (int i = 0; i < 200; i++) {
            met.add(i + 1);
        }

        anyFound = met.get((metric, val) -> {
            metrix.put(metric.toString(), val);
        });
        assertTrue(anyFound, "Should have found metrix");
        assertFalse(metrix.isEmpty(), "Should have emitted some metrics");
        assertEquals(expected, metrix, "Emitted metrics do not match expected set");
    }

    enum Things {
        ONE
    }
}
