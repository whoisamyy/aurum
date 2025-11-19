grammar Aurum;

@header {
package lang.aurum.parsing.antlr;
}

program
    : packageDecl (importStmt | declaration)*?
    ;

importStmt
    : 'import' qualifiedName ('as' Identifier)? ENDLINE+
    ;

packageDecl
    : 'package' qualifiedName ENDLINE+
    ;

declaration
    : (typeDef
    | extensionDecl
    | funcDecl
    | classDecl
    | interfaceDecl
    | decoratorDecl
    | varDecl
    | operatorDecl)
    ENDLINE*
    ;

extensionDecl
    : decorator* modifier* ':' typeExpr '{' ENDLINE* extensionMember* '}'
    ;

extensionMember
    : (funcDecl
    | operatorDecl
    | varDecl)
    ENDLINE+
    ;

typeDef
    : 'type' Identifier '=' typeExpr
    ;


interfaceDecl
    : decorator* modifier* 'interface' Identifier ('[' genericTypeList ']')? (':' qualifiedNameList)? '{' ENDLINE* (funcSign ENDLINE+)* '}'
    ;

funcSign
    : decorator* modifier* 'fn' Identifier ('[' genericTypeList ']')? ('(' paramList? ')' | EmptyBraces) returnType?
    ;

modifier
    : 'public' # publicMod
    | 'private' # privateMod
    | 'protected' # protectedMod
    | 'static' # staticMod
    | 'final' # finalMod
    | 'abstract' # abstractMod
    ;


funcDecl
    : funcSign block
    ;

decorator
    : '@' Identifier ('(' argList? ')')?
    ;

classDecl
    : decorator* modifier* 'class' Identifier ('[' genericTypeList ']')? ('(' paramList? ')')? (':' qualifiedNameList)? '{' ENDLINE* (memberDecl ENDLINE+)* '}'
    ;

decoratorDecl
    : decorator* modifier* '@class' Identifier (EmptyBraces | ('(' paramList? ')'))? '{' ENDLINE* (funcDecl ENDLINE+)* '}'
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
    : decorator* modifier* 'let' Identifier (':' typeExpr)? ('=' expression)? # singleDecl
    | decorator* modifier* 'let' varId (',' varId)+ ('=' expression)? # unpackDecl
    | decorator* modifier* 'let' varIdAssignment (',' varIdAssignment)+ # multiDecl
    ;

varIdAssignment
    : varId ('=' expression)?
    ;

varId
    : Identifier (':' typeExpr)?
    ;

operatorDecl
    : decorator* modifier* 'operator' OperatorSymbol ('[' genericTypeList ']')? '(' paramList? ')' ':' typeExpr block
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
    : '{' ENDLINE* statement* '}'
    | statement
    ;


statement
    : declaration # declarationStmt
    | assignmentExpression ENDLINE+ # assignmentExpressionStmt
    | returnStatement ENDLINE+ # returnStatementStmt
    | matchStatement ENDLINE+ # matchStatementStmt
    | ifStatement ENDLINE+ # ifStatementStmt
    | loopStatement ENDLINE+ # loopStatementStmt
    | whileStatement ENDLINE+ # whileStatementStmt
    | forStatement ENDLINE+ # forStatementStmt
    | expression ENDLINE+ # expressionStmt
    | breakStatement ENDLINE+ # breakStatementStmt
    | continueStatement ENDLINE+ # continueStatementStmt
    ;


ifStatement
    : 'if' expression block ('elif' expression block)* ('else' block)?
    ;

matchStatement
    : 'match' expression '{' ENDLINE* (matchCaseStatement ENDLINE+)+ '}'
    ;

matchCaseStatement
    : pattern '=>' block              # patternCase
    | pattern '=>' expressionBlock    # patternCase
    | 'default' '=>' block            # defaultCase
    | 'default' '=>' expressionBlock  # defaultCase
    ;

pattern
    : (expression
    | typePattern)
    ( 'when' expression )?
    ;

