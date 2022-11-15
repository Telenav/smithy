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
lexer grammar SmithyModalLexer;

import smithy;

/*
A modal lexer for the Smithy IDL, which uses Antlr's lexer modes to deal with
things like keywords which can also be element or type names dependening on their
position.  It's a little repetititive, but handles a bunch of flavors of error
at the lexing level, and makes the parser considerably simpler.
*/

//tokens{Boolean,Identifier,Assign,Number,String,Null,OpenArray,CloseArray,OpenBrace,CloseBrace,OpenParen,CloseParen}
@members {
    private String smithyVersion = "2.0";
    public void emit(Token token) {
        switch(token.getType()) {
            case VersionString :
                String txt = token.getText();
                if (txt != null && txt.length() > 2) {
                    smithyVersion = txt.substring(1, txt.length()-1);
                }
        }
        super.emit(token);
    }

    private int nextNonWhitespace() {
        for (int i = 1;; i++) {
            int val = _input.LA(i);
            if (val == CharStream.EOF) {
                return CharStream.EOF;
            }
            char curr = (char) val;
            if (!Character.isWhitespace(curr)) {
                return curr;
            }
        }
    }

    boolean nextNonWhitespace(char c, boolean match) {
        int next = nextNonWhitespace();
        return match ? c == next : c != next;
    }

    private void popModeIfNextNotAssignment() {
        int next = nextNonWhitespace();
        if (next != '=' && next != ':') {
            popMode();
        }
    }

    private void popModeIfNextNotOpenParen(int newMode) {
        int next = nextNonWhitespace();
        if (next != '(') {
            popMode();
        } else {
            mode(newMode);
        }
    }


    private void popModeIfNextCloseBrace() {
        int next = nextNonWhitespace();
        if (next == ')') {
            popMode();
        }
    }

    private void safePopMode() {
        // We need to work on broken sources without throwing an exception
        if (_modeStack.size() > 0) {
            popMode();
        }
    }
}

//Boolean,Identifier,Assign,Number,String,Null,OpenArray,CloseArray,OpenBrace,CloseBrace
Number
    : {false}? 'nnn';

String
    : {false}? 'sss';

Null
    : {false}? 'nunu';

OpenArray
    : {false}? 'aaa';

CloseArray
    : {false}? 'aac';

OpenBrace
    : {false}? 'oob';

CloseBrace
    : {false}? 'ccb';

OpenParen
    : {false}? 'oop';

BooleanValue
    : {false}? 'oop';

MemberReference
    : {false}? 'mem';

Member
    : {false}? 'mbr';

Key
    : {false}? 'kkk';

Value
    : {false}? 'vvv';

Erroneous
    : {false}? 'vvv';

CloseParen
    : {false}? 'ccp';

TypeName
    : ( BOOLEAN
      | BIG_INTEGER
      | BIG_DECIMAL
      | BLOB
      | STRING
      | NUMBER
      | DOCUMENT
      | TIMESTAMP
      | BYTE
      | SHORT
      | FLOAT
      | INTEGER
      | LONG
      | DOUBLE ) -> pushMode ( SimpleShape );

Assign
    : ASSIGN;

Apply
    : APPLY -> pushMode ( ApplyMode );

Metadata
    : METADATA -> pushMode ( MetadataMode );

LineComment
    : LINE_COMMENT -> channel ( 2 );

DocComment
    : DOC_COMMENT -> channel ( 2 );

DollarsVersion
    : VERSION_HEAD -> pushMode ( VersionMode );

Namespace
    : NAMESPACE -> pushMode ( NamespaceMode );

Whitespace
    : WHITESPACE+ -> channel ( 1 );

Use
    : USE -> pushMode ( UseMode );

TraitIdentifier
    : TRAIT_REFERENCE -> pushMode ( ShapePrelude );

Structure
    : STRUCTURE -> pushMode ( ShapeMode );

Resource
    : RESOURCE -> pushMode ( ShapeMode );

Operation
    : OPERATION -> pushMode ( ShapeMode );

Service
    : SERVICE -> pushMode ( ShapeMode );

Union
    : UNION -> pushMode ( ShapeMode );

List
    : LIST -> pushMode ( CollectionMode );

Set
    : SET -> pushMode ( CollectionMode );

Map
    : MAP -> pushMode ( MapMode );

Enum
    : ENUM -> pushMode ( EnumMode );

