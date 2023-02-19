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

import static com.telenav.periodic.metrics.BuiltInMetrics.AVAILABLE_MEMORY;
import static com.telenav.periodic.metrics.BuiltInMetrics.CLASSES;
import static com.telenav.periodic.metrics.BuiltInMetrics.HEAP_IN_USE;
import static com.telenav.periodic.metrics.BuiltInMetrics.LIVE_THREADS;
import static com.telenav.periodic.metrics.BuiltInMetrics.NON_HEAP_TOTAL_MEMORY;
import static com.telenav.periodic.metrics.BuiltInMetrics.NON_HEAP_USED_MEMORY;
import static com.telenav.periodic.metrics.BuiltInMetrics.TOTAL_MEMORY;
import static com.telenav.periodic.metrics.BuiltInMetrics.WORKING_DIR_DISK_SPACE;
import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.util.Arrays.asList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * On demand metrics are ones which are computed on the fly rather than drizzled
 * into a sink. Two of our sink types allow for adding a set of on demand
 * metrics to their contents.
 *
 * @author Tim Boudreau
 */
public interface OnDemandMetric<N extends Number> extends Comparable<OnDemandMetric<?>> {

    static final ThreadGroup TOP_THREAD_GROUP = Thread.currentThread().getThreadGroup();

    Metric kind();

    N get();
    
    default void get(BiConsumer<Metric, N> c) {
        c.accept(kind(), get());
    }

    default boolean isValid() {
        return true;
    }

    @Override
    public default int compareTo(OnDemandMetric<?> o) {
        return Metric.compare(kind(), o.kind());
    }

    public static <N extends Number> OnDemandMetric<N> onDemandMetric(Metric kind, Supplier<N> supplier) {
        return new SimpleOnDemandMetric<>(kind, supplier);
    }

    public static OnDemandMetric<Long> nullMetric(Metric kind) {
        return new FixedOnDemandMetric<>(-1L, kind, false);
    }

    public static OnDemandMetric<Long> workingDirDiskSpace() {
        try {
            Path path = Paths.get(".").toRealPath();
            return diskSpace(WORKING_DIR_DISK_SPACE, path);
        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
            return nullMetric(WORKING_DIR_DISK_SPACE);
        }
    }

    public static OnDemandMetric<Long> tempDirDiskSpace() {
        try {
            Path path = Paths.get(System.getProperty("java.io.tmpdir")).toRealPath();
            return diskSpace(WORKING_DIR_DISK_SPACE, path);
        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
            return nullMetric(WORKING_DIR_DISK_SPACE);
        }
    }

    public static OnDemandMetric<Long> diskSpace(Metric diskSpaceKind, Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            if (store.getUsableSpace() == 0) {
                return nullMetric(diskSpaceKind);
            }
            return new SimpleOnDemandMetric<>(diskSpaceKind, () -> {
                try {
                    return store.getUsableSpace();
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                    return -1L;
                }
            });
        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
            return nullMetric(diskSpaceKind);
        }
    }

    public static OnDemandMetric<Long> availableMemory() {
        return new SimpleOnDemandMetric<>(AVAILABLE_MEMORY, () -> Runtime.getRuntime().freeMemory());
    }

    public static OnDemandMetric<Long> totalMemory() {
        return new SimpleOnDemandMetric<>(TOTAL_MEMORY, () -> Runtime.getRuntime().totalMemory());
    }

    public static OnDemandMetric<Long> heapInUse() {
        return new SimpleOnDemandMetric<>(HEAP_IN_USE, ()
                -> Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    public static OnDemandMetric<Long> liveThreads() {
        return new SimpleOnDemandMetric<>(LIVE_THREADS, () -> {
            return (long) TOP_THREAD_GROUP.activeCount();
        });
    }

    public static OnDemandMetric<Long> loadedClasses() {
        ClassLoadingMXBean bean = ManagementFactory.getClassLoadingMXBean();
        return onDemandMetric(CLASSES, () -> (long) bean.getLoadedClassCount());
    }

    public static OnDemandMetric<Long> nonHeapTotal() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        if (memBean.getNonHeapMemoryUsage().getMax() < 0) {
            return nullMetric(NON_HEAP_TOTAL_MEMORY);
        }
        return onDemandMetric(NON_HEAP_TOTAL_MEMORY, ()
                -> memBean.getNonHeapMemoryUsage().getMax());
    }

    public static OnDemandMetric<Long> nonHeapUsage() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        return onDemandMetric(NON_HEAP_USED_MEMORY, ()
                -> memBean.getNonHeapMemoryUsage().getUsed());
    }

    public static List<OnDemandMetric<Long>> defaultOnDemandMetrics() {
        return asList(heapInUse(), liveThreads(),
                workingDirDiskSpace(), tempDirDiskSpace(), loadedClasses(),
                nonHeapTotal(), nonHeapUsage());
    }
}
