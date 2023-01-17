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
package com.telenav.smithy.generators;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class DestinationImpl implements SmithyDestinations.Destination {

    private final Path path;
    private final GenerationTarget target;
    private final LanguageWithVersion language;

    DestinationImpl(Path path, GenerationTarget target, LanguageWithVersion language) {
        this.path = path;
        this.target = target;
        this.language = language;
    }

    public Path path() {
        return path;
    }

    public Optional<GenerationTarget> generationTarget() {
        return Optional.ofNullable(target);
    }

    public Optional<LanguageWithVersion> language() {
        return Optional.ofNullable(language);
    }

    @Override
    public String toString() {
        return "Destination(" + path + "," + target + "," + language + ")";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.path);
        hash = 97 * hash + Objects.hashCode(this.target);
        hash = 97 * hash + Objects.hashCode(this.language);
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
        final DestinationImpl other = (DestinationImpl) obj;
        if (!Objects.equals(this.path, other.path)) {
            return false;
        }
        if (!Objects.equals(this.target, other.target)) {
            return false;
        }
        return Objects.equals(this.language, other.language);
    }

}
