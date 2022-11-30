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
