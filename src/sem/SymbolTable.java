package sem;

import java.util.*;

public class SymbolTable {

    public static class Entry {
        public final int scopeLevel;
        public final String name;
        public final Type type;

        public Entry(int scopeLevel, String name, Type type) {
            this.scopeLevel = scopeLevel;
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("SCOPE %d  %-12s : %s", scopeLevel, name, type);
        }
    }

    private final Deque<Map<String, Type>> scopes = new ArrayDeque<>();
    private final List<Entry> history = new ArrayList<>();   // <- lo entregable
    private int level = -1;

    public SymbolTable() {
    }

    public void enterScope() {
        scopes.push(new LinkedHashMap<>()); // LinkedHashMap para orden estable
        level++;
    }

    public void exitScope() {
        if (!scopes.isEmpty()) {
            scopes.pop();
            level--;
        }
    }

    // false si ya existe en el MISMO scope
    public boolean declare(String name, Type type) {
        Map<String, Type> top = scopes.peek();
        if (top.containsKey(name)) return false;
        top.put(name, type);

        // Guardamos para el symbols.txt final
        history.add(new Entry(level, name, type));
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

    /** Texto completo para out/symbols.txt */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("SYMBOL TABLE (insertions order)\n");
        sb.append("--------------------------------\n");
        for (Entry e : history) sb.append(e).append('\n');
        return sb.toString();
    }
}
