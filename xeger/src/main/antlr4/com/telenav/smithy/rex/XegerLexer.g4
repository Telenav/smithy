
/*
 * [The "BSD license"]
 *  Copyright (c) 2019 PANTHEON.tech
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Lexer grammar for https://www.w3.org/TR/2004/REC-xmlschema-2-20041028/#regexs.
 *
 * This grammar is modified in following ways:
 * - we use lexer modes to disambiguate between Char, XmlChar and QuantExact
 * - we use separate lexer tokens to disambiguate positive and negative character groups
 * - XmlCharIncDash is removed in favor of DASH token, which is handled in parser
 */

/**
 * This is a slightly tweaked version of
 * https://github.com/bkiers/pcre-parser
 */

lexer grammar XegerLexer;

import xeger;

// QUOTING
//
//         \x         where x is non-alphanumeric is a literal x
//         \Q...\E    treat enclosed characters as literal
Quoted      : '\\' NonAlphaNumeric;
BlockQuoted : '\\Q' .*? '\\E';

// CHARACTERS
//
//         \a         alarm, that is, the BEL character (hex 07)
//         \cx        "control-x", where x is any ASCII character
//         \e         escape (hex 1B)
//         \f         form feed (hex 0C)
//         \n         newline (hex 0A)
//         \r         carriage return (hex 0D)
//         \t         tab (hex 09)
//         \ddd       character with octal code ddd, or backreference
//         \xhh       character with hex code hh
//         \x{hhh..}  character with hex code hhh..
BellChar       : '\\a';
ControlChar    : '\\c' ASCII?;
EscapeChar     : '\\e';
FormFeed       : '\\f';
NewLine        : '\\n';
CarriageReturn : '\\r';
Tab            : '\\t';
Backslash      : '\\';
HexChar        : '\\x' ( HexDigit HexDigit
                       | '{' HexDigit HexDigit HexDigit+ '}'
                       )
               ;

// CHARACTER TYPES
//
//         .          any character except newline;
//                      in dotall mode, any character whatsoever
//         \C         one data unit, even in UTF mode (best avoided)
//         \d         a decimal digit
//         \D         a character that is not a decimal digit
//         \h         a horizontal white space character
//         \H         a character that is not a horizontal white space character
//         \N         a character that is not a newline
//         \p{xx}     a character with the xx property
//         \P{xx}     a character without the xx property
//         \R         a newline sequence
//         \s         a white space character
//         \S         a character that is not a white space character
//         \v         a vertical white space character
//         \V         a character that is not a vertical white space character
//         \w         a "word" character
//         \W         a "non-word" character
//         \X         an extended Unicode sequence
//
//       In  PCRE,  by  default, \d, \D, \s, \S, \w, and \W recognize only ASCII
//       characters, even in a UTF mode. However, this can be changed by setting
//       the PCRE_UCP option.
Dot                     : '.';
OneDataUnit             : '\\C';
DecimalDigit            : '\\d';
NotDecimalDigit         : '\\D';
HorizontalWhiteSpace    : '\\h';
NotHorizontalWhiteSpace : '\\H';
NotNewLine              : '\\N';
CharWithProperty        : '\\p{' UnderscoreAlphaNumerics '}';
CharWithoutProperty     : '\\P{' UnderscoreAlphaNumerics '}';
NewLineSequence         : '\\R';
WhiteSpace              : '\\s';
NotWhiteSpace           : '\\S';
VerticalWhiteSpace      : '\\v';
NotVerticalWhiteSpace   : '\\V';
WordChar                : '\\w';
NotWordChar             : '\\W';
ExtendedUnicodeChar     : '\\X';

// CHARACTER CLASSES
//
//         [...]       positive character class
//         [^...]      negative character class
//         [x-y]       range (can be used for hex characters)
//         [[:xxx:]]   positive POSIX named set
//         [[:^xxx:]]  negative POSIX named set
//
//         alnum       alphanumeric
//         alpha       alphabetic
//         ascii       0-127
//         blank       space or tab
//         cntrl       control character
//         digit       decimal digit
//         graph       printing, excluding space
//         lower       lower case letter
//         print       printing, including space
//         punct       printing, excluding alphanumeric
//         space       white space
//         upper       upper case letter
//         word        same as \w
//         xdigit      hexadecimal digit
//
//       In PCRE, POSIX character set names recognize only ASCII  characters  by
//       default,  but  some  of them use Unicode properties if PCRE_UCP is set.
//       You can use \Q...\E inside a character class.
CharacterClassStart  : '[';
CharacterClassEnd    : ']';
Caret                : '^';
Hyphen               : '-';
POSIXNamedSet        : '[[:' AlphaNumerics ':]]';
POSIXNegatedNamedSet : '[[:^' AlphaNumerics ':]]';

QuestionMark : '?';
Plus         : '+';
Star         : '*';
OpenBrace    : '{';
CloseBrace   : '}';
Comma        : ',';

// ANCHORS AND SIMPLE ASSERTIONS
//
//         \b          word boundary
//         \B          not a word boundary
//         ^           start of subject
//                      also after internal newline in multiline mode
//         \A          start of subject
//         $           end of subject
//                      also before newline at end of subject
//                      also before internal newline in multiline mode
//         \Z          end of subject
//                      also before newline at end of subject
//         \z          end of subject
//         \G          first matching position in subject
WordBoundary                   : '\\b';
NonWordBoundary                : '\\B';
StartOfSubject                 : '\\A'; 
EndOfSubjectOrLine             : '$';
EndOfSubjectOrLineEndOfSubject : '\\Z'; 
EndOfSubject                   : '\\z'; 
PreviousMatchInSubject         : '\\G';

// MATCH POINT RESET
//
//         \K          reset start of match
ResetStartMatch : '\\K';

SubroutineOrNamedReferenceStartG : '\\g';
NamedReferenceStartK             : '\\k';

Pipe        : '|';
OpenParen   : '(';
CloseParen  : ')';
LessThan    : '<';
GreaterThan : '>';
SingleQuote : '\'';
Underscore  : '_';
Colon       : ':';
Hash        : '#';
Equals      : '=';
Exclamation : '!';
DoubleAmpersand   : '&&';
Ampersand   : '&';

ALC : 'a';
BLC : 'b';
CLC : 'c';
DLC : 'd';
ELC : 'e';
FLC : 'f';
GLC : 'g';
HLC : 'h';
ILC : 'i';
JLC : 'j';
KLC : 'k';
LLC : 'l';
MLC : 'm';
NLC : 'n';
OLC : 'o';
PLC : 'p';
QLC : 'q';
RLC : 'r';
SLC : 's';
TLC : 't';
ULC : 'u';
VLC : 'v';
WLC : 'w';
XLC : 'x';
YLC : 'y';
ZLC : 'z';

AUC : 'A';
BUC : 'B';
CUC : 'C';
DUC : 'D';
EUC : 'E';
FUC : 'F';
GUC : 'G';
HUC : 'H';
IUC : 'I';
JUC : 'J';
KUC : 'K';
LUC : 'L';
MUC : 'M';
NUC : 'N';
OUC : 'O';
PUC : 'P';
QUC : 'Q';
RUC : 'R';
SUC : 'S';
TUC : 'T';
UUC : 'U';
VUC : 'V';
WUC : 'W';
XUC : 'X';
YUC : 'Y';
ZUC : 'Z';

D1 : '1';
D2 : '2';
D3 : '3';
D4 : '4';
D5 : '5';
D6 : '6';
D7 : '7';
D8 : '8';
D9 : '9';
D0 : '0';

OtherChar : . ;

