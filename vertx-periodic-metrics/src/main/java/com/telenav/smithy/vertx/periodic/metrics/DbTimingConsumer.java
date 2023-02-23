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
package com.telenav.smithy.vertx.periodic.metrics;

import com.mastfrog.settings.Settings;
import com.telenav.periodic.metrics.MetricsRegistry;
import com.telenav.periodic.metrics.MultiMetric;
import com.telenav.periodic.metrics.OperationStatsMetric;
import static com.telenav.periodic.metrics.PercentileMethod.INTERPOLATED;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class DbTimingConsumer extends MetricsRegistry implements ClientTimingConsumer {

    private final SimpleOperationMetrics mx;
    private final List<OperationStatsMetric<Timing>> all = new ArrayList<>();
    public static final String SETTINGS_KEY_DB_NAMESPACE = "db.metrics.namespace";
    private final String dbNamespace;

    @Inject
    DbTimingConsumer(MetricsRegistrar registrar, SimpleOperationMetrics mx, Settings settings) {
        super(registrar);
        this.mx = mx;
        dbNamespace = settings.getString(SETTINGS_KEY_DB_NAMESPACE, "db");
    }

    @Override
    public Collection<? extends MultiMetric<Long>> multiMetrics(Duration samplingInterval) {
        Set<OperationStatsMetric<Timing>> result = OperationStatsMetric.forEnum(Timing.class, 7000, INTERPOLATED);
        all.addAll(result);
        return result;
    }

    @Override
    public void onTiming(String metricNamespace, Duration age, Duration sinceSend, Object request) {
        // Here an implementation *could* split these out by query - the request for postgres
        // will simply be a string
        if (dbNamespace.equals(metricNamespace)) {
            for (OperationStatsMetric<Timing> m : all) {
                m.addTime(age);
            }
        }
    }

    enum Timing {
        DB
    }
}
