package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SourceReader {
    private final String input;
    private int idx = 0;
    private int line = 1;
    private int col = 1;

    public SourceReader(Path path) throws IOException {
        this.input = Files.readString(path);
    }

    public boolean isEOF() {
        return idx >= input.length();
    }

    public char peek() {
        return isEOF() ? '\0' : input.charAt(idx);
    }

    public char peekNext() {
        return (idx + 1 >= input.length()) ? '\0' : input.charAt(idx + 1);
    }

    public char next() {
        if (isEOF()) return '\0';
        char c = input.charAt(idx++);
        if (c == '\n') { line++; col = 1; }
        else { col++; }
        return c;
    }

    public int getLine() { return line; }
    public int getCol() { return col; }
}
