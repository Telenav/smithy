lexer grammar smithy;

fragment NAMESPACE
    : 'namespace'
    | 'Namespace'
    | 'NameSpace'
    | 'NAMESPACE';

fragment VERSION_HEAD
    : DOLLARS VERSION COLON;

fragment VERSION
    : 'version'
    | 'Version'
    | 'VERSION';

fragment USE
    : 'use'
    | 'Use'
    | 'USE';

fragment COLON
    : ':';

fragment TRAIT_REFERENCE
    : '@' IDENTIFIER ( DOT IDENTIFIER )* ( MEMBER_IDENTIFIER| );

fragment DOLLARS_IDENTIFIER
    : DOLLARS IDENTIFIER;

// Pending:  This is a little too generous
fragment MEMBER_IDENTIFIER
    : '#' ( IDENTIFIER
          | NON_WHITESPACE );

fragment ESCAPED_QUOTE
    : '\\"';

fragment QUOTED_TEXT
    : QUOTE ( ESCAPED_QUOTE
            | . )*? QUOTE;

fragment METADATA
    : 'metadata'
    | 'Metadata'
    | 'MetaData'
    | 'METADATA';

fragment SERVICE
    : 'service'
    | 'Service'
    | 'SERVICE';

fragment RESOURCE
    : 'resource'
    | 'Resource'
    | 'RESOURCE';

fragment OPERATION
    : 'operation'
    | 'Operation'
    | 'OPERATION';

fragment UNION
    : 'union'
    | 'Union'
    | 'UNION';

fragment BLOB
    : 'Blob'
    | 'blob'
    | 'BLOB';

fragment BOOLEAN
    : 'Boolean'
    | 'boolean'
    | 'BOOLEAN';

fragment DOCUMENT
    : 'Document'
    | 'document'
    | 'DOCUMENT';

fragment STRING
    : 'String'
    | 'string'
    | 'STRING';

fragment BYTE
    : 'Byte'
    | 'BYTE'
    | 'byte';

fragment SHORT
    : 'Short'
    | 'short'
    | 'SHORT';

fragment INTEGER
    : 'Integer'
    | 'integer'
    | 'INTEGER';

fragment LONG
    : 'Long'
    | 'long'
    | 'LONG';

fragment FLOAT
    : 'Float'
    | 'float'
    | 'FLOAT';

fragment DOUBLE
    : 'Double'
    | 'double'
    | 'DOUBLED';

fragment ENUM
    : 'Enum'
    | 'enum'
    | 'ENUM'
    | 'intEnum';

fragment WITH
    : 'With'
    | 'with'
    | 'WITH';

fragment BIG_INTEGER
    : 'BigInteger'
    | 'Biginteger'
    | 'bigInteger'
    | 'BIGINTEGER'
    | 'biginteger';

fragment BIG_DECIMAL
    : 'BigDecimal'
    | 'Bigdecimal'
    | 'bigDecimal'
    | 'BIGDECIMAL'
    | 'bigdecimal';

fragment TIMESTAMP
    : 'TimeStamp'
    | 'Timestamp'
    | 'timestamp'
    | 'TIMESTAMP';

fragment FOR
    : 'for'
    | 'For'
    | 'FOR';

fragment LIST
    : 'list'
    | 'List'
    | 'LIST';

fragment SET
    : 'set'
    | 'Set'
    | 'SET';

fragment MEMBER
    : 'member'
    | 'Member'
    | 'MEMBER';

fragment MAP
    : 'map'
    | 'Map'
    | 'MAP';

fragment KEY
    : 'key'
    | 'Key'
    | 'KEY';

fragment VALUE
    : 'value'
    | 'Value'
    | 'VALUE';

fragment STRUCTURE
    : 'structure'
    | 'Structure'
    | 'STRUCTURE';

fragment APPLY
    : 'apply'
    | 'Apply'
    | 'APPLY';

fragment TEXT_BLOCK
    : TRIPLE_QUOTE (ESCAPED_QUOTE | .) *? TRIPLE_QUOTE;

fragment TRUE_LITERAL
    : 'true'
    | 'True'
    | 'TRUE';

fragment FALSE_LITERAL
    : 'false'
    | 'False'
    | 'FALSE';

fragment NULL_LITERAL
    : 'null'
    | 'Null'
    | 'NULL';

fragment EXPONENT
    : [Ee] ( MINUS
           | PLUS ) DIGITS;

fragment NUMBER
    : MINUS? DIGITS ( DOT DIGITS| )( EXPONENT| );

fragment DOLLARS
    : '$';

fragment POUND
    : '#';

fragment SLASH
    : '/';

fragment MINUS
    : '-';

fragment PLUS
    : '+';

fragment OPEN_PAREN
    : '(';

fragment CLOSE_PAREN
    : ')';

fragment OPEN_BRACE
    : '{';

fragment CLOSE_BRACE
    : '}';

fragment ASSIGN
    : ':='
    | ':'
    | '=';

fragment OPEN_ARRAY
    : '[';

fragment CLOSE_ARRAY
    : ']';

fragment NL
    : '\r'? '\n';

fragment LINE_COMMENT
    : OPEN_LINE_COMMENT .*? ( NL
                            | EOF );

fragment DOC_COMMENT
    : OPEN_DOC_COMMENT .*? ( NL
                           | EOF );

fragment OPEN_LINE_COMMENT
    : '//';

fragment OPEN_DOC_COMMENT
    : '///';

fragment OPEN_COMMENT
    : '/*';

fragment CLOSE_COMMENT
    : '*/';

fragment DIGIT
    : [0-9];

fragment IDENTIFIER
    : [a-zA-Z_] [a-zA-Z0-9_]*;

fragment TRIPLE_QUOTE
    : '"""';

fragment QUOTE
    : '"';

fragment ESCAPE
    : '\\';

fragment DIGITS
    : [0-9]+;

fragment DOT
    : '.';

fragment WHITESPACE
    : [ \r\n\t,];

fragment NON_WHITESPACE
    : ~[\r\n\t, ];