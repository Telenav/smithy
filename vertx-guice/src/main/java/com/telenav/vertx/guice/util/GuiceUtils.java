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

import com.google.inject.Key;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Tools for creating types.
 *
 * @author Tim Boudreau
 */
public final class GuiceUtils {

    private GuiceUtils() {
        throw new AssertionError();
    }

    /**
     * Construct a Guice key for a parameterized type where the types are
     * determined dynamically.
     *
     * @param <T> The type, e.g. Consumer
     * @param <P> A type parameter, e.g. String
     * @param topType The type, e.g. Consumer
     * @param typeParam A type parameter, e.g. String
     * @return Given Consumer and String, a
     * <code>Key&lt;Consumer&lt;String&gt;&gt;</code>
     */
    @SuppressWarnings("unchecked")
    public static <T, P> Key<T> keyForGenericType(Class<? super T> topType, Class<? super P> typeParam) {
        return (Key<T>) Key.get(genericType(topType, typeParam));
    }

    @SuppressWarnings("unchecked")
    public static <T, P, P1> Key<T> keyForGenericType(Class<? super T> topType, Class<? super P> typeParam,
            Class<? super P1> secondTypeParam) {
        return (Key<T>) Key.get(genericType(topType, typeParam, secondTypeParam));
    }

    private static <T, R> ParameterizedType genericType(Class<R> topType, Class<T> typeParameter) {
        return new OneGenericFakeType<>(topType, typeParameter);
    }

    private static <T, R, B> ParameterizedType genericType(Class<R> topType, Class<T> firstTypeParameter,
            Class<B> secondTypeParameter) {
        return new TwoGenericFakeType<>(topType, firstTypeParameter, secondTypeParameter);
    }

    private static final class OneGenericFakeType<R, T> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;

        public OneGenericFakeType(Class<R> topType, Class<T> genericType) {
            this.topType = topType;
            this.genericType = genericType;
        }

        @Override
        public String getTypeName() {
            return topType.getName();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{genericType};
        }

        @Override
        public Type getRawType() {
            return topType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + '>';
        }
    }

    private static final class TwoGenericFakeType<R, T, U> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;
        private final Class<U> otherGenericType;

        public TwoGenericFakeType(Class<R> topType, Class<T> genericType, Class<U> otherGenericType) {
            this.topType = topType;
            this.genericType = genericType;
            this.otherGenericType = otherGenericType;
        }

        @Override
        public String getTypeName() {
            return topType.getName();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{genericType, otherGenericType};
        }

        @Override
        public Type getRawType() {
            return topType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + "," + otherGenericType.getSimpleName()
                    + '>';
        }
    }

}
