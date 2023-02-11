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

import static com.telenav.smithy.rex.RegexElement.countForMinMax;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 *
 * @author Tim Boudreau
 */
final class CharacterClass implements ContainerRegexElement, Confoundable<CharacterClass> {

    private final CharsBitSet characters;
    private transient CharsBitSet cachedComplement;
    private int min = -1;
    private int max = -1;
    private boolean negated;

    CharacterClass(char start, char initialMax) {
        characters = new CharsBitSet(start, initialMax, false);
    }

    CharacterClass(CharsBitSet characters) {
        this.characters = characters.copy();
    }

    CharacterClass(boolean negated) {
        characters = new CharsBitSet();
        this.negated = negated;
    }

    CharacterClass() {
        this((char) 32, (char) 127);
    }

    CharacterClass(CharacterClass c) {
        this.characters = c.characters.copy();
        this.negated = c.negated;
        this.min = c.min;
        this.max = c.max;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (negated) {
            result.append("[^").append(characters).append("]");
        } else {
            result.append("[").append(characters).append("]");
        }
        if (max == Integer.MAX_VALUE) {
            if (min <= 0) {
                result.append('*');
            } else if (min == 1) {
                result.append('+');
            } else {
                result.append('{').append(min).append(",}");
            }
        } else if (min >= 0) {
            if (max < 0) {
                result.append('{').append(min).append(",}");
            } else if (max >= 0) {
                result.append('{').append(min).append(',').append(max).append('}');
            }
        }
        return result.toString();
    }

    private CharsBitSet complement() {
        if (cachedComplement == null) {
            cachedComplement = characters.complement();
            if (characters.first() >= 32) {
                cachedComplement.removeRange((char) 0, (char) 31);
            }
        }
        return cachedComplement;
    }

    public boolean matches(char c) {
        if (negated) {
            return !characters.contains(c);
        } else {
            return characters.contains(c);
        }
    }

    private void clearState() {
        cachedComplement = null;
    }

    public CharacterClass negated(boolean negated) {
        this.negated = negated;
        return this;
    }

    public CharacterClass negated() {
        negated = true;
        return this;
    }

    @Override
    public boolean isEmpty() {
        return characters.isEmpty();
    }

    @Override
    public ContainerRegexElement prune() {
        return this;
    }

    @Override
    public <T> T enter(Function<Consumer<RegexElement>, T> f) {
        return f.apply(this::add);
    }

    @Override
    public void add(RegexElement el) {
        if (el instanceof CharRange cr) {
            characters.addRange(cr.start.cc, cr.end.cc);
        } else if (el instanceof OneChar oc) {
            if (oc.cc == 'd') {
                new Exception(el.toString()).printStackTrace();
            }
            characters.add(oc.cc);
        } else if (el instanceof OneString os) {
            characters.addAll(os.string);
        } else if (el instanceof CharacterClass cc) {
            if (cc.negated) {
                characters.removeAll(cc.characters);
            } else {
                characters.addAll(cc.characters);
            }
        } else {
            throw new IllegalArgumentException("Huh? " + el);
        }
    }

    @Override
    public void boundLast(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public ContainerRegexElement duplicate() {
        return new CharacterClass(characters);
    }

    @Override
    public ElementKinds kind() {
        return ElementKinds.CHAR_CLASS;
    }

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        CharsBitSet set = negated ? complement() : characters;
        Runnable emitter = () -> {
            set.pick(rnd).ifPresent(c -> {
                into.append(c);
            });
        };
        int count = countForMinMax(min, max, rnd);
        for (int i = 0; i < count; i++) {
            emitter.run();
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.characters);
        hash = 97 * hash + Objects.hashCode(this.cachedComplement);
        hash = 97 * hash + this.min;
        hash = 97 * hash + this.max;
        hash = 97 * hash + (this.negated ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CharacterClass other = (CharacterClass) obj;
        if (this.min != other.min) {
            return false;
        }
        if (this.max != other.max) {
            return false;
        }
        if (this.negated != other.negated) {
            return false;
        }
        if (!Objects.equals(this.characters, other.characters)) {
            return false;
        }
        return Objects.equals(this.cachedComplement, other.cachedComplement);
    }

    @Override
    public boolean canConfound() {
        return true;
    }

    @Override
    public Optional<CharacterClass> confound() {
        CharacterClass result = new CharacterClass(this).negated(!negated);
        if (min > 2) {
            result.boundLast(0, min - 1);
        } else if (max < 100 && max > 0) {
            result.boundLast(max + 1, -1);
        }
        return Optional.of(result);
    }

}
