periodic-metrics
================

Generic support for collecting, summing and performing statstical operations on numeric
metrics, with no particular assumptions about where they are emitted to or what they
mean, with Guice support.

Contains a standard set of metrics, some of which can be obtained automatically at
a timed frequency, from the JVM's JMX Mbeans.

A `Metric` is simply a name and some information about when it should be omitted
(some built-in metrics, such as the JMX ones, depend on services being available from
the host operation system - for example, the capacity of Mac OS's $TMPDIR will always
register as -1, but will work fine on Linux) - via `omitIfZero()` and `omitIfNegative()`.

Metrics that reflect events happening over a time period are emitted into a `MetricsSink`
by the code that detects them (such as the number of http requests in one minute).

Metrics that are computed on-demand (but may contain internal state that is reset on
read - for example, the JDK's GC pause metrics are cumulative over the life of the JVM,
so the last value must be subtracted from the new value to get the delta over a given
time period) are instances of `OnDemandMetric`.

Things that are computed on-demand but wish to emit multiple metrics from a single
atomic snapshot of state (typically timings you wish to compute the min/max/avg/median/p10/p90/p99
for over a time period) implement `MultiMetric` whose signature `boolean get(BiConsumer<Metric, N> c)`
can take a snapshot of the current state, and then pass each derived bvalue to the consumer.

`OperationStatsMetric` is an implementation of such statistical computation over timings, and
maintains a pair of atomic-backed ring-buffers it swaps between.

Use `MetricsModule` to enable metrics support in your application.  Register the metrics
you want to record by implementing `MetricsRegistry` and binding it as an eager singleton.

