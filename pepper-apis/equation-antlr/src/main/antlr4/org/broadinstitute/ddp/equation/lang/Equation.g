grammar equation;

file_ : expression* EOF;

expression
   :  expression  POW expression
   |  expression  (TIMES | DIV)  expression
   |  expression  (PLUS | MINUS) expression
   |  LPAREN expression RPAREN
   |  (PLUS | MINUS)* atom
   ;

atom
   : scientific
   | variable
   ;

scientific
   : SCIENTIFIC_NUMBER
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

//The NUMBER part gets its potential sign from "(PLUS | MINUS)* atom" in the expression rule
SCIENTIFIC_NUMBER
   : NUMBER (E SIGN? UNSIGNED_INTEGER)?
   ;

fragment NUMBER
   : ('0' .. '9') + ('.' ('0' .. '9') +)?
   ;

fragment UNSIGNED_INTEGER
   : ('0' .. '9')+
   ;


fragment E
   : 'E' | 'e'
   ;


fragment SIGN
   : ('+' | '-')
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