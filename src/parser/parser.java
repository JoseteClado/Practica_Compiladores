package parser;

import errors.ErrorManager;
import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;
import sem.SymbolTable;
import sem.Type;

public class Parser {
    private final Lexer lexer;
    private final ErrorManager err;
    private Token lookahead;

    // Tabla de símbolos (ámbitos)
    private final SymbolTable st = new SymbolTable();

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
        if (check(t)) {
            advance();
        } else {
            syntaxError(msg + " (encontrado: " + lookahead.type + " '" + lookahead.lexeme + "')");
            panicRecover(t);
        }
    }

    private void syntaxError(String msg) {
        err.addLexical(lookahead.line, lookahead.column, "[SYN] " + msg);
    }

    private void semanticError(String msg) {
        err.addLexical(lookahead.line, lookahead.column, "[SEM] " + msg);
    }

    /**
     * Recuperación simple: avanzar hasta encontrar el token esperado o un punto de sincronización.
     */
    private void panicRecover(TokenType expected) {
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
        st.enterScope();

        parseDecls();
        parseStmts();

        match(TokenType.RBRACE, "Se esperaba '}'");
        st.exitScope();
    }

    private void parseDecls() {
        while (isTypeToken(lookahead.type)) {
            parseDecl();
        }
    }

    private void parseDecl() {
        Type t = parseTypeReturn();

        // Guardamos nombre ANTES de consumir ID
        String name = lookahead.lexeme;
        match(TokenType.ID, "Se esperaba un identificador en declaración");

        // Semántica: no redeclarar en el mismo ámbito
        if (t != Type.ERROR) {
            boolean ok = st.declare(name, t);
            if (!ok) {
                semanticError("Variable redeclarada en el mismo ámbito: " + name);
            }
        }

        match(TokenType.SEMI, "Falta ';' al final de la declaración");
    }

    private Type parseTypeReturn() {
        if (check(TokenType.INT))  { advance(); return Type.INT; }
        if (check(TokenType.BOOL)) { advance(); return Type.BOOL; }
        if (check(TokenType.CHAR)) { advance(); return Type.CHAR; }

        syntaxError("Se esperaba un tipo (int/bool/char)");
        // recuperamos como podamos
        advance();
        return Type.ERROR;
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
        // Capturamos el nombre antes de consumir ID
        String name = lookahead.lexeme;
        match(TokenType.ID, "Se esperaba ID en asignación");

        // Semántica: variable declarada
        Type varType = st.lookup(name);
        if (varType == null) {
            semanticError("Variable no declarada: " + name);
            varType = Type.ERROR;
        }

        match(TokenType.ASSIGN, "Se esperaba '=' en asignación");

        Type exprType = parseExprType();

        // Semántica: tipos compatibles
        if (varType != Type.ERROR && exprType != Type.ERROR && varType != exprType) {
            semanticError("Asignación incompatible: " + name + " es " + varType + " y la expresión es " + exprType);
        }
    }

    private void parsePrint() {
        match(TokenType.PRINT, "Se esperaba 'print'");
        match(TokenType.LPAREN, "Se esperaba '(' tras print");
        parseExprType(); // puede imprimirse cualquier tipo (mínimo)
        match(TokenType.RPAREN, "Se esperaba ')' en print");
    }

    private void parseIf() {
        match(TokenType.IF, "Se esperaba 'if'");
        match(TokenType.LPAREN, "Se esperaba '(' tras if");

        Type cond = parseExprType();
        if (cond != Type.BOOL && cond != Type.ERROR) {
            semanticError("La condición del if debe ser BOOL");
        }

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

        Type cond = parseExprType();
        if (cond != Type.BOOL && cond != Type.ERROR) {
            semanticError("La condición del while debe ser BOOL");
        }

        match(TokenType.RPAREN, "Se esperaba ')' tras condición");
        parseStmt();
    }

    // --------- Expressions with precedence (DEVUELVEN TYPE) ---------
    private Type parseExprType() { return parseOr(); }

    private Type parseOr() {
        Type left = parseAnd();
        while (check(TokenType.OROR)) {
            advance();
            Type right = parseAnd();
            if (left != Type.BOOL || right != Type.BOOL) {
                semanticError("'||' requiere operandos BOOL");
                left = Type.ERROR;
            } else {
                left = Type.BOOL;
            }
        }
        return left;
    }

    private Type parseAnd() {
        Type left = parseEq();
        while (check(TokenType.ANDAND)) {
            advance();
            Type right = parseEq();
            if (left != Type.BOOL || right != Type.BOOL) {
                semanticError("'&&' requiere operandos BOOL");
                left = Type.ERROR;
            } else {
                left = Type.BOOL;
            }
        }
        return left;
    }

    private Type parseEq() {
        Type left = parseRel();
        while (check(TokenType.EQEQ) || check(TokenType.NEQ)) {
            advance();
            Type right = parseRel();

            if (left == Type.ERROR || right == Type.ERROR) {
                left = Type.ERROR;
            } else if (left != right) {
                semanticError("'=='/'!=' requiere operandos del mismo tipo");
                left = Type.ERROR;
            } else {
                left = Type.BOOL;
            }
        }
        return left;
    }

    private Type parseRel() {
        Type left = parseAdd();
        while (check(TokenType.LT) || check(TokenType.LE) || check(TokenType.GT) || check(TokenType.GE)) {
            advance();
            Type right = parseAdd();
            if (left != Type.INT || right != Type.INT) {
                semanticError("Comparaciones (<,<=,>,>=) requieren INT");
                left = Type.ERROR;
            } else {
                left = Type.BOOL;
            }
        }
        return left;
    }

    private Type parseAdd() {
        Type left = parseMul();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            TokenType op = lookahead.type;
            advance();
            Type right = parseMul();
            if (left != Type.INT || right != Type.INT) {
                semanticError("Operador '" + (op == TokenType.PLUS ? "+" : "-") + "' requiere INT");
                left = Type.ERROR;
            } else {
                left = Type.INT;
            }
        }
        return left;
    }

    private Type parseMul() {
        Type left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            TokenType op = lookahead.type;
            advance();
            Type right = parseUnary();
            if (left != Type.INT || right != Type.INT) {
                String sym = (op == TokenType.STAR ? "*" : (op == TokenType.SLASH ? "/" : "%"));
                semanticError("Operador '" + sym + "' requiere INT");
                left = Type.ERROR;
            } else {
                left = Type.INT;
            }
        }
        return left;
    }

    private Type parseUnary() {
        if (check(TokenType.NOT)) {
            advance();
            Type t = parseUnary();
            if (t != Type.BOOL && t != Type.ERROR) {
                semanticError("'!' requiere BOOL");
                return Type.ERROR;
            }
            return (t == Type.ERROR) ? Type.ERROR : Type.BOOL;
        }
        if (check(TokenType.MINUS)) {
            advance();
            Type t = parseUnary();
            if (t != Type.INT && t != Type.ERROR) {
                semanticError("'-' unario requiere INT");
                return Type.ERROR;
            }
            return (t == Type.ERROR) ? Type.ERROR : Type.INT;
        }
        return parsePrimary();
    }

    private Type parsePrimary() {
        if (check(TokenType.NUM)) {
            advance();
            return Type.INT;
        }
        if (check(TokenType.CHAR_LIT)) {
            advance();
            return Type.CHAR;
        }
        if (check(TokenType.TRUE) || check(TokenType.FALSE)) {
            advance();
            return Type.BOOL;
        }

        if (check(TokenType.ID)) {
            String name = lookahead.lexeme;
            advance();
            Type t = st.lookup(name);
            if (t == null) {
                semanticError("Variable no declarada: " + name);
                return Type.ERROR;
            }
            return t;
        }

        if (check(TokenType.LPAREN)) {
            advance();
            Type t = parseExprType();
            match(TokenType.RPAREN, "Se esperaba ')'");
            return t;
        }

        syntaxError("Expresión inválida");
        advance();
        return Type.ERROR;
    }
}
