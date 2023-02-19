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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Metrics re garbage collection, obtained from mbeans in the running JVM -
 * garbage collection frequence is important for detecting high-GC which
 * indicates potential poor system health. GC metrics differ from others in that
 * the source metrics are cumulative over the life of the JVM; metrics instances
 * here will emit deltas since the last time they were polled.
 *
 * @author Tim Boudreau
 */
final class GCMetrics {

    static List<OnDemandMetric<Long>> defaultGcMetrics() {
        List<OnDemandMetric<Long>> result = new ArrayList<>();
        boolean hasZGC = false;
        // G1 garbage collector beans will exist even with -XX:+UseZGC, but will
        // always return 0, so preemptively figure out if we should ignore them, so
        // they don't clutter our metrics
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (g.getName().contains("ZGC")) {
                hasZGC = true;
                break;
            }
        }
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            BuiltInMetrics timeKind = null;
            BuiltInMetrics countKind = null;
            if (g.getName().contains("Young") && !hasZGC) {
                timeKind = BuiltInMetrics.G1_YOUNG_GENERATION_TIME;
                countKind = BuiltInMetrics.G1_YOUNG_GENERATION_GC_COUNT;
            } else if (g.getName().contains("Old") && !hasZGC) {
                timeKind = BuiltInMetrics.G1_OLD_GENERATION_TIME;
                countKind = BuiltInMetrics.G1_OLD_GENERATION_GC_COUNT;
            } else if (g.getName().contains("ZGC Cycles")) {
                countKind = BuiltInMetrics.ZGC_CYCLES;
            } else if (g.getName().contains("ZGC Pauses")) {
                timeKind = BuiltInMetrics.ZGC_TIME;
                countKind = BuiltInMetrics.ZGC_PAUSES;
            }
            if (timeKind != null) {
                result.add(new GarbageCollectorMetric(g, GarbageCollectorMXBean::getCollectionTime, timeKind));
            }
            if (countKind != null) {
                result.add(new GarbageCollectorMetric(g, GarbageCollectorMXBean::getCollectionCount, countKind));
            }
        }
        return result;
    }

    static class GarbageCollectorMetric implements OnDemandMetric<Long> {

        private final GarbageCollectorMXBean bean;
        private final Function<GarbageCollectorMXBean, Long> getter;
        private final BuiltInMetrics kind;
        private final AtomicLong lastValue = new AtomicLong();

        public GarbageCollectorMetric(GarbageCollectorMXBean bean, Function<GarbageCollectorMXBean, Long> getter, BuiltInMetrics kind) {
            this.bean = bean;
            this.getter = getter;
            this.kind = kind;
        }

        @Override
        public BuiltInMetrics kind() {
            return kind;
        }

        @Override
        public Long get() {
            long newValue = getter.apply(bean);
            long oldValue = lastValue.getAndSet(newValue);
            return newValue - oldValue;
        }

    }

}
