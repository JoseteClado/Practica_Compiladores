package sem;

import java.util.*;

public class SymbolTable {
    private final Deque<Map<String, Type>> scopes = new ArrayDeque<>();

    public SymbolTable() { enterScope(); } // scope global

    public void enterScope() { scopes.push(new HashMap<>()); }

    public void exitScope() {
        if (!scopes.isEmpty()) scopes.pop();
    }

    // devuelve false si ya existe en el MISMO scope
    public boolean declare(String name, Type type) {
        Map<String, Type> top = scopes.peek();
        if (top.containsKey(name)) return false;
        top.put(name, type);
        return true;
    }

    // busca en scopes (del más interno al más externo)
    public Type lookup(String name) {
        for (Map<String, Type> s : scopes) {
            Type t = s.get(name);
            if (t != null) return t;
        }
        return null;
    }
}
