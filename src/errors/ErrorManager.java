package errors;

import java.util.ArrayList;
import java.util.List;

public class ErrorManager {
    private final List<String> errors = new ArrayList<>();

    public void addLexical(int line, int col, String msg) {
        errors.add(String.format("%d:%d  [LEX] %s", line, col, msg));
    }

    public List<String> getErrors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }
}
