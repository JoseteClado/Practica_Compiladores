package lexer;

public enum TokenType {
    // Keywords
    PROGRAM, PROC, CONST, INT, BOOL, CHAR, IF, ELSE, WHILE, FOR, PRINT, READ, TRUE, FALSE,

    // Identifiers & literals
    ID, NUM, CHAR_LIT,

    // Operators
    ASSIGN, PLUS, MINUS, STAR, SLASH, MOD,
    EQEQ, NEQ, LT, LE, GT, GE,
    ANDAND, OROR, NOT,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, SEMI, COMMA,

    // Special
    EOF, ERROR
}
