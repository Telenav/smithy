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
package com.telenav.smithy.generators;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public final class SettingsKey<T> {

    private final Class<? super T> type;
    private final String name;

    SettingsKey(Class<? super T> type, String name) {
        this.type = type;
        this.name = name;
    }

    public static <T> SettingsKey<T> key(Class<? super T> type) {
        return new SettingsKey<>(notNull("type", type), type.getName());
    }

    public static <T> SettingsKey<T> key(Class<? super T> type, String name) {
        return new SettingsKey<T>(notNull("type", type),
                notNull("name", name));
    }

    @SuppressWarnings(value = "unchecked")
    T cast(Object value) {
        return (T) type.cast(value);
    }

    public String toString() {
        String n = type.getName();
        if (name.equals(n)) {
            return n;
        }
        return name + "(" + type.getName() + ")";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.type);
        hash = 83 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || SettingsKey.class != obj.getClass()) {
            return false;
        }
        final SettingsKey<?> other = (SettingsKey<?>) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.type, other.type);
    }

}
