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
 * Parser grammar for https://www.w3.org/TR/2004/REC-xmlschema-2-20041028/#regexs.
 *
 * This grammar is modified in following ways:
 * - charGroup definition inlines the charClassSub case
 *   This allows us to simplify processing, eliminating one level of nesting. It
 *   also makes this rule consistent with XSD 1.1 definition.
 */
/**
 * This is a slightly tweaked version of
 * https://github.com/bkiers/pcre-parser
 */
parser grammar XegerParser;

options { tokenVocab = XegerLexer; }

// Most single line comments above the lexer- and  parser rules 
// are copied from the official PCRE man pages (last updated: 
// 10 January 2012): http://www.pcre.org/pcre.txt
parse
    : alternation EOF;
// ALTERNATION
//
//         expr|expr|expr...
alternation
    : expr alternative*;

alternative
    : Pipe expr;

expr
    : element*;

element
    : atom quantifier?;
// QUANTIFIERS
//
//         ?           0 or 1, greedy
//         ?+          0 or 1, possessive
//         ??          0 or 1, lazy
//         *           0 or more, greedy
//         *+          0 or more, possessive
//         *?          0 or more, lazy
//         +           1 or more, greedy
//         ++          1 or more, possessive
//         +?          1 or more, lazy
//         {n}         exactly n
//         {n,m}       at least n, no more than m, greedy
//         {n,m}+      at least n, no more than m, possessive
//         {n,m}?      at least n, no more than m, lazy
//         {n,}        n or more, greedy
//         {n,}+       n or more, possessive
//         {n,}?       n or more, lazy
quantifier
    : QuestionMark ( quantifier_type| )
    | Plus ( quantifier_type| )
    | Star ( quantifier_type| )
    | OpenBrace count=number CloseBrace ( quantifier_type| )
    | OpenBrace start=number Comma CloseBrace ( quantifier_type| )
    | OpenBrace start=number Comma end=number CloseBrace ( quantifier_type| );

quantifier_type
    : plus=Plus
    | question=QuestionMark;
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
character_class
    : CharacterClassStart Caret CharacterClassEnd Hyphen cc_atom+ sub_character_class* CharacterClassEnd
    | CharacterClassStart Caret CharacterClassEnd cc_atom* sub_character_class* CharacterClassEnd
    | CharacterClassStart Caret cc_atom+ sub_character_class* CharacterClassEnd
    | CharacterClassStart CharacterClassEnd Hyphen cc_atom+ sub_character_class* CharacterClassEnd
    | CharacterClassStart CharacterClassEnd cc_atom* sub_character_class* CharacterClassEnd
    | CharacterClassStart cc_atom+ sub_character_class* CharacterClassEnd;

sub_character_class
    : DoubleAmpersand character_class;
// BACKREFERENCES
//
//         \n              reference by number (can be ambiguous)
//         \gn             reference by number
//         \g{n}           reference by number
//         \g{-n}          relative reference by number
//         \k<name>        reference by name (Perl)
//         \k'name'        reference by name (Perl)
//         \g{name}        reference by name (Perl)
//         \k{name}        reference by name (.NET)
//         (?P=name)       reference by name (Python)
backreference
    : backreference_or_octal
    | SubroutineOrNamedReferenceStartG number
    | SubroutineOrNamedReferenceStartG OpenBrace number CloseBrace
    | SubroutineOrNamedReferenceStartG OpenBrace Hyphen number CloseBrace
    | NamedReferenceStartK LessThan name GreaterThan
    | NamedReferenceStartK SingleQuote name SingleQuote
    | SubroutineOrNamedReferenceStartG OpenBrace name CloseBrace
    | NamedReferenceStartK OpenBrace name CloseBrace
    | OpenParen QuestionMark PUC Equals name CloseParen;

backreference_or_octal
    : octal_char
    | Backslash digit;
// CAPTURING
//
//         (...)           capturing group
//         (?<name>...)    named capturing group (Perl)
//         (?'name'...)    named capturing group (Perl)
//         (?P<name>...)   named capturing group (Python)
//         (?:...)         non-capturing group
//         (?|...)         non-capturing group; reset group numbers for
//                          capturing groups in each alternative
//
// ATOMIC GROUPS
//
//         (?>...)         atomic, non-capturing group
capture
    : OpenParen QuestionMark capture_name alternation CloseParen
    | OpenParen QuestionMark capture_name alternation CloseParen
    | OpenParen QuestionMark PUC capture_name alternation CloseParen
    | OpenParen alternation CloseParen;

