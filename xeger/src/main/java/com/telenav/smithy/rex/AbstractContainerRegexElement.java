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
package com.telenav.smithy.rex;

import static com.telenav.smithy.rex.ElementKinds.ALTERNATION;
import static com.telenav.smithy.rex.ElementKinds.REGEX;
import static com.telenav.smithy.rex.EmittingElementSelectionStrategy.EmittingElementSelectionStrategies.ALL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for container elements.
 */
abstract class AbstractContainerRegexElement<C extends AbstractContainerRegexElement>
        implements RegexElement, ContainerRegexElement, Confoundable<C> {

    final List<RegexElement> contents;
    final ElementKinds kind;

    AbstractContainerRegexElement(ElementKinds kind) {
        this.kind = kind;
        this.contents = new ArrayList<>(8);
    }

    AbstractContainerRegexElement(ElementKinds kind, List<RegexElement> contents) {
        this.kind = kind;
        this.contents = contents;
    }

    @Override
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public C prune() {
        for (Iterator<RegexElement> it = contents.iterator(); it.hasNext();) {
            RegexElement re = it.next();
            boolean empty = re.as(AbstractContainerRegexElement.class)
                    .map(ce -> ce.prune().isEmpty()).orElse(false);
            if (empty) {
                it.remove();
            }
        }
        return (C) this;
    }

    abstract C newCopy(List<RegexElement> elements);

    List<RegexElement> duplicateElements() {
        List<RegexElement> nue = new ArrayList<>(contents.size());
        contents.forEach(el -> nue.add(el.duplicate()));
        return nue;
    }

    List<RegexElement> duplicateElementsWithTransform(Function<RegexElement, RegexElement> f) {
        List<RegexElement> nue = new ArrayList<>(contents.size());
        contents.forEach(el -> nue.add(f.apply(el.duplicate())));
        return nue;
    }

    @Override
    public <T> T enter(Function<Consumer<RegexElement>, T> f) {
        return f.apply(contents::add);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(kind.toString());
        sb.append('(');
        for (Iterator<RegexElement> it = contents.iterator(); it.hasNext();) {
            RegexElement r = it.next();
            sb.append(r);
            if (kind == ALTERNATION && it.hasNext()) {
                sb.append('|');
            }
        }
        return sb.append(')').toString();
    }

    @Override
    public void add(RegexElement el) {
        contents.add(el);
    }

    @Override
    public ElementKinds kind() {
        if (contents.isEmpty()) {
            return ElementKinds.EMPTY;
        }
        return kind;
    }

    @Override
    public void boundLast(int min, int max) {
        int end = contents.size() - 1;
        RegexElement el = contents.get(end);
        if (!(el instanceof ContainerRegexElement)) {
            GeneralBag nue = new GeneralBag(REGEX, ALL);
            nue.add(el);
            el = nue;
        }
        contents.set(end, new Bounds((ContainerRegexElement) el, min, max));
    }

    @Override
    public boolean canConfound() {
        int confoundableCount = 0;
        for (RegexElement el : contents) {
            if (el instanceof Confoundable con) {
                if (con.canConfound()) {
                    confoundableCount++;
                }
            }
        }
        if (kind == ElementKinds.ALTERNATION) {
            return confoundableCount >= contents.size() - 1;
        }
        return confoundableCount > 0;
    }

    @Override
    public Optional<C> confound() {
        boolean[] anyTransformed = new boolean[1];
        List<RegexElement> xformed = duplicateElementsWithTransform(el -> {
            if (el instanceof Confoundable<?> con) {
                Optional<? extends RegexElement> opt = con.confound();
                if (opt.isPresent()) {
                    anyTransformed[0] = true;
                    return opt.get();
                }
            }
            return el;
        });
        if (anyTransformed[0]) {
            return Optional.of(newCopy(xformed));
        }
        return Optional.empty();
    }

    @Override
    public void traverse(int depth, BiConsumer<Integer, RegexElement> c) {
        c.accept(depth, this);
        contents.forEach(el -> el.traverse(depth + 1, c));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.contents);
        hash = 53 * hash + Objects.hashCode(this.kind);
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
        final AbstractContainerRegexElement<?> other = (AbstractContainerRegexElement<?>) obj;
        return true;
    }
}
