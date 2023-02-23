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

import static java.util.Collections.unmodifiableSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/**
 * Kinds of built in metrics we collect and log.
 *
 * @author Tim Boudreau
 */
public enum BuiltInMetrics implements Metric {
    REQUESTS,
    HTTP_BYTES_READ,
    HTTP_BYTES_WRITTEN,
    EXCEPTION_OCCURRED,
    HTTP_RESPONSES_INITIATED,
    HTTP_RESPONSES_COMPLETED,
    HTTP_REQUEST_RESET,
    WORKING_DIR_DISK_SPACE,
    TEMP_DISK_SPACE,
    AVAILABLE_MEMORY,
    TOTAL_MEMORY,
    LIVE_THREADS,
    DATAGRAM_BYTES_WRITTEN,
    DATAGRAM_BYTES_READ,
    NET_CONNECTS,
    NET_DISCONNECTS,
    NET_BYTES_READ,
    NET_BYTES_WRITTEN,
    DB_REQUESTS,
    DB_RESPONSES,
    DB_RESETS,
    DB_TIME,
    NET_CLIENT_REQUESTS,
    NET_CLIENT_RESPONSES,
    NET_CLIENT_RESETS,
    NET_CLIENT_BYTES_READ,
    NET_CLIENT_BYTES_WRITTEN,
    G1_YOUNG_GENERATION_TIME,
    G1_OLD_GENERATION_TIME,
    G1_YOUNG_GENERATION_GC_COUNT,
    G1_OLD_GENERATION_GC_COUNT,
    ZGC_TIME,
    ZGC_PAUSES,
    ZGC_CYCLES,
    CLASSES,
    NON_HEAP_TOTAL_MEMORY,
    NON_HEAP_USED_MEMORY,
    HTTP_NON_ERROR_RESPONSES,
    HTTP_CLIENT_ERROR_RESPONSES,
    HTTP_SERVER_ERROR_RESPONSES,
    HEAP_IN_USE;

    public static final Set<BuiltInMetrics> INCREMENTAL;

    static {
        Set<BuiltInMetrics> result = EnumSet.noneOf(BuiltInMetrics.class);
        for (BuiltInMetrics k : values()) {
            if (!k.isOnDemand()) {
                result.add(k);
            }
        }
        INCREMENTAL = unmodifiableSet(result);
    }

    @Override
    public boolean isOnDemand() {
        switch (this) {
            case AVAILABLE_MEMORY:
            case WORKING_DIR_DISK_SPACE:
            case TOTAL_MEMORY:
            case TEMP_DISK_SPACE:
            case LIVE_THREADS:
            case G1_OLD_GENERATION_GC_COUNT:
            case G1_YOUNG_GENERATION_GC_COUNT:
            case G1_OLD_GENERATION_TIME:
            case G1_YOUNG_GENERATION_TIME:
            case ZGC_CYCLES:
            case ZGC_PAUSES:
            case ZGC_TIME:
            case CLASSES:
            case NON_HEAP_TOTAL_MEMORY:
            case NON_HEAP_USED_MEMORY:
            case HEAP_IN_USE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean omitIfZero() {
        switch (this) {
            case REQUESTS:
            case HTTP_RESPONSES_INITIATED:
            case G1_YOUNG_GENERATION_GC_COUNT:
            case G1_YOUNG_GENERATION_TIME:
            case G1_OLD_GENERATION_GC_COUNT:
            case G1_OLD_GENERATION_TIME:
            case ZGC_CYCLES:
            case ZGC_PAUSES:
            case ZGC_TIME:
            case EXCEPTION_OCCURRED:
            case HTTP_BYTES_READ:
            case HTTP_BYTES_WRITTEN:
            case HTTP_REQUEST_RESET:
            case HTTP_NON_ERROR_RESPONSES:
            case HTTP_SERVER_ERROR_RESPONSES:
            case HTTP_RESPONSES_COMPLETED:
            case HTTP_CLIENT_ERROR_RESPONSES:
                return false;
            default:
                return true;
        }
    }

    static EnumMap<BuiltInMetrics, LongAdder> newMap() {
        EnumMap<BuiltInMetrics, LongAdder> metrics = new EnumMap<>(BuiltInMetrics.class);
        for (BuiltInMetrics mk : BuiltInMetrics.values()) {
            metrics.put(mk, new LongAdder());
        }
        return metrics;
    }

    @Override
    public String toString() {
        return name().toLowerCase().replace('_', '-');
    }

}
