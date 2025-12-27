package lexer;

import errors.ErrorManager;
import util.SourceReader;

import java.util.HashMap;
import java.util.Map;

public class Lexer {
    private final SourceReader r;
    private final ErrorManager err;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("program", TokenType.PROGRAM);
        KEYWORDS.put("proc", TokenType.PROC);
        KEYWORDS.put("const", TokenType.CONST);
        KEYWORDS.put("int", TokenType.INT);
        KEYWORDS.put("bool", TokenType.BOOL);
        KEYWORDS.put("char", TokenType.CHAR);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("print", TokenType.PRINT);
        KEYWORDS.put("read", TokenType.READ);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
    }

    public Lexer(SourceReader r, ErrorManager err) {
        this.r = r;
        this.err = err;
    }

    public Token nextToken() {
        skipWhitespaceAndComments();

        int line = r.getLine();
        int col  = r.getCol();

        if (r.isEOF()) {
            return new Token(TokenType.EOF, "<EOF>", null, line, col);
        }

        char c = r.peek();

        // Identifiers / keywords
        if (isLetter(c) || c == '_') {
            String lex = readIdentifier();
            TokenType type = KEYWORDS.getOrDefault(lex, TokenType.ID);
            return new Token(type, lex, null, line, col);
        }

        // Numbers
        if (isDigit(c)) {
            String lex = readNumber();
            Integer val = Integer.parseInt(lex);
            return new Token(TokenType.NUM, lex, val, line, col);
        }

        // Char literal: 'A' o '\n'
        if (c == '\'') {
            return readCharLiteral();
        }

        // Two-char operators
        if (c == '=' && r.peekNext() == '=') { r.next(); r.next(); return new Token(TokenType.EQEQ, "==", null, line, col); }
        if (c == '!' && r.peekNext() == '=') { r.next(); r.next(); return new Token(TokenType.NEQ, "!=", null, line, col); }
        if (c == '<' && r.peekNext() == '=') { r.next(); r.next(); return new Token(TokenType.LE, "<=", null, line, col); }
        if (c == '>' && r.peekNext() == '=') { r.next(); r.next(); return new Token(TokenType.GE, ">=", null, line, col); }
        if (c == '&' && r.peekNext() == '&') { r.next(); r.next(); return new Token(TokenType.ANDAND, "&&", null, line, col); }
        if (c == '|' && r.peekNext() == '|') { r.next(); r.next(); return new Token(TokenType.OROR, "||", null, line, col); }

        // Single-char tokens
        switch (c) {
            case '=': r.next(); return new Token(TokenType.ASSIGN, "=", null, line, col);
            case '+': r.next(); return new Token(TokenType.PLUS, "+", null, line, col);
            case '-': r.next(); return new Token(TokenType.MINUS, "-", null, line, col);
            case '*': r.next(); return new Token(TokenType.STAR, "*", null, line, col);
            case '/': r.next(); return new Token(TokenType.SLASH, "/", null, line, col);
            case '%': r.next(); return new Token(TokenType.MOD, "%", null, line, col);
            case '<': r.next(); return new Token(TokenType.LT, "<", null, line, col);
            case '>': r.next(); return new Token(TokenType.GT, ">", null, line, col);
            case '!': r.next(); return new Token(TokenType.NOT, "!", null, line, col);

            case '(': r.next(); return new Token(TokenType.LPAREN, "(", null, line, col);
            case ')': r.next(); return new Token(TokenType.RPAREN, ")", null, line, col);
            case '{': r.next(); return new Token(TokenType.LBRACE, "{", null, line, col);
            case '}': r.next(); return new Token(TokenType.RBRACE, "}", null, line, col);
            case ';': r.next(); return new Token(TokenType.SEMI, ";", null, line, col);
            case ',': r.next(); return new Token(TokenType.COMMA, ",", null, line, col);
        }

        // Unknown char => lexical error
        err.addLexical(line, col, "Carácter no reconocido: '" + c + "'");
        r.next(); // consumir para no bucle infinito
        return new Token(TokenType.ERROR, String.valueOf(c), null, line, col);
    }

    private void skipWhitespaceAndComments() {
        boolean again;
        do {
            again = false;
            // whitespace
            while (!r.isEOF() && Character.isWhitespace(r.peek())) r.next();

            // line comment //
            if (!r.isEOF() && r.peek() == '/' && r.peekNext() == '/') {
                while (!r.isEOF() && r.peek() != '\n') r.next();
                again = true;
            }
        } while (again);
    }

    private String readIdentifier() {
        StringBuilder sb = new StringBuilder();
        while (!r.isEOF() && (isLetter(r.peek()) || isDigit(r.peek()) || r.peek() == '_')) {
            sb.append(r.next());
        }
        return sb.toString();
    }

    private String readNumber() {
        StringBuilder sb = new StringBuilder();
        while (!r.isEOF() && isDigit(r.peek())) sb.append(r.next());
        return sb.toString();
    }

    private Token readCharLiteral() {
        int line = r.getLine();
        int col  = r.getCol();
        StringBuilder lex = new StringBuilder();
        lex.append(r.next()); // '

        if (r.isEOF()) {
            err.addLexical(line, col, "Literal char sin cerrar");
            return new Token(TokenType.ERROR, lex.toString(), null, line, col);
        }

        char c = r.next();
        lex.append(c);

        char value;
        if (c == '\\') { // escape
            if (r.isEOF()) {
                err.addLexical(line, col, "Escape incompleto en literal char");
                return new Token(TokenType.ERROR, lex.toString(), null, line, col);
            }
            char e = r.next();
            lex.append(e);
            
            switch (e) {
    case 'n':
        value = '\n';
        break;
    case 't':
        value = '\t';
        break;
    case '\'':
        value = '\'';
        break;
    case '\\':
        value = '\\';
        break;
    default:
        value = e; // simplificación
        break;
}

            
        } else {
            value = c;
        }

        if (r.peek() != '\'') {
            err.addLexical(line, col, "Literal char inválido o sin cierre");
            return new Token(TokenType.ERROR, lex.toString(), null, line, col);
        }
        lex.append(r.next()); // cierre '

        return new Token(TokenType.CHAR_LIT, lex.toString(), value, line, col);
    }

    private boolean isLetter(char c) { return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'); }
    private boolean isDigit(char c)  { return (c >= '0' && c <= '9'); }
}
