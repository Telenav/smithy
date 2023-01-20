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
import java.util.ArrayList;
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
    private static final Pattern NAMED_SET = Pattern.compile("^\\[\\[:\\^?(\\w+)\\:]\\]$");
    private static final Pattern SHAR_PROP = Pattern.compile("^\\[pP]\\{([A-Za-z_]+)\\}$");
    private static final char[] WHITESPACE = " \t\n\r".toCharArray();
    private static final char[] SPACE_TAB = " \t".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] WORD_CHARS
            = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] HORIZ_WHITESPACE = " \t\r".toCharArray();
    private static final char[] VERT_WHITESPACE = "\n\f".toCharArray();
    private static final char[] UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER_CASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] PUNCTUATON = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toCharArray();
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
        sort(PUNCTUATON);
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

    @Override
    public String toString() {
        return tokenKind + "(" + tokenText + ")";
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
                    default:
                        throw new Error("");
                }
            case CharWithProperty:
                Matcher matcher3 = SHAR_PROP.matcher(tokenText);
                matcher3.find();
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
                    case "Print":
                        into.append(PUNCTUATON[rnd.nextInt(PUNCTUATON.length)]);
                        break;
                    case "XDigit":
                        into.append(HEX[rnd.nextInt(HEX.length)]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported character class "
                                + g3);
                }
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
