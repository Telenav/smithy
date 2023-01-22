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
import static java.lang.Integer.min;
import static java.lang.Math.max;
import java.util.AbstractSet;
import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Random;

/**
 * A set of characters over a bit set.
 *
 * @author Tim Boudreau
 */
final class CharsBitSet extends AbstractSet<Character> implements Iterable<Character> {

    private final BitSet bits;
    private final boolean initiallyPopulated;
    private int currMax;
    private char[] cachedArr;

    CharsBitSet() {
        this(false);
    }

    CharsBitSet(boolean initiallyPopulated) {
        this.bits = new BitSet(128);
        this.initiallyPopulated = initiallyPopulated;
    }

    CharsBitSet(char max, boolean initiallyPopulated) {
        this(initiallyPopulated);
        if (max > 0 && initiallyPopulated) {
            bits.set(0, max);
        }
        currMax = max;
    }

    CharsBitSet(char first, char max, boolean initiallyPopulated) {
        this(initiallyPopulated);
        int high = max(first, max);
        int low = min(first, max);
        if (high > 0 && initiallyPopulated) {
            bits.set(0, high + 1);
        }
        if (low > 0) {
            bits.flip(0, low);
        }
        currMax = max;
    }

    CharsBitSet(boolean initiallyPopulated, BitSet bits, int max) {
        this.bits = bits;
        this.initiallyPopulated = initiallyPopulated;
        this.currMax = max;
    }

    CharsBitSet(String initialContents) {
        this(maxChar(initialContents), false);
        for (int i = 0; i < initialContents.length(); i++) {
            char c = initialContents.charAt(i);
            bits.set(c);
        }
    }

    int first() {
        return bits.nextSetBit(0);
    }

    private char[] cachedArr() {
        if (cachedArr == null) {
            cachedArr = toCharArray();
        }
        return cachedArr;
    }

    private void clearState() {
        cachedArr = null;
    }

