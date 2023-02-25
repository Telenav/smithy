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
package com.telenav.vertx.guice.util;

import com.google.inject.Binder;
import com.google.inject.Provider;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A TypeOrInstanceList specialized for UnaryOperators. Non-API.
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

    @Override
    @SuppressWarnings("unchecked")
    public TypeOrInstanceList<UnaryOperator<T>> copy() {
        return (CustomizerTypeOrInstanceList<T>) super.copy();
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
