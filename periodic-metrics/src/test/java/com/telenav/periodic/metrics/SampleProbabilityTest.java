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

import static java.lang.Math.abs;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
 
/**
 *
 * @author Tim Boudreau
 */
public class SampleProbabilityTest {

    @Test
    public void testRandomlySampleWithAdjustment() {
        // Test that the percentages of true vs false emitted by a RandomlySample
        // with a given probability matches the target given enough samples
        for (SampleProbability a : SampleProbability.values()) {
            // Use a specific random or we could have a randomly failing test
            Random rnd = new Random(91232452393L);
            SampleProbability.RandomlySample smp = new SampleProbability.RandomlySample(120, rnd, a);
            int max = 1000000;
            int ct = 0;
            for (int j = 0; j < max; j++) {
                if (smp.getAsBoolean()) {
                    ct++;
                }
            }
            double got = (double) ct / max;
            double expected = a.targetProbability();
            double delta = abs(got - expected);
            assertEquals(expected, got, .0008, () -> "Wrong result for " + a.name() + " (" + a + ") delta " + delta);
        }
    }

    @Test
    public void testRandomlySample() {
        // Test default 50/50 randomness
        SampleProbability.RandomlySample rs = new SampleProbability.RandomlySample(20, new Random(1));
        boolean[] as = new boolean[20];
        for (int i = 0; i < 20; i++) {
            as[i] = rs.getAsBoolean();
        }
        // Ensure we don't get the same array with no reset (we are using
        // a random seed where that won't accidentally happen - so if it does,
        // the array was not reinitialized on wrap around.
        boolean[] bs = new boolean[20];
        for (int i = 0; i < 20; i++) {
            bs[i] = rs.getAsBoolean();
        }
        assertFalse(Arrays.equals(as, bs));

        int max = 100000;
        int ct = 0;
        for (int i = 0; i < max; i++) {
            if (rs.getAsBoolean()) {
                ct++;
            }
        }
        assertTrue(ct > max * 0.4, "Too low: Should be effectively a coin "
                + "toss, but got " + ((double) ct / (double) max));
        assertTrue(ct < max * 0.6, "Too high: Should be effectively a coin "
                + "toss, but got " + ((double) ct / (double) max));
    }
}