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
package com.telenav.smithy.names;

import com.mastfrog.function.character.CharFilter;
import com.mastfrog.function.character.CharPredicates;
import com.mastfrog.util.strings.Escaper;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 *
 * @author Tim Boudreau
 */
public class JavaSymbolProvider /*implements SymbolProvider */{

    static final CharFilter JAVA_LEGAL = CharFilter.of(CharPredicates.JAVA_IDENTIFIER_START,
            CharPredicates.JAVA_IDENTIFIER_PART);
    static final JavaWords JAVA_RESERVED_WORDS = new JavaWords();

    public static final JavaSymbolProvider INSTANCE = new JavaSymbolProvider();

    /*
    public static SymbolProvider create() {
        JavaSymbolProvider syms = new JavaSymbolProvider();

        return ReservedWordSymbolProvider.builder().memberReservedWords(JAVA_RESERVED_WORDS)
                .nameReservedWords(JAVA_RESERVED_WORDS)
                .namespaceReservedWords(JAVA_RESERVED_WORDS)
                .symbolProvider(syms)
                .build();
    }
    */

    public static String fieldNameFor(ShapeId shape) {
        return INSTANCE.convertToJavaName(shape.getName());
    }

    public static String escape(String memberName) {
        return INSTANCE.convertToJavaName(memberName);
    }
/*
    @Override
    public Symbol toSymbol(Shape shape) {
        String name = shape.getId().getName();
        name = convertToJavaName(name);
        return Symbol.builder().namespace(shape.getId().getNamespace(), ".")
                .name(name).build();
    }
*/

    public String convertToJavaName(String name) {
        if (!JAVA_LEGAL.test(name)) {
            name = Escaper.JAVA_IDENTIFIER_CAMEL_CASE.escape(name);
        }
        String result = JAVA_RESERVED_WORDS.escape(name);
        switch (result) {
            case "Optional":
            case "StringBuilder":
            case "String":
            case "Integer":
            case "Long":
            case "Float":
            case "Double":
            case "Byte":
            case "Short":
            case "List":
            case "Set":
            case "LinkedHashSet":
            case "ArrayList":
            case "TreeSet":
            case "Map":
            case "LinkedHashMap":
            case "HashMap":
            case "JsonCreator":
            case "JsonValue":
            case "JsonProperty":
            case "Serializable":
            case "Supplier":
            case "Consumer":
            case "Function":
            case "Number":
                // Pending - plugins should be able to add to this list
                result = "_" + result;
        }
        return result;
    }

    /*
    @Override
    public String toMemberName(MemberShape shape) {
        return convertToJavaName(shape.getMemberName());
    }
    */

}
