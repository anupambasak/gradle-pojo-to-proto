package io.github.anupambasak.gradle.testenums;

import java.util.EnumSet;
import java.util.HashMap;

public interface Conts {

    enum b {
        c("LB", 0, 76, "L"),
        d("MB", 1, 77, "M"),
        e("UB", 2, 85, "U"),
        f("SL", 3, 82, "R"),
        g("SU", 4, 80, "P"),
        h("WS", 5, 87, "W"),
        i("AS", 6, 65, "A"),
        j("CB", 7, 67, "CB"),
        k("CP", 8, 80, "CP"),
        l("HP", 9, 72, "HB"),
        m("SM", 10, 68, "D"),
        n("S", 11, 83, "S"),
        o("ST", 12, 84, "T");

        private static final HashMap<String, b> p = new HashMap<>();
        private static final HashMap<Integer, b> q = new HashMap<>();
        private static final HashMap<Integer, b> r = new HashMap<>();
        private static final HashMap<String, b> s = new HashMap<>();

        static {
            for (b x : EnumSet.allOf(b.class)) {
                p.put(x.u, x);
                q.put(x.t, x);
                r.put(x.v, x);
                s.put(x.w, x);
            }
        }

        private final Integer t;
        private final String u;
        private final Integer v;
        private final String w;

        b(String u, Integer t, Integer v, String w) {
            this.t = t;
            this.u = u;
            this.v = v;
            this.w = w;
        }

        public Integer aa() { return t; }
        public String ab() { return u; }
        public Integer ac() { return v; }
        public String ad() { return w; }

        public static b ae(Integer t) { return q.get(t); }
        public static b af(String u) { return p.get(u); }
        public static b ag(Integer v) { return r.get(v); }
        public static b ah(String w) { return s.get(w); }
    }
}