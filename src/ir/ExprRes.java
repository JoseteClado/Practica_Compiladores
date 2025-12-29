package ir;

import sem.Type;

public class ExprRes {
    public final Type type;
    public final String r;   // “lugar” donde queda el resultado (temp/ID)

    public ExprRes(Type type, String r) {
        this.type = type;
        this.r = r;
    }
}
