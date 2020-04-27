// PEX - The Pepper Expression Language
grammar Pex;

// Starting rule for parsing
pex : expr ;

// Ordering of rule alternatives (aka right-hand-side) determines precedence. Top to bottom is highest
// to lowest precedence. Having a higer precedence means that the syntactic elements will "bind" closer together.
expr
  : BOOL              # BoolLiteralExpr
  | INT               # IntLiteralExpr
  | STR               # StrLiteralExpr
  | query             # QueryExpr
  | UNARY_OPERATOR expr          # UnaryExpr
  | expr RELATION_OPERATOR expr  # CompareExpr
  | expr EQUALITY_OPERATOR expr  # EqualityExpr
  | expr '&&' expr    # AndExpr
  | expr '||' expr    # OrExpr
  | '(' expr ')'      # GroupExpr
  ;

query
  : 'user' '.' study '.' studyPredicate                                                  # StudyQuery
  | 'user' '.' study '.' form '.' formPredicate                                          # FormQuery
  | 'user' '.' study '.' form '.' question '.' questionPredicate                         # QuestionQuery
  | 'user' '.' study '.' form '.' question '.' 'answers' '.' predicate                   # DefaultLatestAnswerQuery
  | 'user' '.' study '.' form '.' instance '.' question '.' 'answers' '.' predicate      # AnswerQuery
  | 'user' '.' 'profile' '.' profileDataQuery   # ProfileQuery
  ;

study : 'studies' '[' STR ']' ;
form : 'forms' '[' STR ']' ;
instance : 'instances' '[' INSTANCE_TYPE ']' ;
question : 'questions' '[' STR ']' ;

// A predicate evaluating to true if a user has aged up (reached the age of maturity) for this study
studyPredicate
  : 'hasAgedUp' '(' ')'  # HasAgedUpPredicate
  ;

// Form predicate functions that operate on a single piece of data
formPredicate
  : 'isStatus' '(' STR ( ',' STR )* ')'  # IsStatusPredicate
  | 'hasInstance' '(' ')'         # HasInstancePredicate
  ;

// Question predicate functions
questionPredicate
  : 'isAnswered' '(' ')'    # IsAnsweredPredicate
  ;

// Predicate functions that operates on a set/collection of things or a single piece of data
predicate
  : 'hasTrue' '(' ')'   # HasTruePredicate
  | 'hasFalse' '(' ')'  # HasFalsePredicate
  | 'hasText' '(' ')'   # HasTextPredicate
  | 'hasOption' '(' STR ')'                 # HasOptionPredicate
  | 'hasAnyOption' '(' STR ( ',' STR )* ')' # HasAnyOptionPredicate
  | 'hasDate' '(' ')'                       # HasDatePredicate
  | 'ageAtLeast' '(' INT ',' TIMEUNIT ')'   # AgeAtLeastPredicate
  | 'value' '(' ')' # ValueQuery    // Not exactly a predicate but putting this here eases implementation and backwards-compatibility.
  ;

// Queries to pull out various pieces of profile data
profileDataQuery
  : 'birthDate' '(' ')'   # ProfileBirthDateQuery
  ;


// Lexical rules

INSTANCE_TYPE : 'latest' | 'specific' ;
BOOL : 'true' | 'false' ;
STR : '"' .*? '"' ;
INT : ('0'..'9')+ ;
TIMEUNIT : 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS' ;  // just a java.time.temporal.ChronoUnit
UNARY_OPERATOR : '!' | '-' ;
RELATION_OPERATOR : '<' | '<=' | '>' | '>=' ;
EQUALITY_OPERATOR : '==' | '!=' ;


// Misc

WS : [ \t\r\n] -> skip ;    // Whitespace is not significant
