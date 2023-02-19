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

/**
 * On-demand metric used for dummy metrics which have no value (e.g. an attempt
 * to collect free disk space that cannot be reported,like Mac OS's private temp
 * dir). Used when metrics collection cannot be initialized.
 *
 * @param <N>
 */
final class FixedOnDemandMetric<N extends Number> implements OnDemandMetric<N> {

    private final N value;
    private final Metric kind;
    private final boolean valid;

    FixedOnDemandMetric(N value, Metric kind) {
        this(value, kind, true);
    }

    FixedOnDemandMetric(N value, Metric kind, boolean valid) {
        this.value = value;
        this.kind = kind;
        this.valid = valid;
    }

    @Override
    public Metric kind() {
        return kind;
    }

    @Override
    public N get() {
        return value;
    }

    @Override
    public boolean isValid() {
        return valid;
    }
}
