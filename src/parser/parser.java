package parser;

import errors.ErrorManager;
import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;
import sem.SymbolTable;
import sem.Type;
import ir.IRBuilder;
import ir.ExprRes;

public class Parser {
    private final Lexer lexer;
    private final ErrorManager err;
    private Token lookahead;

    // Semántica
    private final SymbolTable st = new SymbolTable();

    // IR
    private final IRBuilder ir = new IRBuilder();

    public Parser(Lexer lexer, ErrorManager err) {
        this.lexer = lexer;
        this.err = err;
        this.lookahead = lexer.nextToken();
        while (lookahead.type == TokenType.ERROR) lookahead = lexer.nextToken();
    }

    public SymbolTable getSymbolTable() { return st; }
    public IRBuilder getIR() { return ir; }

    // --------- API ---------
    public void parseProgram() {
        match(TokenType.PROGRAM, "Se esperaba 'program'");
        parseBlock();
        match(TokenType.EOF, "Se esperaba EOF al final del programa");
    }

    // --------- Helpers ---------
    private void advance() {
        lookahead = lexer.nextToken();
        while (lookahead.type == TokenType.ERROR) lookahead = lexer.nextToken();
    }

    private boolean check(TokenType t) { return lookahead.type == t; }

    private void match(TokenType t, String msg) {
        if (check(t)) advance();
        else {
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
        while (isTypeToken(lookahead.type)) parseDecl();
    }

    private void parseDecl() {
        Type t = parseTypeReturn();

        String name = lookahead.lexeme;
        match(TokenType.ID, "Se esperaba un identificador en declaración");

        if (t != Type.ERROR) {
            boolean ok = st.declare(name, t);
            if (!ok) semanticError("Variable redeclarada en el mismo ámbito: " + name);
        }

        match(TokenType.SEMI, "Falta ';' al final de la declaración");
    }

    private Type parseTypeReturn() {
        if (check(TokenType.INT))  { advance(); return Type.INT; }
        if (check(TokenType.BOOL)) { advance(); return Type.BOOL; }
        if (check(TokenType.CHAR)) { advance(); return Type.CHAR; }

        syntaxError("Se esperaba un tipo (int/bool/char)");
        advance();
        return Type.ERROR;
    }

    private boolean isTypeToken(TokenType t) {
        return t == TokenType.INT || t == TokenType.BOOL || t == TokenType.CHAR;
    }

    private void parseStmts() {
        while (isStmtStart(lookahead.type)) parseStmt();
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
        if (check(TokenType.IF)) { parseIf(); return; }
        if (check(TokenType.WHILE)) { parseWhile(); return; }
        if (check(TokenType.LBRACE)) { parseBlock(); return; }

        syntaxError("Inicio de sentencia no válido");
        advance();
    }

    // --------- Statements + IR ---------
    private void parseAssign() {
        String name = lookahead.lexeme;
        match(TokenType.ID, "Se esperaba ID en asignación");

        Type varType = st.lookup(name);
        if (varType == null) {
            semanticError("Variable no declarada: " + name);
            varType = Type.ERROR;
        }

        match(TokenType.ASSIGN, "Se esperaba '=' en asignación");

        ExprRes e = parseExprIR();

        if (varType != Type.ERROR && e.type != Type.ERROR && varType != e.type) {
            semanticError("Asignación incompatible: " + name + " es " + varType + " y la expresión es " + e.type);
        }

        // IR: copy expr -> var
        ir.emit("copy", e.r, null, name);
    }

    private void parsePrint() {
        match(TokenType.PRINT, "Se esperaba 'print'");
        match(TokenType.LPAREN, "Se esperaba '(' tras print");
        ExprRes e = parseExprIR();
        match(TokenType.RPAREN, "Se esperaba ')' en print");

        // IR: print x
        ir.emit("print", e.r, null, null);
    }

    private void parseIf() {
        match(TokenType.IF, "Se esperaba 'if'");
        match(TokenType.LPAREN, "Se esperaba '(' tras if");

        ExprRes cond = parseExprIR();
        if (cond.type != Type.BOOL && cond.type != Type.ERROR) {
            semanticError("La condición del if debe ser BOOL");
        }

        match(TokenType.RPAREN, "Se esperaba ')' tras condición");

        String Lelse = ir.newLabel();
        String Lend  = ir.newLabel();

        // false = 0
        ir.emit("if_EQ", cond.r, "0", Lelse);
        parseStmt();

        if (check(TokenType.ELSE)) {
            ir.emit("goto", Lend, null, null);
            ir.emitLabel(Lelse);
            advance();
            parseStmt();
            ir.emitLabel(Lend);
        } else {
            ir.emitLabel(Lelse);
        }
    }

    private void parseWhile() {
        match(TokenType.WHILE, "Se esperaba 'while'");
        match(TokenType.LPAREN, "Se esperaba '(' tras while");

        String Lstart = ir.newLabel();
        String Lend   = ir.newLabel();

        ir.emitLabel(Lstart);

        ExprRes cond = parseExprIR();
        if (cond.type != Type.BOOL && cond.type != Type.ERROR) {
            semanticError("La condición del while debe ser BOOL");
        }

        match(TokenType.RPAREN, "Se esperaba ')' tras condición");

        ir.emit("if_EQ", cond.r, "0", Lend);
        parseStmt();
        ir.emit("goto", Lstart, null, null);
        ir.emitLabel(Lend);
    }

    // --------- Expressions with precedence (SEM + IR) ---------
    private ExprRes parseExprIR() { return parseOr(); }

    private ExprRes parseOr() {
        ExprRes left = parseAnd();
        while (check(TokenType.OROR)) {
            advance();
            ExprRes right = parseAnd();

            if (left.type != Type.BOOL || right.type != Type.BOOL) {
                semanticError("'||' requiere operandos BOOL");
                left = new ExprRes(Type.ERROR, left.r);
            } else {
                String t = ir.newTemp();
                ir.emit("or", left.r, right.r, t);
                left = new ExprRes(Type.BOOL, t);
            }
        }
        return left;
    }

    private ExprRes parseAnd() {
        ExprRes left = parseEq();
        while (check(TokenType.ANDAND)) {
            advance();
            ExprRes right = parseEq();

            if (left.type != Type.BOOL || right.type != Type.BOOL) {
                semanticError("'&&' requiere operandos BOOL");
                left = new ExprRes(Type.ERROR, left.r);
            } else {
                String t = ir.newTemp();
                ir.emit("and", left.r, right.r, t);
                left = new ExprRes(Type.BOOL, t);
            }
        }
        return left;
    }

    private ExprRes parseEq() {
        ExprRes left = parseRel();
        while (check(TokenType.EQEQ) || check(TokenType.NEQ)) {
            TokenType op = lookahead.type;
            advance();
            ExprRes right = parseRel();

            if (left.type == Type.ERROR || right.type == Type.ERROR) {
                left = new ExprRes(Type.ERROR, left.r);
                continue;
            }
            if (left.type != right.type) {
                semanticError("'=='/'!=' requiere operandos del mismo tipo");
                left = new ExprRes(Type.ERROR, left.r);
                continue;
            }

            // IR boolean resultado 0 / -1
            String t = ir.newTemp();
            String e1 = ir.newLabel();
            String e2 = ir.newLabel();

            ir.emit(op == TokenType.EQEQ ? "if_EQ" : "if_NE", left.r, right.r, e1);
            ir.emit("copy", "0", null, t);
            ir.emit("goto", e2, null, null);
            ir.emitLabel(e1);
            ir.emit("copy", "-1", null, t);
            ir.emitLabel(e2);

            left = new ExprRes(Type.BOOL, t);
        }
        return left;
    }

    private ExprRes parseRel() {
        ExprRes left = parseAdd();
        while (check(TokenType.LT) || check(TokenType.LE) || check(TokenType.GT) || check(TokenType.GE)) {
            TokenType op = lookahead.type;
            advance();
            ExprRes right = parseAdd();

            if (left.type != Type.INT || right.type != Type.INT) {
                semanticError("Comparaciones (<,<=,>,>=) requieren INT");
                left = new ExprRes(Type.ERROR, left.r);
                continue;
            }

            String t = ir.newTemp();
            String e1 = ir.newLabel();
            String e2 = ir.newLabel();

            ir.emit("if_" + relMnemonic(op), left.r, right.r, e1);
            ir.emit("copy", "0", null, t);
            ir.emit("goto", e2, null, null);
            ir.emitLabel(e1);
            ir.emit("copy", "-1", null, t);
            ir.emitLabel(e2);

            left = new ExprRes(Type.BOOL, t);
        }
        return left;
    }

    private String relMnemonic(TokenType t) {
        switch (t) {
            case LT: return "LT";
            case LE: return "LE";
            case GT: return "GT";
            case GE: return "GE";
            default: return "??";
        }
    }

    private ExprRes parseAdd() {
        ExprRes left = parseMul();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            TokenType op = lookahead.type;
            advance();
            ExprRes right = parseMul();

            if (left.type != Type.INT || right.type != Type.INT) {
                semanticError("'+/-' requiere INT");
                left = new ExprRes(Type.ERROR, left.r);
                continue;
            }

            String t = ir.newTemp();
            ir.emit(op == TokenType.PLUS ? "add" : "sub", left.r, right.r, t);
            left = new ExprRes(Type.INT, t);
        }
        return left;
    }

