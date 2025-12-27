package lexer;

public class Token {
    public final TokenType type;
    public final String lexeme;
    public final int line;
    public final int column;

    // opcional: valor parseado para NUM/CHAR_LIT
    public final Object value;

    public Token(TokenType type, String lexeme, Object value, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        // formato simple para tokens.txt
        if (value != null) {
            return String.format("%d:%d  %-10s  %s  (value=%s)", line, column, type, lexeme, value);
        }
        return String.format("%d:%d  %-10s  %s", line, column, type, lexeme);
    }
}
