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
 * Set of shorthand character classes such as \s or \d that can be used in
 * regular expressions.
 *
 * @author Tim Boudreau
 */
enum ShorthandCharacterClassKinds {
    POSIXNamedSet,
    POSIXNegatedNamedSet,
    ControlChar,
    DecimalDigit,
    NotDecimalDigit,
    HorizontalWhiteSpace,
    NotHorizontalWhiteSpace,
    NotNewLine,
    CharWithProperty,
    CharWithoutProperty,
    NewLineSequence,
    WhiteSpace,
    NotWhiteSpace,
    VerticalWhiteSpace,
    NotVerticalWhiteSpace,
    WordChar,
    NotWordChar,
    Backslash;

    ShorthandCharacterClassKinds opposite() {
        switch (this) {
            case VerticalWhiteSpace:
                return NotVerticalWhiteSpace;
            case NotVerticalWhiteSpace:
                return VerticalWhiteSpace;
            case DecimalDigit:
                return NotDecimalDigit;
            case NotDecimalDigit:
                return DecimalDigit;
            case NotNewLine:
                return NewLineSequence;
            case NewLineSequence:
                return NotNewLine;
            case POSIXNegatedNamedSet:
                return POSIXNamedSet;
            case POSIXNamedSet:
                return POSIXNegatedNamedSet;
            case ControlChar:
                return WordChar;
            case CharWithProperty:
                return CharWithoutProperty;
            case CharWithoutProperty:
                return CharWithProperty;
            case WhiteSpace:
                return NotWhiteSpace;
            case NotWhiteSpace:
                return WhiteSpace;
            case WordChar:
                return NotWordChar;
            case Backslash:
                return WordChar;
            case HorizontalWhiteSpace:
                return NotHorizontalWhiteSpace;
            case NotHorizontalWhiteSpace:
                return HorizontalWhiteSpace;
            case NotWordChar:
                return WordChar;
            default:
                throw new AssertionError(this);
        }
    }

    static ShorthandCharacterClassKinds of(XegerParser.Shared_atomContext ctx) {
        switch (ctx.start.getType()) {
            case XegerLexer.POSIXNamedSet:
                return POSIXNamedSet;
            case XegerLexer.POSIXNegatedNamedSet:
                return POSIXNegatedNamedSet;
            case XegerLexer.ControlChar:
                return ControlChar;
            case XegerLexer.DecimalDigit:
                return DecimalDigit;
            case XegerLexer.NotDecimalDigit:
                return NotDecimalDigit;
            case XegerLexer.HorizontalWhiteSpace:
                return HorizontalWhiteSpace;
            case XegerLexer.NotHorizontalWhiteSpace:
                return NotHorizontalWhiteSpace;
            case XegerLexer.NotNewLine:
                return NotNewLine;
            case XegerLexer.CharWithoutProperty:
                return CharWithoutProperty;
            case XegerLexer.CharWithProperty:
                return CharWithProperty;
            case XegerLexer.NewLineSequence:
                return NewLineSequence;
            case XegerLexer.WhiteSpace:
                return WhiteSpace;
            case XegerLexer.NotWhiteSpace:
                return NotWhiteSpace;
            case XegerLexer.VerticalWhiteSpace:
                return VerticalWhiteSpace;
            case XegerLexer.NotVerticalWhiteSpace:
                return NotVerticalWhiteSpace;
            case XegerLexer.WordChar:
                return WordChar;
            case XegerLexer.NotWordChar:
                return NotWordChar;
            case XegerLexer.Backslash:
                return Backslash;
            default:
                System.out.println("WHAT IS THIS? '" + ctx.getText() + "' "
                        + XegerLexer.VOCABULARY.getSymbolicName(ctx.start.getType()));
                return null;
        }
    }

}
