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

import static com.telenav.smithy.rex.ElementKinds.CHAR_CLASS;
import static com.telenav.smithy.rex.RegexElement.escapeForDisplay;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.sort;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.Token;

/**
 * Represents things like \p{UpperCase} or \s which are special cases in regex
 * syntax.
 */
final class ShorthandCharacterClass implements RegexElement, Confoundable<ShorthandCharacterClass> {

    final ShorthandCharacterClassKinds tokenKind;
    private static final Pattern NAMED_SET = Pattern.compile("^\\[\\[:\\^?(\\w+)\\:]\\]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAR_PROP = Pattern.compile(".[pP]\\{(\\S+)\\}", Pattern.CASE_INSENSITIVE);
    private static final char[] WHITESPACE = " \t\n\r".toCharArray();
    private static final char[] SPACE_TAB = " \t".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    static final char[] WORD_CHARS
            = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] HORIZ_WHITESPACE = " \t\r".toCharArray();
    private static final char[] VERT_WHITESPACE = "\n\f".toCharArray();
    private static final char[] UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER_CASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toCharArray();
    private static final char[] HEX = "0123456789abcdefABCDEF".toCharArray();

    static {
        // Should not need sorting, but in case they are ever amended,
        // well, an endless loop in binary search is no fun.
        sort(WHITESPACE);
        sort(WORD_CHARS);
        sort(DIGITS);
        sort(HORIZ_WHITESPACE);
        sort(VERT_WHITESPACE);
        sort(UPPER_CASE);
        sort(LOWER_CASE);
        sort(PUNCTUATION);
        sort(HEX);
        sort(SPACE_TAB);
    }
    private final String tokenText;

    ShorthandCharacterClass(Token token, ShorthandCharacterClassKinds kinds) {
        this(token.getText(), kinds);
    }

    ShorthandCharacterClass(String tokenText, ShorthandCharacterClassKinds kinds) {
        this.tokenText = tokenText;
        this.tokenKind = kinds;
    }

    boolean matches(char c) {
        switch (tokenKind) {
            case WhiteSpace:
                return Character.isWhitespace(c);
            case NotWhiteSpace:
                return !Character.isWhitespace(c);
            case Backslash:
                return c == '\\';
            case CharWithProperty:
                switch (tokenText) {
                    case "Upper":
                        return Character.isUpperCase(c);
                    case "Lower":
                        return Character.isLowerCase(c);
                    case "Alpha":
                        return Character.isAlphabetic(c);
                    case "Digit":
                        return Character.isDigit(c);
                    case "Punct":
                        return Arrays.binarySearch(PUNCTUATION, c) >= 0;
                    case "XDigit":
                        return Arrays.binarySearch(HEX, c) >= 0;
                }
                break;
            case CharWithoutProperty:
                switch (tokenText) {
                    case "Upper":
                        return !Character.isUpperCase(c);
                    case "Lower":
                        return !Character.isLowerCase(c);
                    case "Alpha":
                        return !Character.isAlphabetic(c);
                    case "Digit":
                        return !Character.isDigit(c);
                    case "Punct":
                        return Arrays.binarySearch(PUNCTUATION, c) < 0;
                    case "XDigit":
                        return Arrays.binarySearch(HEX, c) < 0;
                }
            case DecimalDigit:
                return Character.isDigit(c);
            case NotDecimalDigit:
                return !Character.isDigit(c);
            case NewLineSequence:
                return c == '\r' || c == '\n';
            case NotNewLine:
                return c != '\n';
            case ControlChar:
                return c < 32;
            case WordChar:
                return Character.isAlphabetic(c);
            case NotWordChar:
                return !Character.isAlphabetic(c);
            case VerticalWhiteSpace:
                return c == '\n' || c == '\f';
            case HorizontalWhiteSpace:
                return c == ' ' || c == '\t';
            case NotHorizontalWhiteSpace:
                return c != ' ' && c != '\t';
            case NotVerticalWhiteSpace:
                return c != '\n' && c != '\f';
            case POSIXNamedSet:
                Matcher matcher2 = NAMED_SET.matcher(tokenText);
                matcher2.find();
                switch (matcher2.group(1)) {
                    case "digits":
                        return Character.isDigit(c);
                    case "blank":
                        return Character.isWhitespace(c);
                    case "xdigit":
                        return Arrays.binarySearch(HEX, c) >= 0;
                    case "ascii":
                        return c <= 127;
                    case "punct":
                        return Arrays.binarySearch(PUNCTUATION, c) >= 0;
                    default:
                        throw new IllegalArgumentException("Unsupported named posix cahracter class: " + matcher2.group(1));
                }
            case POSIXNegatedNamedSet:
                Matcher matcher3 = NAMED_SET.matcher(tokenText);
                matcher3.find();
                switch (matcher3.group(1)) {
                    case "digits":
                        return !Character.isDigit(c);
                    case "blank":
                        return !Character.isWhitespace(c);
                    case "xdigit":
                        return Arrays.binarySearch(HEX, c) < 0;
                    case "ascii":
                        return c > 127;
                    case "punct":
                        return Arrays.binarySearch(PUNCTUATION, c) < 0;
                    default:
                        throw new IllegalArgumentException("Unsupported named posix cahracter class: " + matcher3.group(1));
                }
        }
        return false;
    }

