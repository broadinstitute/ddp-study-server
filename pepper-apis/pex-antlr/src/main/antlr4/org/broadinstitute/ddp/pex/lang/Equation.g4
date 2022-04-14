grammar Equation;

expression
   :  expression op='^' expression # Power
   |  expression op='*' expression # Multiplication
   |  expression op='/' expression # Division
   |  expression op='+' expression # Addition
   |  expression op='-' expression # Subtraction
   |  '(' expression ')'           # Parentheses
   |  VARIABLE                     # Variable
   |  NUMBER                       # Number
   ;

VARIABLE: VALID_ID_START VALID_ID_CHAR*;
fragment VALID_ID_START: ('a' .. 'z') | ('A' .. 'Z') | '_';
fragment VALID_ID_CHAR: VALID_ID_START | ('0' .. '9');

NUMBER: ('-')? ('0' .. '9')+ ('.' ('0' .. '9')+)?;

WS: [ \r\n\t] + -> skip;
