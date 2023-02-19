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
import static com.telenav.periodic.metrics.PercentileMethod.INTERPOLATED;
import static com.telenav.periodic.metrics.PercentileMethod.NEAREST;
import static com.telenav.periodic.metrics.PercentileMethod.SPLINE_INTERPOLATION;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PercentileMethodTest {

    @Test
    public void testInterpolationAlgorithms() {
        // Test implementation where the number of elements is larger than
        // we need
        LongList list = LongList.create(200);
        for (int i = 0; i < 200; i++) {
            list.add((i + 1) * 100);
        }
        for (PercentileMethod m : PercentileMethod.values()) {
            // We test a few definitely-in-between values to ensure we aren't
            // being fooled by always picking up the exact value
            double p90, p99, p10, p926, p962;
            switch (m) {
                case NEAREST:
                    p90 = 18000;
                    p99 = 19800;
                    p10 = 2000;
                    p926 = 18500;
                    p962 = 19200;
                    break;
                case INTERPOLATED:
                    p90 = 18000;
                    p99 = 19800;
                    p10 = 2000;
                    p926 = 18540;
                    p962 = 19230;
                    break;
                case SPLINE_INTERPOLATION:
                    p90 = 18000;
                    p99 = 19800;
                    p10 = 2000;
                    p926 = 18520;
                    p962 = 19240;
                    break;
                default:
                    throw new AssertionError(m);
            }

            assertEquals(p90, m.value(0.9, list), "p90");
            assertEquals(p99, m.value(0.99, list), "p99");
            assertEquals(p10, m.value(0.10, list), "p10");
            assertEquals(p926, m.value(0.926, list), "p926");
            assertEquals(p962, m.value(0.962, list), "p962");
        }

        list = LongList.create(10);
        for (int i = 0; i < 10; i++) {
            list.add((i + 1) * 1000);
        }

        for (PercentileMethod m : PercentileMethod.values()) {
            double p90, p99, p10, p923, p962;
            switch (m) {
                case NEAREST:
                    p90 = 9000;
                    p99 = 10000;
                    p10 = 1000;
                    p923 = 9000;
                    p962 = 10000;
                    break;
                case INTERPOLATED:
                    p90 = 9000;
                    p99 = 9550;
                    p10 = 1000;
                    p923 = 9385;
                    p962 = 9690;
                    break;
                case SPLINE_INTERPOLATION:
                    p90 = 9000;
                    p99 = 9900;
                    p10 = 1000;
                    p923 = 9230;
                    p962 = 9620;
                    break;
                default:
                    throw new AssertionError(m);
            }

            assertEquals(p90, m.value(0.9, list), "p90");
            assertEquals(p99, m.value(0.99, list), "p99");
            assertEquals(p10, m.value(0.10, list), "p10");
            assertEquals(p923, m.value(0.923, list), "p923");
            assertEquals(p962, m.value(0.962, list), "p962");
        }
    }

}
