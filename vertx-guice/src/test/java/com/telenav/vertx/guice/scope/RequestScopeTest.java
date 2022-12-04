/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.vertx.guice.scope;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import static java.util.Arrays.asList;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class RequestScopeTest {

    @Test
    public void testOptionalsAreDifferentiated() {
        RequestScope scope = new RequestScope();
        Injector inj = Guice.createInjector(Stage.PRODUCTION, binder -> {
            scope.bindOptional(binder, String.class);
            scope.bindOptional(binder, Integer.class);
            scope.bindType(binder, Long.class);
        });

        ThingOne one;
        ThingOne oneA;
        ThingTwo two;
        ThingThree three;
        ThingThree threeB;

        assertFalse(scope.inScope());
        try (ScopeEntry first = scope.enter(123456L)) {
            assertTrue(scope.inScope());
            assertTrue(scope.contents().equals(asList(123456L)));
            try (ScopeEntry second = scope.enter(56789L, Optional.of(23))) {
                assertTrue(scope.inScope());
                assertTrue(scope.contents().contains(123456L));
                assertTrue(scope.contents().contains(56789L));
                assertTrue(scope.contents().contains(Optional.of(23)));
                try (ScopeEntry third = scope.enter(Optional.of("foo"))) {
                    assertTrue(scope.contents().contains(123456L));
                    assertTrue(scope.contents().contains(56789L));
                    assertTrue(scope.contents().contains(Optional.of(23)));
                    assertTrue(scope.contents().contains(Optional.of("foo")));
                    one = inj.getInstance(ThingOne.class);
                    two = inj.getInstance(ThingTwo.class);
                    three = inj.getInstance(ThingThree.class);
                }
                assertTrue(scope.contents().contains(123456L));
                assertTrue(scope.contents().contains(56789L));
                assertTrue(scope.contents().contains(Optional.of(23)));
                assertFalse(scope.contents().contains(Optional.of("foo")));
            }
            assertTrue(scope.inScope());
            assertFalse(scope.contents().contains(56789L));
            threeB = inj.getInstance(ThingThree.class);
            oneA = inj.getInstance(ThingOne.class);
        }
        assertFalse(scope.inScope());
        assertTrue(scope.contents().isEmpty());

        ThingTwo twoB = inj.getInstance(ThingTwo.class);

        assertTrue(one.hasValue());
        assertTrue(two.hasValue());
        assertEquals(123456L, threeB.value);
        assertEquals(56789L, three.value);
        assertEquals("foo", one.get());
        assertEquals(23, two.get().intValue());
        assertFalse(twoB.hasValue());
    }

    static class ThingOne {

        private final Optional<String> string;

        @Inject
        ThingOne(Optional<String> what) {
            this.string = what;
        }

        public String toString() {
            return string.orElse("-none-");
        }

        boolean hasValue() {
            return string.isPresent();
        }

        String get() {
            return string.get();
        }
    }

    static class ThingTwo {

        private final Optional<Integer> value;

        @Inject
        ThingTwo(Optional<Integer> valuel) {
            this.value = valuel;
        }

        public String toString() {
            return value.map(v -> Integer.toString(v)).orElse("-none-");
        }

        boolean hasValue() {
            return value.isPresent();
        }

        public Integer get() {
            return value.get();
        }

    }

    static class ThingThree {

        private final long value;

        @Inject
        ThingThree(Long value) {
            this.value = value;
        }

        public String toString() {
            return Long.toString(value);
        }

        public Long get() {
            return value;
        }
    }
}
