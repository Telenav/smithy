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
package com.telenav.vertx.guice.util;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.function.Function;

/**
 * Factory for a Provider which allows to code to deal uniformly with instances
 * and types which must be instantiated by Guice.
 *
 * @author Tim Boudreau
 */
public abstract class TypeOrInstance<T> implements Function<Binder, Provider<? extends T>> {

    public static <T> TypeOrInstance<T> fromInstance(T obj) {
        return new ObjectTypeOrInstance<>(obj);
    }

    public static <T> TypeOrInstance<T> fromType(Class<? extends T> obj) {
        return new ClassTypeOrInstance<>(obj);
    }

    private static final class ClassTypeOrInstance<T> extends TypeOrInstance<T> {

        private final Class<? extends T> type;

        ClassTypeOrInstance(Class<? extends T> type) {
            this.type = type;
        }

        @Override
        public Provider<? extends T> apply(Binder t) {
            return t.getProvider(type);
        }
    }

    private static final class ObjectTypeOrInstance<T> extends TypeOrInstance<T> {

        private final T obj;

        ObjectTypeOrInstance(T obj) {
            this.obj = obj;
        }

        @Override
        public Provider<? extends T> apply(Binder t) {
            return Providers.of(obj);
        }
    }

}