    private ExprRes parseMul() {
        ExprRes left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            TokenType op = lookahead.type;
            advance();
            ExprRes right = parseUnary();

            if (left.type != Type.INT || right.type != Type.INT) {
                semanticError("'*//%' requiere INT");
                left = new ExprRes(Type.ERROR, left.r);
                continue;
            }

            String t = ir.newTemp();
            String irOp = (op == TokenType.STAR) ? "prod" : (op == TokenType.SLASH ? "div" : "mod");
            ir.emit(irOp, left.r, right.r, t);
            left = new ExprRes(Type.INT, t);
        }
        return left;
    }

    private ExprRes parseUnary() {
        if (check(TokenType.NOT)) {
            advance();
            ExprRes e = parseUnary();
            if (e.type != Type.BOOL && e.type != Type.ERROR) semanticError("'!' requiere BOOL");
            String t = ir.newTemp();
            ir.emit("not", e.r, null, t);
            return new ExprRes(e.type == Type.ERROR ? Type.ERROR : Type.BOOL, t);
        }
        if (check(TokenType.MINUS)) {
            advance();
            ExprRes e = parseUnary();
            if (e.type != Type.INT && e.type != Type.ERROR) semanticError("'-' unario requiere INT");
            String t = ir.newTemp();
            ir.emit("neg", e.r, null, t);
            return new ExprRes(e.type == Type.ERROR ? Type.ERROR : Type.INT, t);
        }
        return parsePrimary();
    }

    private ExprRes parsePrimary() {
        if (check(TokenType.NUM)) {
            String v = lookahead.lexeme;
            advance();
            String t = ir.newTemp();
            ir.emit("copy", v, null, t);
            return new ExprRes(Type.INT, t);
        }

        if (check(TokenType.CHAR_LIT)) {
            // si vuestro lexeme es "'A'" podéis convertirlo a número ASCII o guardar literal tal cual.
            // Para simplificar: lo dejamos como lexeme (p.ej. 'A') y lo copiamos.
            String v = lookahead.lexeme;
            advance();
            String t = ir.newTemp();
            ir.emit("copy", v, null, t);
            return new ExprRes(Type.CHAR, t);
        }

        if (check(TokenType.TRUE) || check(TokenType.FALSE)) {
            String v = check(TokenType.TRUE) ? "-1" : "0"; // bool: true=-1, false=0
            advance();
            String t = ir.newTemp();
            ir.emit("copy", v, null, t);
            return new ExprRes(Type.BOOL, t);
        }

        if (check(TokenType.ID)) {
            String name = lookahead.lexeme;
            advance();
            Type t = st.lookup(name);
            if (t == null) {
                semanticError("Variable no declarada: " + name);
                return new ExprRes(Type.ERROR, name);
            }
            // Para IDs devolvemos el nombre como “lugar”
            return new ExprRes(t, name);
        }

        if (check(TokenType.LPAREN)) {
            advance();
            ExprRes e = parseExprIR();
            match(TokenType.RPAREN, "Se esperaba ')'");
            return e;
        }

        syntaxError("Expresión inválida");
        advance();
        return new ExprRes(Type.ERROR, "<?>");
    }
}
