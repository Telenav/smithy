/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
