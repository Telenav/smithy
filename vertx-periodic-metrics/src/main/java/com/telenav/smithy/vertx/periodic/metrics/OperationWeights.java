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

import com.google.inject.ImplementedBy;

/**
 * Evaluates the weights of individual operations in an API. The default
 * implementation looks in Settings for {Operation.name()}.weight or
 * {Operation.loggingName()}.weight, using that if present, and otherwise
 * failing over to <code>1D/Operation.values().length()</code>.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultOperationWeights.class)
public interface OperationWeights {

    /**
     * Determine the multiplier to use when computing the number of metrics
     * buckets (an AtomicIntegerArray) to allocate for each operation in order
     * to collect timing metrics. The timings are typically request duration
     * from initiation to flushing the last byte of the response. These are
     * stored as unsigned ints (so requests longer than 14 hours in duration
     * will be ignored).
     * <p>
     * The number of buckets allocated is
     * <code>targetRequestsPerSecond * samplingInterval.toSeconds() * operationWeight(op)</code>
     * so, if you expect (or are configuring a load balancer to limit you to)
     * 1000 requests per second, then you need 600,000 stats buckets to collect
     * one minute's worth of data for all operations at maximum load (and this
     * will be really be the number used for the <i>all</i>
     * bucket which aggregates across all HTTP). But if the operation is only
     * likely to be 1/3 of your operations, then the number of buckets actually
     * alloacted can be reduced to <i>1000 req * 60 seconds * 0.33 = 198,000</i>
     * buckets.
     * </p>
     * <p>
     * Buckets are allocated once on startup and reused, in pairs of sets of
     * buckets, plus an array used for statistical calculations against a
     * snapshot, so the actual memory requirements for one mintue's worth of
     * complete metrics at 1000 requests per second is <i>198,000 buckets * 8
     * bytes = 4,752,000 bytes</i> (subject to reduction via pending
     * optimization).
     * </p>
     * <p>
     * If more requests occur in a period than there are buckets, then writes
     * will wrap around and early requests for the target period are lost.
     * </p>
     * <p>
     * If the required number of buckets exceeds the hard maximum number of
     * stats buckets, then stats capturing, then a only random sample of timings
     * will be collected, such that the percentage of requests sampled fits
     * within the available buckets, but this means that statistics are
     * approximate.
     * </p>
     * <p>
     * The default implementation simply returns <code>1D</code> for null, and
     * <code>1D / Op.getEnumConstants().length</code> for an even distributions
     * of buckets across operations (which will never be the case in real life).
     * </p>
     * <p>
     * A hard maximum number of stats buckets can be set with the setting
     * <code>max.stats.buckets</code>, and will not be exceeded; the expected
     * overall requests per second can be set with the setting
     * <code>req.per.second.target</code>. Bear in mind that vertx-based servers
     * are capable of handling hundreds of thousands of requests per second, so
     * practical values for these should be large unless there is a known cap.
     * The default is an expectation of handling <i>84,000</i> requests per
     * second.
     * </p>
     *
     * @param op The operation, or null for all operations
     * @return A positive double greater than zero and less or equal to than
     * one.
     */
    double operationWeight(Enum<?> op);

}
