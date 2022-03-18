grammar Equation;

file_ : expression* EOF;

expression
   :  expression  (PLUS | MINUS | TIMES | DIV | POW)  expression
   |  LPAREN expression RPAREN
   |  variable
   |  number
   ;

variable: VARIABLE;
number: NUMBER;

VARIABLE: VALID_ID_START VALID_ID_CHAR*;
fragment VALID_ID_START: ('a' .. 'z') | ('A' .. 'Z') | '_';
fragment VALID_ID_CHAR: VALID_ID_START | ('0' .. '9');

NUMBER: (MINUS)? ('0' .. '9')+ ('.' ('0' .. '9')+)?;

LPAREN: '(';
RPAREN: ')';

PLUS: '+';
MINUS: '-';
TIMES: '*';
DIV: '/';
POW: '^';

WS: [ \r\n\t] + -> skip;
