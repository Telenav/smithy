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

import java.util.Optional;

/**
 * A metric which aggregates a member of a generated operation enum indicating a
 * single HTTP operation, and a built-in metric such as MetricKind.HTTP_REQUESTS
 * or a statistical metric.
 *
 * @author Tim Boudreau
 */
public final class OperationMetric<Op extends Enum<Op>, M extends Enum<M> & Metric> implements Metric {

    private final Op operation;
    private final M metric;
    private final String name;

    OperationMetric(Op operation, M metric) {
        this.operation = operation;
        this.metric = metric;
        name = operation.name() + "." + metric.name();
    }

    /**
     * If this is a percentile metric, returns the percentile as a double
     * between 0 and 1.
     *
     * @return The percentile, if applicable
     */
    public Optional<Double> percentile() {
        if (metric instanceof StatisticalMetrics statisticalMetrics) {
            return statisticalMetrics.percentile();
        }
        return Optional.empty();
    }

    public Op operation() {
        return operation;
    }

    public M metric() {
        return metric;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean omitIfNegative() {
        return metric.omitIfNegative();
    }

    @Override
    public boolean omitIfZero() {
        return metric.omitIfZero();
    }

    @Override
    public boolean isOnDemand() {
        return false;
    }

    @Override
    public String toString() {
        return loggingName();
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
        } else if (o instanceof OperationMetric om) {
            return om.metric == metric && om.operation == operation;
        }
        Metric other = (Metric) o;
        return name().equals(other.name());
    }
}
