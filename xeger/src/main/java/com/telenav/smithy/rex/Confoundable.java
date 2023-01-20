package com.telenav.smithy.rex;

import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
interface Confoundable<C extends RegexElement> extends RegexElement {

    boolean canConfound();

    Optional<C> confound();
}
