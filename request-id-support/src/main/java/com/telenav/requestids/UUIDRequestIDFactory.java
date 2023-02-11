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
package com.telenav.requestids;

import java.util.Optional;
import java.util.UUID;

/**
 * A simple request ID factory returning the result of
 * <code>UUID.randomUUID()</code>.
 *
 * @author Tim Boudreau
 */
public class UUIDRequestIDFactory implements RequestIdFactory<UUID> {

    @Override
    public Optional<UUID> fromString(CharSequence txt) {
        if (txt == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(txt.toString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @Override
    public UUID nextId() {
        return UUID.randomUUID();
    }

    @Override
    public Class<UUID> idType() {
        return UUID.class;
    }

}
