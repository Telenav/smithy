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
package com.telenav.smithy.rex;

/**
 * Flavors of RegexElement.
 *
 * @author Tim Boudreau
 */
 enum ElementKinds {
    REGEX, 
    ALTERNATION, 
    CHAR_CLASS, 
    CHAR_RANGE, 
    CHAR_LITERAL, 
    STRING_LITERAL, 
    CAPTURE_GROUP, 
    BACKREFERENCE,
    ANY,
    EMPTY
}