typePattern
    : Identifier ':' typeExpr
    ;

assignmentExpression
    : qualifiedName OperatorSymbol?'=' expression
    ;

returnStatement
    : 'return' expression?
    ;

loopStatement
    : 'loop' block
    ;

whileStatement
    : 'while' expression block
    ;

forStatement
    : 'for' (varId (',' varId)*?) 'in' expression block
    ;

breakStatement
    : 'break' expression?
    ;

continueStatement
    : 'continue'
    ;


typeExpr
    : unionType typeSuffix?
    ;

typeSuffix
    : '[]'+
    | '...'
    ;

unionType
    : intersectionType ('|' intersectionType)*?
    ;

intersectionType
    : genericType ('&' genericType)*?
    ;

genericTypeList
    : genericType (',' genericType)*?
    ;

genericType
    : typeParam                           # parameterType
    | primaryType ('[' typeArgList ']')?  # regularType
    | '?'                                 # wildcardType
    ;

typeArgList
    : typeExpr (',' typeExpr)*?
    ;

typeParam
    : Identifier (':' typeExpr (',' typeExpr)*?)
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
    : ifExpr # ifExprExpr
    | matchStatement # matchExpr
    | loopStatement # loopStatementExpr
    | whileStatement # whileStatementExpr
    | lambdaExpr # lambdaExprExpr
    ;


ifExpr
    : 'if' expression expressionBlock ('elif' expression expressionBlock)* ('else' expressionBlock)?
    ;

expressionBlock
    : '{' ENDLINE* statement* expression '}'
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
    : prefixExpr postfixPart?  # postfixWithPref
    | postfixExpr postfixPart  # recursivePostfix
    ;

postfixPart
    : '.' Identifier                           # memberAccess
    | 'as' typeExpr                            # cast
    | '(' argList? ')'                         # functionCall
    | '[' argList ']'                          # indexAccess
    | EmptyBraces                              # functionCall
    | OperatorSymbol                           # operator
    ;

prefixExpr
    : OperatorSymbol? primaryExpr
    ;

primaryExpr
    : Identifier                              # identifier
    | Literal                                 # literal
    | '(' expression ')'                      # paren
    | '[' (expression (',' expression)*)? ']' # array
    ;

argList
    : expression (',' expression)*?
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
    : Digit+ [lLfFdD]?
    ;

FloatLiteral
    : Digit+ '.' Digit+ [fFdD]?
    ;

StringLiteral
    : '"' (~["\\] | '\\' .)* '"'
    | '\'' (~["\\] | '\\' .)* '\''
    ;

BooleanLiteral
    : 'true' | 'false'
    ;

KWfn:         'fn'       ;
KWclass:      'class'    ;
KWwhen:       'when'     ;
KWpackage:    'package'  ;
KWinterface:  'interface';
KWtype:       'type'     ;
KWlet:        'let'      ;
KWmatch:      'match'    ;
KWdefault:    'default'  ;
KWoperator:   'operator' ;
KWreturn:     'return'   ;
KWif:         'if'       ;
KWelse:       'else'     ;
KWelif:       'elif'     ;
KWcontinue:   'continue' ;
KWbreak:      'break'    ;
KWfor:        'for'      ;
KWwhile:      'while'    ;
KWloop:       'loop'     ;
KWextend:     'extend'   ;
KWfinal:      'final'    ;
KWabstract:   'abstract' ;
KWstatic:     'static'   ;
KWprivate:    'private'  ;
KWpublic:     'public'   ;
KWimport:     'import'   ;

Identifier
    : Letter (Letter | Digit | '_')*
    ;

fragment Digit
    : [0-9]
    ;

fragment Letter
    : [a-zA-Z]
    ;

ENDLINE
    : [;\r\n]+
    ;

WS
    : [ \t]+ -> skip
    ;

COMMENT
    : '#' ~[\r\n]* -> skip
    ;

MULTILINE_COMMENT
    : '#*' .*? '*#' -> skip
    ;