    @Override
    public String toString() {
        return tokenKind + "(" + escapeForDisplay(tokenText) + ")";
    }

    @Override
    public ElementKinds kind() {
        return CHAR_CLASS;
    }

    static List<Character> charsExcluding(char start, char end) {
        List<Character> chars = new ArrayList<>(127);
        for (int i = 32; i < 127; i++) {
            if (i < start) {
                chars.add((char) i);
            } else if (i > end) {
                chars.add((char) i);
            }
        }
        return chars;
    }

    static char charExcluding(char[] span, Random rnd) {
        return charExcluding(span[0], span[span.length - 1], rnd);
    }

    static char charExcluding(char start, char end, Random rnd) {
        // Could be much more clever and efficient here
        List<Character> chars = charsExcluding(start, end);
        assert !chars.isEmpty();
        return chars.get(rnd.nextInt(chars.size()));
    }

    @Override
    public void emit(StringBuilder into, Random rnd,
            IntFunction<CaptureGroup> backreferenceResolver) {
        switch (tokenKind) {
            case Backslash:
                into.append('\\');
                break;
            case ControlChar:
                char c = (char) (rnd.nextInt(31) + 1);
                into.append(c);
                break;
            case WhiteSpace:
                into.append(WHITESPACE[rnd.nextInt(WHITESPACE.length)]);
                break;
            case DecimalDigit:
                into.append(DIGITS[rnd.nextInt(DIGITS.length)]);
                break;
            case HorizontalWhiteSpace:
                into.append(rnd.nextBoolean() ? ' ' : '\t');
                break;
            case NewLineSequence:
                into.append('\n');
                break;
            case WordChar:
                into.append(WORD_CHARS[rnd.nextInt(WORD_CHARS.length)]);
                break;
            case NotNewLine:
                into.append(charExcluding('\n', '\n', rnd));
                break;
            case NotDecimalDigit:
                into.append(charExcluding('0', '9', rnd));
                break;
            case NotHorizontalWhiteSpace:
                into.append(charExcluding(HORIZ_WHITESPACE, rnd));
                break;
            case NotVerticalWhiteSpace:
                into.append(charExcluding(VERT_WHITESPACE, rnd));
            case NotWhiteSpace:
                into.append(charExcluding(WHITESPACE, rnd));
                break;
            case NotWordChar:
                into.append(charExcluding(WORD_CHARS, rnd));
                break;
            case VerticalWhiteSpace:
                into.append(VERT_WHITESPACE[rnd.nextInt(VERT_WHITESPACE.length)]);
                break;
            case POSIXNamedSet:
                Matcher matcher = NAMED_SET.matcher(tokenText);
                matcher.find();
                switch (matcher.group(1)) {
                    case "digits":
                        into.append(DIGITS[rnd.nextInt(DIGITS.length)]);
                        break;
                    case "blank":
                        into.append(SPACE_TAB[rnd.nextInt(SPACE_TAB.length)]);
                        break;
                    case "xdigit":
                        into.append(HEX[rnd.nextInt(HEX.length)]);
                        break;
                    case "ascii":
                        into.append((char) (rnd.nextInt(0x7F)));
                        break;
                    case "punct":
                        into.append(PUNCTUATION[rnd.nextInt(PUNCTUATION.length)]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported named posix character class: " + matcher.group(1));
                }
                break;
            case POSIXNegatedNamedSet:
                Matcher matcher2 = NAMED_SET.matcher(tokenText);
                matcher2.find();
                switch (matcher2.group(1)) {
                    case "digits":
                        into.append(charExcluding(DIGITS, rnd));
                        break;
                    case "blank":
                        into.append(charExcluding(SPACE_TAB, rnd));
                        break;
                    case "xdigit":
                        into.append(charExcluding(HEX, rnd));
                        break;
                    case "ascii":
                        into.append((char) (rnd.nextInt(127) + 127));
                        break;
                    case "punct":
                        into.append(charExcluding(PUNCTUATION, rnd));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported named posix cahracter class: " + matcher2.group(1));
                }
            case CharWithProperty:
                Matcher matcher3 = SHAR_PROP.matcher(tokenText);
                boolean res = matcher3.find();
                if (!res) {
                    throw new IllegalStateException("Failed to match '" + tokenText + "' with " + SHAR_PROP.pattern());
                }
                String g3 = matcher3.group(1);
                switch (g3) {
                    case "Upper":
                        into.append(UPPER_CASE[rnd.nextInt(UPPER_CASE.length)]);
                        break;
                    case "Lower":
                        into.append(LOWER_CASE[rnd.nextInt(LOWER_CASE.length)]);
                        break;
                    case "Alpha":
                        into.append(WORD_CHARS[rnd.nextInt(WORD_CHARS.length)]);
                        break;
                    case "Digit":
                        into.append(DIGITS[rnd.nextInt(DIGITS.length)]);
                        break;
                    case "Punct":
                        char cc = PUNCTUATION[rnd.nextInt(PUNCTUATION.length)];
                        into.append(cc);
                        break;
                    case "XDigit":
                        into.append(HEX[rnd.nextInt(HEX.length)]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported character class "
                                + g3);
                }
                break;
            case CharWithoutProperty:
                Matcher matcher4 = SHAR_PROP.matcher(tokenText);
                matcher4.find();
                String g4 = matcher4.group(1);
                switch (g4) {
                    case "Upper":
                        into.append(LOWER_CASE[rnd.nextInt(LOWER_CASE.length)]);
                        break;
                    case "Lower":
                        into.append(UPPER_CASE[rnd.nextInt(UPPER_CASE.length)]);
                        break;
                    case "Alpha":
                        into.append(charExcluding(WORD_CHARS, rnd));
                        break;
                    case "Digit":
                        into.append(charExcluding(DIGITS, rnd));
                        break;
                    case "Punct":
                        into.append(charExcluding(PUNCTUATION, rnd));
                        break;
                    case "XDigit":
                        into.append(charExcluding(HEX, rnd));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported character class "
                                + g4);
                }
                break;
        }
    }

    @Override
    public boolean canConfound() {
        return true;
    }

    @Override
    public Optional<ShorthandCharacterClass> confound() {
        return Optional.of(new ShorthandCharacterClass(tokenText, tokenKind.opposite()));
    }

}
