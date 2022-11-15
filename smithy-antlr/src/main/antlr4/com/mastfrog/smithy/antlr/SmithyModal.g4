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
grammar SmithyModal;

options { tokenVocab = SmithyModalLexer; }

compilationUnit
    : ( versionDeclaration| )(( metadataSection| )( namespaceStatement| )
                              | ( namespaceStatement| )( metadataSection| )) shapeSection EOF;

namespaceStatement
    : Namespace namespace;

namespace
    : NamespacePath;

metadataSection
    : Metadata metadataKeyValuePair+;

metadataKeyValuePair
    : metadataIdentifier Assign nodeValue;

metadataIdentifier
    : Identifier;

versionDeclaration
    : DollarsVersion ver=VersionString;

shapeSection
    : useStatement* ( shapeStatement
                    | applyStatement )+;

shapeStatement
    : traitStatement* ( simpleShapeStatement
                      | structureDeclaration
                      | listDeclaration
                      | enumDeclaration
                      | setDeclaration
                      | serviceDeclaration
                      | resourceDeclaration
                      | operationDeclaration
                      | mapDeclaration
                      | unionDeclaration );

simpleShapeStatement
    : id=ShapeIdentifier ((( Assign| ) nodeValue ( assignDefault| )) | )
    | simpleType id=ShapeIdentifier ((( Assign| ) nodeValue ( assignDefault| )) | );

useStatement
    : Use UsePath;

structureDeclaration
    : Structure id=ShapeIdentifier ( withClause| )( structureForClause| ) structureMembers;

structureMembers
    : OpenBrace (( structureMember ( structureMember )* ) | ) CloseBrace;

structureMember
    : traitStatement* explicitStructureMember ( assignDefault| );

enumDeclaration
    : traitStatement* Enum id=EnumIdentifier ( withClause| ) OpenBrace ( traitStatement* EnumMemberName ( assignDefault| ))*
    CloseBrace;

explicitStructureMember
    : identifier Assign shapeId
    | MemberReference;

structureForClause
    : For id=shapeId;

serviceDeclaration
    : Service id=ShapeIdentifier serviceObject;

resourceDeclaration
    : Resource id=ShapeIdentifier nodeObject;

operationDeclaration
    : Operation id=ShapeIdentifier nodeObject;

listDeclaration
    : List id=CollectionIdentifier listMembers;

listMembers
    : OpenBrace ( listMember
                | CollectionMemberIdentifier ) CloseBrace;

listMember
    : traitStatement* explicitListMember;

explicitListMember
    : Member Assign shapeId;

setDeclaration
    : Set id=CollectionIdentifier setMembers;

setMembers
    : OpenBrace ( setMember
                | CollectionMemberIdentifier ) CloseBrace;

setMember
    : traitStatement* explicitSetMember;

explicitSetMember
    : Member Assign shapeId;

mapDeclaration
    : traitStatement* Map id=MapIdentifier mapMembers;

mapMembers
    : OpenBrace mapKey mapValue CloseBrace;

mapKey
    : traitStatement* explicitMapKey;

explicitMapKey
    : Key Assign shapeId;

mapValue
    : traitStatement* explicitMapValue;

explicitMapValue
    : Value Assign shapeId;

unionDeclaration
    : Union id=ShapeIdentifier unionMembers;

unionMembers
    : OpenBrace unionMember* CloseBrace;

unionMember
    : traitStatement* explicitUnionMember;

explicitUnionMember
    : id=Identifier Assign shapeId;

shapeIdMember
    : MemberReference;

traitStatement
    : id=TraitIdentifier ( traitBody| );

traitBody
    : OpenParen ( traitBodyValue| ) CloseParen;

traitBodyValue
    : traitStructure
    | nodeValue
    | nodeArray;

traitStructure
    : traitStructureKvp traitStructureKvp*;

traitStructureKvp
    : key=nodeObjectKey Assign val=nodeValue;

assignDefault
    : Assign nodeValue;

nodeObjectKey
    : String
    | ShapeMemberName
    | Identifier;

absoluteRootShapeId
    : ns=namespace id=MemberReference;

rootShapeId
    : absoluteRootShapeId
    | identifier;

shapeId
    : simpleType
    | ShapeMemberName
    | String
    | OpenArray shapeId CloseArray
    | rootShapeId ( OpenArray shapeIdMember CloseArray| );

identifier
    : ShapeMemberName
    | Identifier;

nodeObjectKvp
    : traitStatement* nodeObjectKey Assign ( withClause| ) nodeValue ( assignDefault| );

nodeValue
    : nodeArray
    | nodeObject
    | number
    | nodeStringValue
    | nodeKeywords
    | simpleType
    | emptyObject
    | inlineStructure;

inlineStructure
    : traitStatement* ( withClause| )( structureForClause| ) structureMembers;

emptyObject
    : OpenBrace CloseBrace;

withClause
    : With OpenArray Identifier* CloseArray;

nodeStringValue
    : shapeId
    | text;

nodeObject
    : OpenBrace nodeObjectKvp* CloseBrace;

serviceObject
    : OpenBrace nodeObjectKvp* CloseBrace;

applyStatement
    : Apply id=Identifier ( TraitIdentifier
                          | applyBlock )( traitBody| );

applyBlock
    : OpenBrace traitStatement* CloseBrace;

nodeKeywords
    : trueLiteral
    | falseLiteral
    | nullLiteral;

trueLiteral
    : BooleanValue;

falseLiteral
    : BooleanValue;

nullLiteral
    : Null;

number
    : Number;

text
    : String;

nodeArray
    : OpenArray nodeValue* CloseArray;

simpleType
    : TypeName;

literal
    : String
    | BooleanValue
    | Null
    | Number
    | Identifier;