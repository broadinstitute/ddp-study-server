grammar Equation;

file_ : expression* EOF;

expression
   :  expression  POW expression
   |  expression  (TIMES | DIV)  expression
   |  expression  (PLUS | MINUS) expression
   |  LPAREN expression RPAREN
   |  (PLUS | MINUS)* atom
   ;

atom
   : number
   | variable
   ;

number
   : NUMBER
   ;

variable
   : VARIABLE
   ;


VARIABLE
   : VALID_ID_START VALID_ID_CHAR*
   ;


fragment VALID_ID_START
   : ('a' .. 'z') | ('A' .. 'Z') | '_'
   ;


fragment VALID_ID_CHAR
   : VALID_ID_START | ('0' .. '9')
   ;

NUMBER
   : ('0' .. '9') + ('.' ('0' .. '9') +)?
   ;


LPAREN
   : '('
   ;


RPAREN
   : ')'
   ;


PLUS
   : '+'
   ;


MINUS
   : '-'
   ;


TIMES
   : '*'
   ;


DIV
   : '/'
   ;


POINT
   : '.'
   ;


POW
   : '^'
   ;


WS
   : [ \r\n\t] + -> skip
   ;