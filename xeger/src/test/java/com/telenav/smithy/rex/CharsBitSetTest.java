/*
 * Copyright 2023 Mastfrog Technologies.
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
package com.telenav.smithy.rex;

import com.mastfrog.function.character.CharConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class CharsBitSetTest {

    @Test
    public void testNonInverted() {
        CharsBitSet bs = new CharsBitSet("abalone");
        assertEquals("abelno", bs.toString());
        assertEquals(6, bs.size());
        assertTrue(bs.contains('a'));
        assertTrue(bs.contains('b'));
        assertTrue(bs.contains('l'));
        assertTrue(bs.contains('o'));
        assertTrue(bs.contains('n'));
        assertTrue(bs.contains('e'));
        assertFalse(bs.contains('k'));

        StringBuilder sb1 = new StringBuilder();
        for (Character c : bs) {
            sb1.append(c);
        }
        assertEquals("abelno", sb1.toString());
        StringBuilder sb2 = new StringBuilder();
        int ct = bs.eachChar(sb2::append);
        assertEquals("abelno", sb2.toString());
        assertEquals(6, ct);
        StringBuilder sb3 = new StringBuilder();
        bs.iterator().forEachRemaining((CharConsumer) sb3::append);
        assertEquals("abelno", sb3.toString());
        StringBuilder sb4 = new StringBuilder();
        bs.iterator('e').forEachRemaining((CharConsumer) sb4::append);
        assertEquals("elno", sb4.toString());

        CharsBitSet two = new CharsBitSet("abacus");
        assertEquals("abcsu", two.toString());

        CharsBitSet isect = bs.intersection(two);

        assertEquals("ab", isect.toString());

        CharsBitSet union = bs.union(two);

        assertEquals("abcelnosu", union.toString());

        assertTrue(bs.intersects(two));

        CharsBitSet complement = bs.complement();
        assertFalse(complement.intersects(bs));
        assertTrue(complement.intersects(two));
    }

    @Test
    public void testInitiallySet() {
        CharsBitSet set = new CharsBitSet(' ', (char) 127, true);
        assertEquals(127 - 31, set.size());
        for (int i = 0; i < 128; i++) {
            if (i < 32) {
                assertFalse(set.contains((char) i), "Set but should not be: '" + ((char) i) + "' (" + i + ")");
            } else {
                assertTrue(set.contains((char) i), "Unset but should be: '" + ((char) i) + "' (" + i + ")");
            }
        }
        assertTrue(set.intersects(new CharsBitSet("blah")));
    }
    
    @Test
    public void testAddRange() {
        CharsBitSet bs = new CharsBitSet();
        bs.addRange('e', 'j');
        for (char c ='e'; c < 'k'; c++) {
            assertTrue(bs.contains(c));
        }
        for (char c='a'; c < 'e'; c++) {
            assertFalse(bs.contains(c));
        }
        
    }

}