Identifier
    : IDENTIFIER;


mode SimpleShape;

SimpleShapeIdentifier
    : IDENTIFIER ( DOT IDENTIFIER )* ( MEMBER_IDENTIFIER| ) -> type ( ShapeIdentifier ), popMode;

SimpleShapeLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

SimpleShapeDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

SimpleShapeWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );


mode ApplyMode;

ApplyIdentifier
    : IDENTIFIER ( DOLLARS_IDENTIFIER| ) -> type ( Identifier );

ApplyTraitIdentifier
    : TRAIT_REFERENCE { popModeIfNextNotOpenParen(TraitArgumentsMode); } -> type ( TraitIdentifier );

ApplyOpenParen
    : OPEN_PAREN -> type ( OpenParen ), mode ( TraitArgumentsMode );

ApplyOpenBlock
    : OPEN_BRACE -> type ( OpenBrace ), mode ( ApplyBlockMode );

ApplyLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ApplyDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

ApplyWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ApplyOther
    : . { safePopMode(); } -> type ( Erroneous );


mode ApplyBlockMode;

ApplyBlockTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier );

ApplyBlockOpenParen
    : OPEN_PAREN -> type ( OpenParen ), pushMode ( TraitArgumentsMode );

ApplyBlockTraitCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

ApplyBlockLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ApplyBlockDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

ApplyBlockWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );


mode ShapePrelude;

ShapePreludeTypeName
    : ( BOOLEAN
      | BIG_INTEGER
      | BLOB
      | BIG_DECIMAL
      | TIMESTAMP
      | STRING
      | NUMBER
      | DOCUMENT
      | BYTE
      | SHORT
      | INTEGER
      | FLOAT
      | LONG
      | DOUBLE ) -> type ( TypeName ), mode ( SimpleShape );

ShapePreludeOpenParen
    : OPEN_PAREN -> type ( OpenParen ), pushMode ( TraitArgumentsMode );

ShapePreludeStructure
    : STRUCTURE -> type ( Structure ), mode ( ShapeMode );

ShapePreludeResource
    : RESOURCE -> type ( Resource ), mode ( ShapeMode );

ShapePreludeOperation
    : OPERATION -> type ( Operation ), mode ( ShapeMode );

ShapePreludeService
    : SERVICE -> type ( Service ), mode ( ShapeMode );

ShapePreludeUnion
    : UNION -> type ( Union ), mode ( ShapeMode );

ShapePreludeSet
    : SET -> type ( List ), mode ( CollectionMode );

ShapePreludeList
    : LIST -> type ( Set ), mode ( CollectionMode );

ShapePreludeMap
    : MAP -> type ( Map ), mode ( MapMode );

ShapePreludeEnum
    : ENUM -> type ( Enum ), mode ( EnumMode );

ShapePreludeTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier );

ShapePreludeLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ShapePreludeDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

ShapePreludeWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );


mode UseMode;

UsePath
    : IDENTIFIER ( DOT IDENTIFIER )* ( MEMBER_IDENTIFIER| ) -> popMode;

UseWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

UseLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

UseDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

UseOther
    : . { safePopMode(); } -> type ( Erroneous );


mode TraitArgumentsMode;

TraitArgCloseParen
    : CLOSE_PAREN -> type ( CloseParen ), popMode;

TraitArgNumber
    : NUMBER -> type ( Number );

TraitArgOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( MetadataArrayMode );

TraitArgOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), pushMode ( MetadataObjectMode );

TraitArgOpenParen
    : OPEN_PAREN -> type ( OpenParen );

TraitArgAssign
    : ASSIGN -> type ( Assign );

TraitArgString
    : ( QUOTED_TEXT
      | TEXT_BLOCK ) -> type ( String );

TraitArgIdentifier
    : IDENTIFIER -> type ( Identifier );

TraitArgWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

TraitArgLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

TraitArgDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

TraitArgOther
    : . { safePopMode(); } -> type ( Erroneous );


mode EnumMode;

EnumOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( EnumBodyMode );

EnumCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

EnumIdentifier
    : IDENTIFIER -> mode ( EnumModePostIdentifier );

EnumWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

EnumLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

EnumDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

EnumModeOther
    : . { safePopMode(); } -> type ( Erroneous );


mode EnumBodyMode;

EnumBodyCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

EnumTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), pushMode ( BeforeTraitArguments );

EnumMemberName
    : IDENTIFIER;

