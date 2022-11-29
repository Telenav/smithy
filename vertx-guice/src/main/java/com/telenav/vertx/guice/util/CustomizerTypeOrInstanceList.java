package com.telenav.vertx.guice.util;

import com.google.inject.Binder;
import com.google.inject.Provider;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 *
 * @author Tim Boudreau
 */
public final class CustomizerTypeOrInstanceList<T> extends TypeOrInstanceList<UnaryOperator<T>> {

    private CustomizerTypeOrInstanceList() {

    }

    public static <T> CustomizerTypeOrInstanceList<T> customizerTypeOrInstanceList() {
        return new CustomizerTypeOrInstanceList<>();
    }

    public UnaryOperator<T> toFunction(Binder binder) {
        return new Func<>(super.get(binder));
    }

    static class Func<T> implements UnaryOperator<T> {

        private final List<Provider<? extends UnaryOperator<T>>> list;

        public Func(List<Provider<? extends UnaryOperator<T>>> list) {
            this.list = list;
        }

        @Override
        public T apply(T t) {
            for (Provider<? extends UnaryOperator<T>> p : list) {
                t = p.get().apply(t);
            }
            return t;
        }

    }
}
