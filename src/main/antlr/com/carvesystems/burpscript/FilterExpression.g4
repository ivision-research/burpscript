grammar FilterExpression;

@header {
  package com.carvesystems.burpscript;
}

expression
  : statement
  ;

statement
  : '(' FUNCNAME ')'
  | '(' FUNCNAME arg+ ')'
  ;

arg
  : literal
  | statement
  ;


literal
  : STRING
  | RAW_STRING
  | NUMBER
  | array
  | BOOLEAN
  ;

array
    : '[' STRING (',' STRING)* ']'
    | '[' NUMBER (',' NUMBER)* ']'
    | '[' BOOLEAN (',' BOOLEAN)* ']'
    | '[' ']'
    ;

BOOLEAN
  : 'true'
  | 'false'
  ;

RAW_STRING
    : 'r' STRING;

STRING
  : '"' (~'"' | '\\' '"')* '"'
  ;

NUMBER
  : Integer
  | HexNumber
  | BinNumber
  ;

FUNCNAME
  : [a-zA-Z0-9][-a-zA-Z0-9]*[a-zA-Z0-9]
  ;

fragment BinNumber
  : '0b' [0-1]+
  ;

fragment HexNumber
  : '0x' '0'+
  | '-'? '0x' [0-9a-fA-F][0-9a-fA-F]*
  ;

fragment Integer
  : '0'
  | '-'? [1-9][0-9]*
  ;

WS
  : [ \t\n\r]+ -> skip
  ;
