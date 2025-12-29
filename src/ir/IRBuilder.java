package ir;

import java.util.*;

public class IRBuilder {

    private final List<String> code = new ArrayList<>();
    private int tempCount = 0;
    private int labelCount = 0;

    public String newTemp() { return "t" + (++tempCount); }
    public String newLabel() { return "L" + (++labelCount); }

    // Formato “3 direcciones” estilo apuntes: op a b c
    public void emit(String op, String a, String b, String c) {
        code.add(String.format("%-7s %-8s %-8s %-8s",
                op,
                a == null ? "" : a,
                b == null ? "" : b,
                c == null ? "" : c));
    }

    // Etiqueta estilo “skip Lx”
    public void emitLabel(String label) {
        code.add(String.format("%-7s %-8s", "skip", label));
    }

    public String getCode() {
        StringBuilder sb = new StringBuilder();
        for (String s : code) sb.append(s).append('\n');
        return sb.toString();
    }
}
