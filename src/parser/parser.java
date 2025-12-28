package parser;

import errors.ErrorManager;
import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;

public class Parser {
    private final Lexer lexer;
    private final ErrorManager err;
    private Token lookahead;

    public Parser(Lexer lexer, ErrorManager err) {
        this.lexer = lexer;
        this.err = err;
        this.lookahead = lexer.nextToken();
    }

    // --------- API ---------
    public void parseProgram() {
        match(TokenType.PROGRAM, "Se esperaba 'program'");
        parseBlock();
        match(TokenType.EOF, "Se esperaba EOF al final del programa");
    }

    // --------- Helpers ---------
    private void advance() {
        lookahead = lexer.nextToken();
        // si el lexer devuelve ERROR, lo saltamos para poder seguir parseando
        while (lookahead.type == TokenType.ERROR) {
            lookahead = lexer.nextToken();
        }
    }

    private boolean check(TokenType t) {
        return lookahead.type == t;
    }

    private void match(TokenType t, String msg) {
        if (lookahead.type == t) {
            advance();
        } else {
            syntaxError(msg + " (encontrado: " + lookahead.type + " '" + lookahead.lexeme + "')");
            panicRecover(t);
        }
    }

    private void syntaxError(String msg) {
        err.addLexical(lookahead.line, lookahead.column, "[SYN] " + msg);
        // Nota: reutilizo addLexical para no crear otra clase ahora.
        // Si preferís, luego hacemos addSyntax(...) en ErrorManager.
    }

    /**
     * Recuperación simple: avanzar hasta encontrar el token esperado o un punto de sincronización.
     */
    private void panicRecover(TokenType expected) {
        // puntos típicos de sincronización
        while (!check(TokenType.EOF)
                && !check(expected)
                && !check(TokenType.SEMI)
                && !check(TokenType.RBRACE)) {
            advance();
        }
        if (check(expected)) advance();
        else if (check(TokenType.SEMI)) advance();
    }

    // --------- Grammar ---------
    private void parseBlock() {
        match(TokenType.LBRACE, "Se esperaba '{'");
        parseDecls();
        parseStmts();
        match(TokenType.RBRACE, "Se esperaba '}'");
    }

    private void parseDecls() {
        while (isTypeToken(lookahead.type)) {
            parseDecl();
        }
    }

    private void parseDecl() {
        parseType();
        match(TokenType.ID, "Se esperaba un identificador en declaración");
        match(TokenType.SEMI, "Falta ';' al final de la declaración");
    }

    private void parseType() {
        if (isTypeToken(lookahead.type)) {
            advance();
        } else {
            match(TokenType.INT, "Se esperaba un tipo (int/bool/char)");
        }
    }

    private boolean isTypeToken(TokenType t) {
        return t == TokenType.INT || t == TokenType.BOOL || t == TokenType.CHAR;
    }

    private void parseStmts() {
        while (isStmtStart(lookahead.type)) {
            parseStmt();
        }
    }

    private boolean isStmtStart(TokenType t) {
        return t == TokenType.ID
                || t == TokenType.PRINT
                || t == TokenType.IF
                || t == TokenType.WHILE
                || t == TokenType.LBRACE;
    }

    private void parseStmt() {
        if (check(TokenType.ID)) {
            parseAssign();
            match(TokenType.SEMI, "Falta ';' al final de la asignación");
            return;
        }
        if (check(TokenType.PRINT)) {
            parsePrint();
            match(TokenType.SEMI, "Falta ';' al final de print");
            return;
        }
        if (check(TokenType.IF)) {
            parseIf();
            return;
        }
        if (check(TokenType.WHILE)) {
            parseWhile();
            return;
        }
        if (check(TokenType.LBRACE)) {
            parseBlock();
            return;
        }

        syntaxError("Inicio de sentencia no válido");
        advance();
    }

    private void parseAssign() {
        match(TokenType.ID, "Se esperaba ID en asignación");
        match(TokenType.ASSIGN, "Se esperaba '=' en asignación");
        parseExpr();
    }

    private void parsePrint() {
        match(TokenType.PRINT, "Se esperaba 'print'");
        match(TokenType.LPAREN, "Se esperaba '(' tras print");
        parseExpr();
        match(TokenType.RPAREN, "Se esperaba ')' en print");
    }

    private void parseIf() {
        match(TokenType.IF, "Se esperaba 'if'");
        match(TokenType.LPAREN, "Se esperaba '(' tras if");
        parseExpr();
        match(TokenType.RPAREN, "Se esperaba ')' tras condición");
        parseStmt(); // permite stmt o block
        if (check(TokenType.ELSE)) {
            advance();
            parseStmt();
        }
    }

    private void parseWhile() {
        match(TokenType.WHILE, "Se esperaba 'while'");
        match(TokenType.LPAREN, "Se esperaba '(' tras while");
        parseExpr();
        match(TokenType.RPAREN, "Se esperaba ')' tras condición");
        parseStmt();
    }

    // --------- Expressions with precedence ---------
    private void parseExpr() { parseOr(); }

    private void parseOr() {
        parseAnd();
        while (check(TokenType.OROR)) {
            advance();
            parseAnd();
        }
    }

    private void parseAnd() {
        parseEq();
        while (check(TokenType.ANDAND)) {
            advance();
            parseEq();
        }
    }

    private void parseEq() {
        parseRel();
        while (check(TokenType.EQEQ) || check(TokenType.NEQ)) {
            advance();
            parseRel();
        }
    }

    private void parseRel() {
        parseAdd();
        while (check(TokenType.LT) || check(TokenType.LE) || check(TokenType.GT) || check(TokenType.GE)) {
            advance();
            parseAdd();
        }
    }

    private void parseAdd() {
        parseMul();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            advance();
            parseMul();
        }
    }

    private void parseMul() {
        parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            advance();
            parseUnary();
        }
    }

    private void parseUnary() {
        if (check(TokenType.NOT) || check(TokenType.MINUS)) {
            advance();
            parseUnary();
            return;
        }
        parsePrimary();
    }

    private void parsePrimary() {
        if (check(TokenType.NUM) || check(TokenType.CHAR_LIT) || check(TokenType.TRUE) || check(TokenType.FALSE) || check(TokenType.ID)) {
            advance();
            return;
        }
        if (check(TokenType.LPAREN)) {
            advance();
            parseExpr();
            match(TokenType.RPAREN, "Se esperaba ')'");
            return;
        }
        syntaxError("Expresión inválida");
        advance();
    }
}
