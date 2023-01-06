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
package com.telenav.smithy.utils.path;

import java.util.Collections;
import java.util.Map;

/**
 * Contains either a valid URI path extracted from an HttpTrait, or a a valid
 * and correct regular expression representing the path.
 *
 * @author Tim Boudreau
 */
public final class PathInfo {

    final boolean isRegex;
    final String text;
    private final Map<Integer, String> inputMemberForRegexElement;

    PathInfo(boolean isRegex, String text, Map<Integer, String> inputMemberForRegexElement) {
        this.isRegex = isRegex;
        this.text = text;
        this.inputMemberForRegexElement = Collections.unmodifiableMap(inputMemberForRegexElement);
    }

    public boolean isRegex() {
        return isRegex;
    }

    public String text() {
        return text;
    }

    public Map<Integer, String> inputMemberForRegexElement() {
        return inputMemberForRegexElement;
    }

}