capture_name
    : LessThan name GreaterThan
    | SingleQuote name SingleQuote;

non_capture
    : OpenParen QuestionMark Colon alternation CloseParen
    | OpenParen QuestionMark Pipe alternation CloseParen
    | OpenParen QuestionMark GreaterThan alternation CloseParen
    | OpenParen QuestionMark option_flags Colon alternation CloseParen;
// COMMENT
//
//         (?#....)        comment (not nestable)
comment
    : OpenParen QuestionMark Hash non_close_parens CloseParen;
// OPTION SETTING
//
//         (?i)            caseless
//         (?J)            allow duplicate names
//         (?m)            multiline
//         (?s)            single line (dotall)
//         (?U)            default ungreedy (lazy)
//         (?x)            extended (ignore white space)
//         (?-...)         unset option(s)
//
//       The following are recognized only at the start of a  pattern  or  after
//       one of the newline-setting options with similar syntax:
//
//         (*NO_START_OPT) no start-match optimization (PCRE_NO_START_OPTIMIZE)
//         (*UTF8)         set UTF-8 mode: 8-bit library (PCRE_UTF8)
//         (*UTF16)        set UTF-16 mode: 16-bit library (PCRE_UTF16)
//         (*UCP)          set PCRE_UCP (use Unicode properties for \d etc)
option
    : OpenParen QuestionMark option_flags Hyphen option_flags CloseParen
    | OpenParen QuestionMark option_flags CloseParen
    | OpenParen QuestionMark Hyphen option_flags CloseParen
    | OpenParen Star NUC OUC Underscore SUC TUC AUC RUC TUC Underscore OUC PUC TUC CloseParen
    | OpenParen Star UUC TUC FUC D8 CloseParen
    | OpenParen Star UUC TUC FUC D1 D6 CloseParen
    | OpenParen Star UUC CUC PUC CloseParen;

option_flags
    : option_flag+;

option_flag
    : ILC
    | JUC
    | MLC
    | SLC
    | UUC
    | XLC;
// LOOKAHEAD AND LOOKBEHIND ASSERTIONS
//
//         (?=...)         positive look ahead
//         (?!...)         negative look ahead
//         (?<=...)        positive look behind
//         (?<!...)        negative look behind
//
//       Each top-level branch of a look behind must be of a fixed length.
look_around
    : OpenParen QuestionMark Equals alternation CloseParen
    | OpenParen QuestionMark Exclamation alternation CloseParen
    | OpenParen QuestionMark LessThan Equals alternation CloseParen
    | OpenParen QuestionMark LessThan Exclamation alternation CloseParen;
// SUBROUTINE REFERENCES (POSSIBLY RECURSIVE)
//
//         (?R)            recurse whole pattern
//         (?n)            call subpattern by absolute number
//         (?+n)           call subpattern by relative number
//         (?-n)           call subpattern by relative number
//         (?&name)        call subpattern by name (Perl)
//         (?P>name)       call subpattern by name (Python)
//         \g<name>        call subpattern by name (Oniguruma)
//         \g'name'        call subpattern by name (Oniguruma)
//         \g<n>           call subpattern by absolute number (Oniguruma)
//         \g'n'           call subpattern by absolute number (Oniguruma)
//         \g<+n>          call subpattern by relative number (PCRE extension)
//         \g'+n'          call subpattern by relative number (PCRE extension)
//         \g<-n>          call subpattern by relative number (PCRE extension)
//         \g'-n'          call subpattern by relative number (PCRE extension)
subroutine_reference
    : OpenParen QuestionMark RUC CloseParen
    | OpenParen QuestionMark number CloseParen
    | OpenParen QuestionMark Plus number CloseParen
    | OpenParen QuestionMark Hyphen number CloseParen
    | OpenParen QuestionMark Ampersand name CloseParen
    | OpenParen QuestionMark PUC GreaterThan name CloseParen
    | SubroutineOrNamedReferenceStartG LessThan name GreaterThan
    | SubroutineOrNamedReferenceStartG SingleQuote name SingleQuote
    | SubroutineOrNamedReferenceStartG LessThan number GreaterThan
    | SubroutineOrNamedReferenceStartG SingleQuote number SingleQuote
    | SubroutineOrNamedReferenceStartG LessThan Plus number GreaterThan
    | SubroutineOrNamedReferenceStartG SingleQuote Plus number SingleQuote
    | SubroutineOrNamedReferenceStartG LessThan Hyphen number GreaterThan
    | SubroutineOrNamedReferenceStartG SingleQuote Hyphen number SingleQuote;
