grammar Aurum;

@header {
package lang.aurum.parsing.antlr;
}

program
    : ENDLINE* packageDecl? (importStmt | declaration)*?
    ;

importStmt
    : 'import' qualifiedName ('.' OperatorSymbol)? ('as' Identifier)? ENDLINE+
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
    : decorator* modifier* ':' typeExpr ('[' genericTypeList ']')? '{' ENDLINE* extensionMember* '}'
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
    : decorator* modifier* 'interface' Identifier ('[' genericTypeList ']')? (':' typeExprList)? '{' ENDLINE* (funcSign ENDLINE+)* '}'
    ;

funcSign
    : decorator* modifier* 'fn' Identifier ('[' genericTypeList ']')? ('(' paramList? ')' | EmptyBraces) returnType?
    ;

constructorDecl
    : decorator* modifier* 'init' ('[' genericTypeList ']')? ('(' paramList? ')' | EmptyBraces) block
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
    : decorator* modifier* 'class' Identifier ('[' genericTypeList ']')? ('(' defaultConstructorParamList? ')')? (':' typeExprList)? '{' ENDLINE* (memberDecl ENDLINE+)* '}'
    ;

typeExprList
    : typeExpr (',' typeExpr)*
    ;

defaultConstructorParamList
    : defaultConstructorParam (',' defaultConstructorParam)*
    ;

defaultConstructorParam
    : decorator* modifier* varDefKW? Identifier ':' typeExpr
    ;

decoratorDecl
    : decorator* modifier* '@class' Identifier (EmptyBraces | ('(' paramList? ')'))? '{' ENDLINE* (funcDecl ENDLINE+)* '}'
    ;

memberDecl
    : varDecl
    | funcDecl
    | constructorDecl
    | operatorDecl
    | funcSign
    ;

varDecl
    : decorator* modifier* varDefKW Identifier (':' typeExpr)? ('=' expression)? # singleDecl
    | decorator* modifier* varDefKW varId (',' varId)+ ('=' expression)? # unpackDecl
    | decorator* modifier* varDefKW varIdAssignment (',' varIdAssignment)+ # multiDecl
    ;

varIdAssignment
    : varId ('=' expression)?
    ;

varId
    : Identifier (':' typeExpr)?
    ;

operatorDecl
    : decorator* modifier* 'operator' OperatorSymbol ('[' genericTypeList ']')? '(' paramList? ')' returnType block
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
    : qualifiedName OperatorSymbol?'=' expression # varAssignment
    | expression indexAccessPart OperatorSymbol?'=' expression # arrayAssignment
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
    : Identifier (':' typeExpr)
    ;

primaryType
    : qualifiedName
    | '(' typeExpr ')'
    | lambdaType
    ;

lambdaType
    : (('(' typeList? ')') | EmptyBraces) '->' typeExpr
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
    : (('(' lambdaParamList? ')') | EmptyBraces) '=>' expression      # lambda
    | (('(' lambdaParamList? ')') | EmptyBraces) '=>' expressionBlock # lambda
    | (('(' lambdaParamList? ')') | EmptyBraces) '=>' statement       # lambda
    | (('(' lambdaParamList? ')') | EmptyBraces) '=>' block           # lambda
    | postfixExpr (binaryOp postfixExpr)*                             # binary
    ;

lambdaParamList
    : lambdaParam (',' lambdaParam)*
    ;

lambdaParam
    : (name=Identifier | name='_') (':' typeExpr)?
    ;

postfixExpr
    : prefixExpr postfixPart?  # postfixWithPref
    | postfixExpr postfixPart  # recursivePostfix
    ;

postfixPart
    : '.' Identifier                           # memberAccess
    | 'as' typeExpr                            # cast
    | '(' argList? ')'                         # functionCall
    | indexAccessPart                          # indexAccess
    | EmptyBraces                              # functionCall
    | OperatorSymbol                           # operator
    ;

indexAccessPart
    : '[' argList ']'
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

varDefKW
    : KWlet | KWvar
    ;

EmptyBraces
    : '()'
    ;

OperatorSymbol
    : '+' | '-' | '*' | '/' | '%' | '**'
    | '==' | '!=' | '<' | '>' | '<=' | '>='
    | 'as' | '[]' | EmptyBraces
    | [+\-*/\\%$!?~^<>]+
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
KWvar:        'var'      ;
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
    : [a-zA-Z_$]
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