EnumAssign
    : ASSIGN -> type ( Assign ), pushMode ( ValueForKeyMode );

EnumBodyWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

EnumBodyLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

EnumBodyDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

EnumBodyModeOther
    : . { safePopMode(); } -> type ( Erroneous );


mode MapMode;

MapOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( MapBodyMode );

MapCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

MapIdentifier
    : IDENTIFIER -> mode ( MapModePostIdentifier );

MapWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

MapLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

MapDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );


mode MapBodyMode;

MapBodyTrait
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), pushMode ( BeforeTraitArguments );

MapBodyKey
    : KEY -> type ( Key ), pushMode ( ValueForKeyMode );

MapBodyValue
    : VALUE -> type ( Value ), pushMode ( ValueForKeyMode );

MapBodyCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

MapBodyWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

MapBodyLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

MapBodyDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

MapBodyOther
    : . { safePopMode();} -> type ( Erroneous );


mode CollectionMode;

CollectionOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( CollectionBodyMode );

CollectionCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

CollectionIdentifier
    : IDENTIFIER -> mode ( CollectionModePostIdentifier );

CollectionMemberIdentifier : DOLLARS_IDENTIFIER -> type(MemberReference);

CollectionWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

CollectionLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

CollectionDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

CollectionOther
    : . { safePopMode();} -> type ( Erroneous );


mode CollectionBodyMode;

CollectionBodyTrait
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), pushMode ( BeforeTraitArguments );

CollectionBodyMember
    : MEMBER -> type ( Member ), pushMode ( ValueForKeyMode );

CollectionBodyCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

CollectionBodyWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

CollectionBodyLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

CollectionBodyDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

CollectionBodyOther
    : . { safePopMode();} -> type ( Erroneous );


mode ShapeMode;

ShapeIdentifier
    : ( IDENTIFIER
      | QUOTED_TEXT ) -> mode ( ShapeModePostIdentifier );

ShapeModeWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ShapeModeLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ShapeModeDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode ShapeModePostIdentifier;

With
    : WITH;

For
    : FOR;

ShapeModePostIdentifierOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( PostIdentifierArray );

ShapeModePostIdentifierIdentifier
    : IDENTIFIER -> type ( Identifier );

ShapeModePostIdentifierOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( ShapeBody );

ShapeModePostIdentifierWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ShapeModePostIdentifierLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ShapeModePostIdentifierDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode MapModePostIdentifier;

MapWith
    : WITH -> type ( With );

MapFor
    : FOR -> type ( For );

MapModePostIdentifierOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( PostIdentifierArray );

MapModePostIdentifierIdentifier
    : IDENTIFIER -> type ( Identifier );

MapModePostIdentifierOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( MapBodyMode );

MapModePostIdentifierWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

MapModePostIdentifierLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

MapModePostIdentifierDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode CollectionModePostIdentifier;

CollectionModeMapWith
    : WITH -> type ( With );

CollectionModeMapFor
    : FOR -> type ( For );

CollectionModePostIdentifierOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( PostIdentifierArray );

CollectionModePostIdentifierIdentifier
    : IDENTIFIER -> type ( Identifier );

CollectionModePostIdentifierOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( CollectionBodyMode );

CollectionModePostIdentifierWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

CollectionModePostIdentifierLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

CollectionModePostIdentifierDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode EnumModePostIdentifier;

EnumModeMapWith
    : WITH -> type ( With );

EnumModeMapFor
    : FOR -> type ( For );

EnumModePostIdentifierOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( PostIdentifierArray );

EnumModePostIdentifierIdentifier
    : IDENTIFIER -> type ( Identifier );

EnumModePostIdentifierOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( EnumBodyMode );

EnumModePostIdentifierWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

EnumModePostIdentifierLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

EnumModePostIdentifierDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode PostIdentifierArray;

ShapeModePostIdentifierArrayIdentifier
    : IDENTIFIER -> type ( Identifier );

ShapeModePostIdentifierArrayCloseArray
    : CLOSE_ARRAY -> type ( CloseArray ), popMode;

ShapeModePostIdentifierArrayWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ShapeModePostIdentifierArrayLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ShapeModePostIdentifierArrayDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode BeforeTraitArguments;

BeforeTraitArgumentsTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier );

BeforeTraitArgumentsOpenParen
    : OPEN_PAREN -> type ( OpenParen ), mode ( TraitArgumentsMode );

BeforeTraitArgumentsWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

BeforeTraitArgumentsWith
    : WITH -> type ( With ), mode ( ShapeModePostIdentifier );

BeforeTraitArgumentsLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

BeforeTraitArgumentsDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

BeforeTraitArgumentsOther
    : . -> popMode, more;


mode ShapeBody;

ShapeBodyCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

ShapeBodyTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), pushMode ( ValueForKeyPrelude );

ShapeBodyReference
    : DOLLARS_IDENTIFIER -> type ( MemberReference );

ShapeMemberName
    : IDENTIFIER -> pushMode ( ValueForKeyMode );

ShapeBodyModeWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ShapeBodyModeLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ShapeBodyModeDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode ValueForKeyPrelude;

ValueForKeyPreludeReference
    : DOLLARS_IDENTIFIER -> type ( MemberReference ), popMode;

ValueForKeyOpenParen
    : OPEN_PAREN -> type ( OpenParen ), pushMode ( TraitArgumentsMode );

ValueForKeyPreludeAssign
    : ASSIGN -> type ( Assign ), mode ( ValueForKeyMode );

ValueForKeyPreludeTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), pushMode ( BeforeTraitArguments );

ValueForKeyPreludeWith
    : WITH -> type ( With ), mode ( ShapeModePostIdentifier );

ValueForKeyPreludeWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ValueForKeyPreludeLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ValueForKeyPreludeDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

ValueForKeyPreludeIdentifier
    : ( IDENTIFIER
      | QUOTED_TEXT ) -> type ( ShapeMemberName );

//type ( TraitIdentifier ),
ValueForKeyPreludeOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), pushMode ( MetadataObjectMode );

ValueForKeyPreludeOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( MetadataArrayMode );

ValueForKeyPreludeOther
    : . -> mode ( ValueForKeyMode ), more;


mode ValueForKeyMode;

ValueForKeyAssign
    : ASSIGN -> type ( Assign );

// Inline struct
ValueForKeyWith
    : WITH -> type ( With ), mode ( InlineShapePrelude );

ValueForKeyTraitIdentifier
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), mode ( InlineShapePrelude );

ValueForKeyOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( MetadataObjectMode );

ValueForKeyOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), mode ( MetadataArrayMode );

ValueForKeyCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

ValueForKeyNumber
    : NUMBER {popModeIfNextNotAssignment();} -> type ( Number );

ValueForKeyString
    : ( QUOTED_TEXT
      | TEXT_BLOCK ) {popModeIfNextNotAssignment();} -> type ( String );

ValueForKeyNull
    : NULL_LITERAL {popModeIfNextNotAssignment();} -> type ( Null );

ValueForKeyBoolean
    : ( TRUE_LITERAL
      | FALSE_LITERAL ) {popModeIfNextNotAssignment();} -> type ( BooleanValue );

ValueForKeySimpleTypeName
    : ( BOOLEAN
      | BIG_INTEGER
      | BIG_DECIMAL
      | BLOB
      | STRING
      | NUMBER
      | DOCUMENT
      | TIMESTAMP
      | BYTE
      | SHORT
      | INTEGER
      | FLOAT
      | LONG
      | DOUBLE ) {popModeIfNextNotAssignment();} -> type ( TypeName );

ValueForKeyIdentifier
    : IDENTIFIER ( DOT IDENTIFIER )* ( MEMBER_IDENTIFIER| ) {popModeIfNextNotAssignment();} -> type ( Identifier );

ValueForKeyWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

ValueForKeyLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

ValueForKeyDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

ValueForKeyOther
    : . -> type ( Erroneous ), popMode;


mode InlineShapePrelude;

InlineTraitWith
    : WITH -> type ( With );

InlineTraitArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( MetadataArrayMode );

InlineTraitOpenParen
    : OPEN_PAREN -> type ( OpenParen ), pushMode ( TraitArgumentsMode );

InlineTraitReference
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), pushMode ( BeforeTraitArguments );

InlineOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( ShapeBody );

InlineTraitIdentifier
    : IDENTIFIER -> type ( TraitIdentifier );

InlineWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );

InlineLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

InlineDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );

InlineOther
    : . -> type ( Erroneous ), popMode;


mode NamespaceMode;

NamespacePath
    : IDENTIFIER ( DOT IDENTIFIER )* -> popMode;

