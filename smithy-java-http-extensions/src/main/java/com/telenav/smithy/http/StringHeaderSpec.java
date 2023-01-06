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
package com.telenav.smithy.http;

/**
 * Headers decoded as strings.
 *
 * @author Tim Boudreau
 */
final class StringHeaderSpec extends HeaderSpec.Base<CharSequence> {

    public StringHeaderSpec(CharSequence name) {
        super(name, CharSequence.class);
    }

    @Override
    public CharSequence toValue(CharSequence value) {
        return value;
    }

    @Override
    public CharSequence toCharSequence(CharSequence value) {
        return value;
    }

}