    public int eachChar(CharConsumer c) {
        int result = 0;
        for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) {
            result++;
            c.accept((char) bit);
        }
        return result;
    }

    public Optional<Character> pick(Random rnd) {
        if (bits.isEmpty()) {
            return Optional.empty();
        }
        char[] chars = cachedArr();
        return Optional.of(chars[rnd.nextInt(chars.length)]);
    }

    CharsBitSet minLimit(char c) {
        ensureInitialized(c);
        return this;
    }

    CharsBitSet trimTo(char c) {
        if (c < currMax + 1) {
            bits.clear(currMax + 1, c);
        }
        return this;
    }

    private static char maxChar(String what) {
        char result = 0;
        for (int i = 0; i < what.length(); i++) {
            result = (char) max(what.charAt(i), result);
        }
        return result;
    }

    private CharsBitSet(CharsBitSet other) {
        this.currMax = other.currMax;
        this.bits = (BitSet) other.bits.clone();
        this.initiallyPopulated = other.initiallyPopulated;
    }

    public char[] toCharArray() {
        char[] result = new char[bits.cardinality()];
        for (int bit = bits.nextSetBit(0), cursor = 0; bit >= 0; bit = bits.nextSetBit(bit + 1), cursor++) {
            result[cursor] = (char) bit;
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int bit = bits.nextSetBit(0), cursor = 0; bit >= 0; bit = bits.nextSetBit(bit + 1), cursor++) {
            RegexElement.escapeForDisplay((char) bit, sb);
        }
        return sb.toString();
    }

    public CharsBitSet copy() {
        return new CharsBitSet(this);
    }

    public char last() {
        return (char) currMax;
    }

    @Override
    public int size() {
        return bits.cardinality();
    }

    private BitSet copyBits() {
        return (BitSet) bits.clone();
    }

    private BitSet copyBits(int max) {
        BitSet set = new BitSet(Math.max(max + 1, currMax + 1));
        set.or(bits);
        if (max > currMax && initiallyPopulated) {
            set.set(currMax + 1, max + 1);
        }
        return set;
    }

    public CharsBitSet invert(char start, char end) {
        if (start > end) {
            char hold = start;
            start = end;
            end = hold;
        }
        if (end == 0) {
            return this;
        }
        bits.flip(start, end + 1);
        clearState();
        return this;
    }

    public CharsBitSet union(CharsBitSet other) {
        if (other == this) {
            return new CharsBitSet(initiallyPopulated, copyBits(), currMax);
        }
        BitSet nue = copyBits(max(other.currMax, currMax));
        nue.or(other.bits);
        return new CharsBitSet(initiallyPopulated, nue, max(other.currMax, currMax));
    }

    public CharsBitSet intersection(CharsBitSet other) {
        if (other == this) {
            return new CharsBitSet(initiallyPopulated, copyBits(), currMax);
        }
        int newMax = max(other.currMax, currMax);
        BitSet nue = copyBits(newMax);
        nue.and(other.bits);
        return new CharsBitSet(initiallyPopulated, nue, newMax);
    }

    public CharsBitSet xor(CharsBitSet other) {
        if (other == this) {
            return new CharsBitSet(initiallyPopulated);
        }
        int newMax = max(other.currMax, currMax);
        BitSet nue = copyBits(newMax);
        nue.xor(other.bits);
        return new CharsBitSet(initiallyPopulated, nue, newMax);
    }

    public CharsBitSet complement() {
        BitSet nue = copyBits();
        nue.flip(0, currMax + 1);
        return new CharsBitSet(!initiallyPopulated, nue, currMax);
    }

    public boolean intersects(CharsBitSet other) {
        return bits.intersects(other.bits);
    }

    public CharsBitSet invertInPlace() {
        bits.flip(0, currMax);
        clearState();
        return this;
    }

    private void ensureInitialized(int a, int b) {
        ensureInitialized(max(a, b));
    }

    private void ensureInitialized(int max) {
        if (max > currMax) {
            if (initiallyPopulated) {
                bits.set(currMax + 1, max + 1);
            } else {
                bits.clear(currMax + 1, max + 1);
            }
            currMax = max;
        }
    }

    @Override
    public boolean contains(Object o) {
        if (o == null || o.getClass() != Character.class) {
            return false;
        }
        return contains((Character) o);
    }

    public boolean contains(char c) {
        return bits.get(c);
    }

    public void addAll(String s) {
        for (int i = 0; i < s.length(); i++) {
            bits.set(s.charAt(i));
        }
        clearState();
    }

    public void add(char c) {
        ensureInitialized(c);
        bits.set(c);
        clearState();
    }

    public void remove(char c) {
        ensureInitialized(c);
        bits.clear(c);
        clearState();
    }

    public CharsBitSet addRange(char start, char end) {
        ensureInitialized(start, end);
        if (start == end) {
            bits.set(start);
        } else if (end < start) {
            bits.set(end, start + 1);
        } else {
            bits.set(start, end + 1);
        }
        clearState();
        return this;
    }

    public CharsBitSet removeRange(char start, char end) {
        ensureInitialized(start, end);
        if (start == end) {
            bits.clear(start);
        } else if (end < start) {
            bits.clear(end, start + 1);
        } else {
            bits.clear(start, end + 1);
        }
        clearState();
        return this;
    }

    public void removeAll(CharsBitSet c) {
        bits.andNot(c.bits);
        clearState();
    }

    public void addAll(CharsBitSet c) {
        bits.or(c.bits);
        clearState();
    }

    public void retainAll(CharsBitSet c) {
        bits.and(c.bits);
        clearState();
    }

    @Override
    public CharIterator iterator() {
        return new CI();
    }

    public CharIterator iterator(char start) {
        CI result = new CI();
        result.pos = start;
        return result;
    }

    public interface CharIterator extends PrimitiveIterator<Character, CharConsumer> {

    }

    class CI implements CharIterator {

        private int pos = 0;

        @Override
        public void forEachRemaining(CharConsumer action) {
            for (pos = bits.nextSetBit(pos); pos >= 0; pos = bits.nextSetBit(pos + 1)) {
                action.accept((char) pos);
            }
        }

        @Override
        public boolean hasNext() {
            return bits.nextSetBit(pos) >= 0;
        }

        @Override
        public Character next() {
            int bit = bits.nextSetBit(pos);
            if (bit < 0) {
                throw new NoSuchElementException();
            }
            pos = bit + 1;
            return (char) bit;
        }
    }

}