NamespaceIdentifierWhitespace
    : WHITESPACE+ -> channel ( 2 ), type ( Whitespace );


mode MetadataMode;

MetadataIdentifier
    : IDENTIFIER ( DOT IDENTIFIER )* -> type ( Identifier );

MetadataQuotedIdentifier
    : QUOTED_TEXT -> type ( Identifier );

MetadataEquals
    : ASSIGN -> mode ( MetadataValue ), type ( Assign );

MetadataLineComment
    : LINE_COMMENT -> channel ( 2 ), type ( LineComment );

MetadataDocComment
    : DOC_COMMENT -> channel ( 2 ), type ( DocComment );

MetadataWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 2 );


mode MetadataValue;

MetadataBoolean
    : ( TRUE_LITERAL
      | FALSE_LITERAL ) -> type ( BooleanValue ), popMode;

//    | FALSE_LITERAL {popModeWith ( Boolean );};
MetadataNull
    : NULL_LITERAL -> type ( Null ), popMode;

MetadataNumber
    : NUMBER -> type ( Number ), popMode;

MetadataString
    : ( QUOTED_TEXT
      | TEXT_BLOCK ) -> type ( String ), popMode;

MetadataOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), mode ( MetadataArrayMode );

MetadataOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), mode ( MetadataObjectMode );

MetadataValueIdentifier
    : IDENTIFIER ( DOT IDENTIFIER )* -> type ( Identifier ), popMode;

MetadataValueWhitespace
    : WHITESPACE+ -> channel ( 1 ), type ( Whitespace );

MetadataValueLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

MetadataValueDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode MetadataArrayMode;

MetadataCloseArray
    : CLOSE_ARRAY -> type ( CloseArray ), popMode;

MetadataArrayString
    : ( QUOTED_TEXT
      | TEXT_BLOCK ) -> type ( String );

MetadataArrayOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), pushMode ( MetadataObjectMode );

MetadataArrayOpenArray
    : OPEN_ARRAY -> type ( CloseBrace ), pushMode ( MetadataArrayMode );

MetadataArrayNumber
    : NUMBER -> type ( Number );

MetadataArrayBoolean
    : ( TRUE_LITERAL
      | FALSE_LITERAL ) -> type ( BooleanValue );

MetadataArrayNull
    : NULL_LITERAL -> type ( Null );

MetadataArrayIdentifier
    : IDENTIFIER ( DOT IDENTIFIER )* -> type ( Identifier );

MetadataArrayWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 1 );

MetadataArrayLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

MetadataArrayDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode MetadataObjectMode;

MetadataObjectCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

MetadataAssign
    : ASSIGN -> type ( Assign );

MetadataObjectString
    : ( QUOTED_TEXT
      | TEXT_BLOCK ) -> type ( String );

MetadataObjectTraitIdentifer
    : TRAIT_REFERENCE -> type ( TraitIdentifier ), mode ( BeforeTraitArguments );

MetadataObjectTraitWith
    : WITH -> type ( With ), mode ( ShapeModePostIdentifier );

MetadataObjectNumber
    : NUMBER -> type ( Number );

MetadataObjectOpenBrace
    : OPEN_BRACE -> type ( OpenBrace ), pushMode ( MetadataObjectMode );

MetadataObjectOpenArray
    : OPEN_ARRAY -> type ( OpenArray ), pushMode ( MetadataArrayMode );

MetadataObjectNull
    : NULL_LITERAL -> type ( Null );

MetadataObjectBoolean
    : ( TRUE_LITERAL
      | FALSE_LITERAL ) -> type ( BooleanValue );

MetadataCloseBrace
    : CLOSE_BRACE -> type ( CloseBrace ), popMode;

MetadataObjectIdentifier
    : IDENTIFIER -> type ( Identifier );

MetadataObjectWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 1 );

MetadataObjectLineComment
    : LINE_COMMENT+ -> channel ( 2 ), type ( LineComment );

MetadataObjectDocComment
    : DOC_COMMENT+ -> channel ( 2 ), type ( DocComment );


mode VersionMode;

VersionString
    : QUOTED_TEXT -> popMode;

VersionLineComment
    : LINE_COMMENT+ -> type ( LineComment ), channel ( 2 );

VersionDocComment
    : DOC_COMMENT+ -> type ( DocComment ), channel ( 2 );

VersionWhitespace
    : WHITESPACE+ -> type ( Whitespace ), channel ( 1 );