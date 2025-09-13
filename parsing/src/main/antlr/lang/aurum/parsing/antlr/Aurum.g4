grammar Aurum;

@header {
package lang.aurum.parsing.antlr;
}

program
    : (importStmt | packageDecl | declaration)* EOF
    ;

importStmt
    : 'import' qualifiedName ('as' Identifier)?
    ;

packageDecl
    : 'package' qualifiedName
    ;

declaration
    : typeDef
    | extensionDecl
    | funcDecl
    | classDecl
    | interfaceDecl
    | decoratorDecl
    | varDecl
    | operatorDecl
    ;

extensionDecl
    : decorator* modifier* 'extend' typeExpr '{' extensionMember* '}'
    ;

extensionMember
    : funcDecl
    | operatorDecl
    | varDecl
    ;

typeDef
    : 'type' Identifier '=' typeExpr
    ;


interfaceDecl
    : decorator* modifier* 'interface' Identifier ('[' genericTypeList ']')? '{' interfaceMember* '}'
    ;

interfaceMember
    : funcSign
    ;

funcSign
    : decorator* modifier* 'fn' Identifier ('[' genericTypeList ']')? ('(' paramList? ')' | EmptyBraces) returnType?
    ;

modifier
    : 'public' | 'private' | 'protected' | 'static' | 'final' | 'abstract'
    ;


funcDecl
    : funcSign block
    ;

decorator
    : '@' Identifier ('(' argList? ')')?
    ;


classDecl
    : decorator* modifier* 'class' Identifier ('[' genericTypeList ']')? ('(' paramList? ')')? (':' qualifiedNameList)? '{' memberDecl* '}'
    ;

decoratorDecl
    : decorator* modifier* '@class' Identifier (EmptyBraces | ('(' paramList? ')'))? '{' decoratorMember* '}'
    ;

decoratorMember
    : funcDecl
    ;

qualifiedNameList
    : qualifiedName (',' qualifiedName)*
    ;

memberDecl
    : varDecl
    | funcDecl
    | operatorDecl
    ;

varDecl
    : decorator* modifier* ('let' | 'var') Identifier (':' typeExpr)? ('=' expression)? # singleDecl
    | decorator* modifier* ('let' | 'var') Identifier (':' typeExpr)? (',' Identifier (':' typeExpr)?)+ ('=' expression)? # unpackDecl
    | decorator* modifier* ('let' | 'var') Identifier (':' typeExpr)? ('=' expression)? (',' Identifier (':' typeExpr)? ('=' expression)?)+ # multiDecl
    ;

operatorDecl
    : decorator* 'operator' OperatorSymbol ('[' genericTypeList ']')? '(' paramList? ')' ':' typeExpr block
    ;

paramList
    : param (',' param)*
    ;

param
    : Identifier ':' typeExpr
    ;

returnType
    : ':' typeExpr
    ;

block
    : '{' statement* '}'
    | statement
    ;


statement
    : declaration
    | assignmentExpression
    | expressionStatement
    | returnStatement
    | matchStatement
    | ifStatement
    | loopStatement
    | whileStatement
    | forStatement
    | breakStatement
    | continueStatement
    ;


ifStatement
    : 'if' expression block ('elif' expression block)* ('else' block)?
    ;


matchStatement
    : 'match' expression '{' matchCaseStatement+ '}'
    ;

matchCaseStatement
    : pattern '=>' block
    ;

assignmentExpression
    : expression OperatorSymbol?'=' expression
    ;

expressionStatement
    : expression
    ;

returnStatement
    : 'return' expression?
    ;

pattern
    : (expression
    | typePattern)
    ( 'when' expression )?
    ;

typePattern
    : Identifier typeExpr
    ;




loopStatement
    : 'loop' block
    ;

whileStatement
    : 'while' expression block
    ;

forStatement
    : 'for' (Identifier (',' Identifier)*?) 'in' expression block
    ;

breakStatement
    : 'break' expression?
    ;

continueStatement
    : 'continue'
    ;


typeExpr
    : unionType typeSuffix*
    ;

typeSuffix
    : '[]'
    | '...'
    ;

unionType
    : intersectionType ('|' intersectionType)*
    ;

intersectionType
    : genericType ('&' genericType)*
    ;

genericTypeList
    : genericType (',' genericType)*?
    ;

genericType
    : primaryType ('[' typeArgList ']')?  # regularType
    | '?'                                 # wildcardType
    ;

typeArgList
    : typeExpr (',' typeExpr)*
    ;

primaryType
    : qualifiedName
    | '(' typeExpr ')'
    | lambdaType
    ;

lambdaType
    : '(' typeList? ')' '->' typeExpr
    ;

typeList
    : typeExpr (',' typeExpr)*
    ;


expression
    : ifExpr
    | matchExpr
    | loopStatement
    | whileStatement
    | lambdaExpr
    ;


ifExpr
    : 'if' expression expressionBlock ('elif' expression expressionBlock)* ('else' expressionBlock)?
    ;


matchExpr
    : 'match' expression '{' matchCase+ '}'
    ;

matchCase
    : pattern '=>' (expression | block)
    ;


expressionBlock
    : '{' statement* expression '}'
    | expression
    ;

lambdaExpr
    : '(' lambdaParamList? ')' '=>' expression    # lambda
    | '(' lambdaParamList? ')' '=>' statement     # lambda
    | '(' lambdaParamList? ')' '=>' block         # lambda
    | postfixExpr (binaryOp postfixExpr)*         # binary
    ;

lambdaParamList
    : lambdaParam (',' lambdaParam)*
    ;

lambdaParam
    : Identifier (':' typeExpr)?
    ;

postfixExpr
    : primaryExpr postfixPart*
    ;

postfixPart
    : '.' Identifier                           # memberAccess
    | '(' argList? ')'                         # functionCall
    | '[' expression ']'                       # indexAccess
    | EmptyBraces                              # emptyCall
    ;

primaryExpr
    : Identifier                              # identifier
    | Literal                                 # literal
    | '(' expression ')'                      # paren
    | '[' (expression (',' expression)*)? ']' # array
    ;

argList
    : expression (',' expression)*
    ;

binaryOp
    : OperatorSymbol
    ;

qualifiedName
    : Identifier ('.' Identifier)*
    ;


EmptyBraces
    : '()'
    ;

OperatorSymbol
    : '+' | '-' | '*' | '/' | '%' | '**'
    | '==' | '!=' | '<' | '>' | '<=' | '>='
    | 'as' | '[]' | EmptyBraces
    | [+\-*/\\%$!?~;^<>]+
    ;

Literal
    : IntegerLiteral
    | FloatLiteral
    | StringLiteral
    | BooleanLiteral
    | '_'
    | 'null'
    ;

IntegerLiteral
    : Digit+
    ;

FloatLiteral
    : Digit+ '.' Digit+
    ;

StringLiteral
    : '"' (~["\\] | '\\' .)* '"'
    | '\'' (~["\\] | '\\' .)* '\''
    ;

BooleanLiteral
    : 'true' | 'false'
    ;

Identifier
    : Letter (Letter | Digit | '_')*
    ;

fragment Digit
    : [0-9]
    ;

fragment Letter
    : [a-zA-Z]
    ;


WS
    : [ \t\r\n]+ -> skip
    ;

COMMENT
    : '#' ~[\r\n]* -> skip
    ;

MULTILINE_COMMENT
    : '#*' .*? '*#' -> skip
    ;
