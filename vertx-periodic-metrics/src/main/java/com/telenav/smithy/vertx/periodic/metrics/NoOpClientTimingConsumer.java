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

import java.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
final class NoOpClientTimingConsumer implements ClientTimingConsumer {

    @Override
    public void onTiming(String metricNamespace, Duration age, Duration sinceSend, Object request) {
        // do nothing
    }

}