// CONDITIONAL PATTERNS
//
//         (?(condition)yes-pattern)
//         (?(condition)yes-pattern|no-pattern)
//
//         (?(n)...        absolute reference condition
//         (?(+n)...       relative reference condition
//         (?(-n)...       relative reference condition
//         (?(<name>)...   named reference condition (Perl)
//         (?('name')...   named reference condition (Perl)
//         (?(name)...     named reference condition (PCRE)
//         (?(R)...        overall recursion condition
//         (?(Rn)...       specific group recursion condition
//         (?(R&name)...   specific recursion condition
//         (?(DEFINE)...   define subpattern for reference
//         (?(assert)...   assertion condition
conditional
    : OpenParen QuestionMark OpenParen number CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen Plus number CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen Hyphen number CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen LessThan name GreaterThan CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen SingleQuote name SingleQuote CloseParen alternation ( Pipe alternation )?
    CloseParen
    | OpenParen QuestionMark OpenParen RUC number CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen RUC CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen RUC Ampersand name CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen DUC EUC FUC IUC NUC EUC CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen ALC SLC SLC ELC RLC TLC CloseParen alternation ( Pipe alternation )? CloseParen
    | OpenParen QuestionMark OpenParen name CloseParen alternation ( Pipe alternation )? CloseParen;
// BACKTRACKING CONTROL
//
//       The following act immediately they are reached:
//
//         (*ACCEPT)       force successful match
//         (*FAIL)         force backtrack; synonym (*F)
//         (*MARK:NAME)    set name to be passed back; synonym (*:NAME)
//
//       The  following  act only when a subsequent match failure causes a back-
//       track to reach them. They all force a match failure, but they differ in
//       what happens afterwards. Those that advance the start-of-match point do
//       so only if the pattern is not anchored.
//
//         (*COMMIT)       overall failure, no advance of starting point
//         (*PRUNE)        advance to next starting character
//         (*PRUNE:NAME)   equivalent to (*MARK:NAME)(*PRUNE)
//         (*SKIP)         advance to current matching position
//         (*SKIP:NAME)    advance to position corresponding to an earlier
//                         (*MARK:NAME); if not found, the (*SKIP) is ignored
//         (*THEN)         local failure, backtrack to next alternation
//         (*THEN:NAME)    equivalent to (*MARK:NAME)(*THEN)
backtrack_control
    : OpenParen Star AUC CUC CUC EUC PUC TUC CloseParen
    | OpenParen Star FUC ( AUC IUC LUC )? CloseParen
    | OpenParen Star ( MUC AUC RUC KUC )? Colon NUC AUC MUC EUC CloseParen
    | OpenParen Star CUC OUC MUC MUC IUC TUC CloseParen
    | OpenParen Star PUC RUC UUC NUC EUC CloseParen
    | OpenParen Star PUC RUC UUC NUC EUC Colon NUC AUC MUC EUC CloseParen
    | OpenParen Star SUC KUC IUC PUC CloseParen
    | OpenParen Star SUC KUC IUC PUC Colon NUC AUC MUC EUC CloseParen
    | OpenParen Star TUC HUC EUC NUC CloseParen
    | OpenParen Star TUC HUC EUC NUC Colon NUC AUC MUC EUC CloseParen;
// NEWLINE CONVENTIONS
//capture
//       These are recognized only at the very start of the pattern or  after  a
//       (*BSR_...), (*UTF8), (*UTF16) or (*UCP) option.
//
//         (*CR)           carriage return only
//         (*LF)           linefeed only
//         (*CRLF)         carriage return followed by linefeed
//         (*ANYCRLF)      all three of the above
//         (*ANY)          any Unicode newline sequence
//
// WHAT \R MATCHES
//
//       These  are  recognized only at the very start of the pattern or after a
//       (*...) option that sets the newline convention or a UTF or UCP mode.
//
//         (*BSR_ANYCRLF)  CR, LF, or CRLF
//         (*BSR_UNICODE)  any Unicode newline sequence
newline_convention
    : OpenParen Star CUC RUC CloseParen
    | OpenParen Star LUC FUC CloseParen
    | OpenParen Star CUC RUC LUC FUC CloseParen
    | OpenParen Star AUC NUC YUC CUC RUC LUC FUC CloseParen
    | OpenParen Star AUC NUC YUC CloseParen
    | OpenParen Star BUC SUC RUC Underscore AUC NUC YUC CUC RUC LUC FUC CloseParen
    | OpenParen Star BUC SUC RUC Underscore UUC NUC IUC CUC OUC DUC EUC CloseParen;
