// PEX - The Pepper Expression Language
grammar Pex;

// Starting rule for parsing
pex : expr ;

expr : '!' expr          # NotExpr
     | expr '&&' expr    # AndExpr
     | expr '||' expr    # OrExpr
     | '(' expr ')'      # GroupExpr
     | query             # QueryExpr
     | BOOL              # BoolLiteralExpr
     ;

query : 'user' '.' study '.' form '.' question '.' 'answers' '.' predicate                   # DefaultLatestAnswerQuery
      | 'user' '.' study '.' form '.' question '.' questionPredicate                         # QuestionQuery
      | 'user' '.' study '.' form '.' formPredicate                                          # FormQuery
      | 'user' '.' study '.' studyPredicate                                                  # StudyQuery
      | 'user' '.' study '.' form '.' instance '.' question '.' 'answers' '.' predicate      # AnswerQuery
      ;

study : 'studies' '[' STR ']' ;
form : 'forms' '[' STR ']' ;
instance : 'instances' '[' INSTANCE_TYPE ']' ;
question : 'questions' '[' STR ']' ;

// Predicate functions that operates on a set/collection of things or a single piece of data
predicate : 'hasTrue()'     # HasTruePredicate
          | 'hasFalse()'    # HasFalsePredicate
          | 'hasText()'     # HasTextPredicate
          | 'hasOption' '(' STR ')'      # HasOptionPredicate
          | 'hasAnyOption' '(' STR ( ',' STR )* ')'       # HasAnyOptionPredicate
          | 'hasDate()'     # HasDatePredicate
          | 'ageAtLeast' '(' INT ',' TIMEUNIT ')'   # AgeAtLeastPredicate
          | 'value' '(' ')' COMPARE_OPERATOR INT    # NumComparePredicate
          ;

// Form predicate functions that operate on a single piece of data
formPredicate : 'isStatus' '(' STR ( ',' STR )* ')'  # IsStatusPredicate
              | 'hasInstance()'         # HasInstancePredicate
              ;

// A predicate evaluating to true if a user has aged up (reached the age of maturity) for this study
studyPredicate : 'hasAgedUp' '(' ')'  # HasAgedUpPredicate
              ;

// Question predicate functions
questionPredicate : 'isAnswered' '(' ')'    # IsAnsweredPredicate
                  ;

// Lexical rules


INSTANCE_TYPE : 'latest' | 'specific' ;
BOOL : 'true' | 'false' ;
STR : '"' .*? '"' ;
INT : ('0'..'9')+ ;
// just a java.time.temporal.ChronoUnit
TIMEUNIT : 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS' ;
COMPARE_OPERATOR : '<' | '<=' | '>' | '>=' | '==' | '!=' ;


// Misc

WS : [ \t\r\n] -> skip ;    // Whitespace is not significant
