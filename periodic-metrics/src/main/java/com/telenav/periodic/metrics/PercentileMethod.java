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

import com.mastfrog.util.collections.LongList;
import static java.lang.Math.max;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/**
 * Methods for computing the percentiles, which have varying costs; NEAREST is
 * the cheapest, simply picking the closest value to the target;
 * SPLINE_INTERPOLATION is the most expensive but the most potentially accurate;
 * INTERPOLATED is takes the weighted average of the nearest two values weighted
 * by the distance of <code>percentile * size</code> from the midpoint between
 * the tow indices.
 *
 * @author Tim Boudreau
 */
public enum PercentileMethod {
    /**
     * Cheapest. Returns the value at the index of series size multiplied by the
     * percentile, rounded.
     */
    NEAREST,
    /**
     * Returns the weighted average of the values at the indices nearest the
     * series size multiplied by the percentile, weighted by the distance of the
     * computed, floating point index, to the midpoint between the floor and
     * ceiling of the computed index - so, given a computed index of, say, 92.3,
     * you get the average of values[92] * 0.3 plus values[93] * 1.7.
     */
    INTERPOLATED,
    /**
     * Spline interpolation. Expensive.
     */
    SPLINE_INTERPOLATION;
    /**
     * Threshold below which INTERPOLATED falls over to the NEAREST method
     * instead of using weighted values.
     */
    private static final double THRESHOLD = 0.001;
    /**
     * Cache of polynomial splines - these are not cheap to create, and we are
     * likely to be called several times with the same series for different
     * percentiles.
     */
    private static final Map<LongList, PolynomialSplineFunction> SPLINE_CACHE
            = Collections.synchronizedMap(new WeakHashMap<>());

    private static PolynomialSplineFunction splineFor(LongList series) {
        // Pending - for very large series, we could minimize the number of
        // points we compute to a subset around the target index
        return SPLINE_CACHE.computeIfAbsent(series, ll -> {
            SplineInterpolator spl = new SplineInterpolator();
            double[] values = new double[series.size()];
            double[] xs = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = series.getAsLong(i);
                xs[i] = i;
            }
            return spl.interpolate(xs, values);
        });
    }

    /**
     * Compute a percentile value given a value series and a percentile.
     *
     * @param percentile A percentile
     * @param series A series of values
     * @return A value which, depending on the algorithm, may or may not
     * actually be present in the input series
     */
    public long value(double percentile, LongList series) {
        switch (this) {
            case NEAREST:
                return valueNearest(percentile, series);
            case INTERPOLATED:
                return valueInterpolated(percentile, series);
            case SPLINE_INTERPOLATION:
                return valueCurveFitting(percentile, series);
            default:
                throw new AssertionError(this);
        }
    }

    private long valueNearest(double percentile, LongList series) {
        int index = (int) Math.round(series.size() * percentile);
        return series.get(index - 1);
    }

    private long valueInterpolated(double percentile, LongList series) {
        double ix = series.size() * percentile;
        double rounded = Math.round(ix);
        double delta = Math.abs(ix - rounded);
        if (delta < THRESHOLD) {
            return series.get((int) (rounded - 1));
        }
        int ixlow = (int) Math.floor(ix) - 1;
        int ixhigh = (int) Math.ceil(ix) - 1;
        /*
        Say we have a value whose logical index is 92.3.
        An average would be vals[92] + vals[93] / 2.
        
        What we want to do here is to use a little more of the high value
        and a little less of the low value.
        
        So, ((vals[93] * 1.3) + (vals[92] * 0.7)) / 2.
        
        If it were reversed, and we had a logical index of 92.7,
        then we would want
        
        (vals[92] * 1.3) + (vals[93] * 0.7).
         */
        double weightHigh;
        double weightLow;
        // Find the midpoint between the two nearest, e.g. 92.5
        double midpoint = ixlow + 0.5;

        if (ix - 1 == midpoint) {
            // If we are exactly at the midpoint, use the average
            return (series.get(ixlow) + series.get(ixhigh)) / 2;
        } else if (ix - 1 > midpoint) {
            // If ix > midpoint, then weight high is 1 + (1 - (ix - floor(ix))
            weightHigh = 1 + (1 - (ix - Math.floor(ix)));
            // and weight low is 2 - weight high
            weightLow = 2 - weightHigh;
        } else {
            // if ix < midpoint then weight high = 1 - (ix - floor(ix))
            weightHigh = 1 - (ix - Math.floor(ix));
            // and weight low is 1 + (1 - weight high)
            weightLow = 1 + (1 - weightHigh);
        }
        long origLow = series.get(max(0, ixlow));
        long origHigh = series.get(max(0, ixhigh));

        double valLow = origLow * weightLow;
        double valHigh = origHigh * weightHigh;

        double result = (valLow + valHigh) / 2;
        return Math.round(result);
    }

    private long valueCurveFitting(double percentile, LongList series) {
        double ix = series.size() * percentile;
        return Math.round(splineFor(series).value(ix - 1));
    }

}
