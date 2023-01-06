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
package com.telenav.vertx.guice.scope;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Needed to allow creating Guice Key instances against Optional with a type
 * parameter passed in as a Class object.
 *
 * @param <T> A type
 */
class FakeOptionalType<T> implements ParameterizedType {

    private final Class<T> typeParam;

    FakeOptionalType(Class<T> typeParam) {
        this.typeParam = typeParam;
    }

    public String toString() {
        return getTypeName();
    }

    @Override
    public String getTypeName() {
        return Optional.class.getName() + "<" + typeParam.getName() + ">";
    }

    @Override
    public Type[] getActualTypeArguments() {
        return new Type[]{typeParam};
    }

    @Override
    public Type getRawType() {
        return Optional.class;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }

}