// CALLOUTS
//
//         (?C)      callout
//         (?Cn)     callout with data n
callout
    : OpenParen QuestionMark CUC CloseParen
    | OpenParen QuestionMark CUC number CloseParen;

atom
    : subroutine_reference
    | shared_atom
    | literal
    | character_class
    | capture
    | non_capture
    | comment
    | option
    | look_around
    | backreference
    | conditional
    | backtrack_control
    | newline_convention
    | callout
    | Dot
    | Caret
    | StartOfSubject
    | WordBoundary
    | NonWordBoundary
    | EndOfSubjectOrLine
    | EndOfSubjectOrLineEndOfSubject
    | EndOfSubject
    | PreviousMatchInSubject
    | ResetStartMatch
    | OneDataUnit
    | ExtendedUnicodeChar;

cc_atom
    : cc_literal Hyphen cc_literal
    | shared_atom
    | cc_literal
    | backreference_or_octal// only octal is valid in a cc
;

shared_atom
    : POSIXNamedSet
    | POSIXNegatedNamedSet
    | ControlChar
    | DecimalDigit
    | NotDecimalDigit
    | HorizontalWhiteSpace
    | NotHorizontalWhiteSpace
    | NotNewLine
    | CharWithProperty
    | CharWithoutProperty
    | NewLineSequence
    | WhiteSpace
    | NotWhiteSpace
    | VerticalWhiteSpace
    | NotVerticalWhiteSpace
    | WordChar
    | NotWordChar
    | Backslash .// will match "unfinished" escape sequences, like `\x`
;

literal
    : shared_literal
    | CharacterClassEnd;

cc_literal
    : shared_literal
    | Dot
    | CharacterClassStart
    | Caret
    | QuestionMark
    | Plus
    | Star
    | WordBoundary
    | EndOfSubjectOrLine
    | Pipe
    | OpenParen
    | CloseParen;

shared_literal
    : octal_char
    | letter
    | digit
    | BellChar
    | EscapeChar
    | FormFeed
    | NewLine
    | CarriageReturn
    | Tab
    | HexChar
    | Quoted
    | BlockQuoted
    | OpenBrace
    | CloseBrace
    | Comma
    | Hyphen
    | LessThan
    | GreaterThan
    | SingleQuote
    | Underscore
    | Colon
    | Hash
    | Equals
    | Exclamation
    | Ampersand
    | OtherChar;

number
    : digits;

octal_char
    : Backslash ( D0
                | D1
                | D2
                | D3 ) octal_digit octal_digit
    | Backslash octal_digit octal_digit;

octal_digit
    : D0
    | D1
    | D2
    | D3
    | D4
    | D5
    | D6
    | D7;

digits
    : digit+;

digit
    : D0
    | D1
    | D2
    | D3
    | D4
    | D5
    | D6
    | D7
    | D8
    | D9;

name
    : alpha_nums;

alpha_nums
    : ( letter
      | Underscore )( letter
                     | Underscore
                     | digit )*;

non_close_parens
    : non_close_paren+;

non_close_paren
    : ~ CloseParen;

letter
    : ALC
    | BLC
    | CLC
    | DLC
    | ELC
    | FLC
    | GLC
    | HLC
    | ILC
    | JLC
    | KLC
    | LLC
    | MLC
    | NLC
    | OLC
    | PLC
    | QLC
    | RLC
    | SLC
    | TLC
    | ULC
    | VLC
    | WLC
    | XLC
    | YLC
    | ZLC
    | AUC
    | BUC
    | CUC
    | DUC
    | EUC
    | FUC
    | GUC
    | HUC
    | IUC
    | JUC
    | KUC
    | LUC
    | MUC
    | NUC
    | OUC
    | PUC
    | QUC
    | RUC
    | SUC
    | TUC
    | UUC
    | VUC
    | WUC
    | XUC
    | YUC
    | ZUC;