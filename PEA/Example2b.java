// java -Xms32M -Xmx32M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:+UseTLAB -XX:CompileCommand='compileonly,Example2b::foo*' -Xlog:gc -XX:+DoPartialEscapeAnalysis -XX:+PrintEscapeAnalysis -XX:+PrintEliminateAllocations -XX:-PrintCompilation -XX:CompileCommand=quiet  Example2b
class Example2b {
    public String _cache;

    public String foo(boolean cond) {
        String x = null;

        if (cond) {
            x = new String("hello");
        }

        return x;
    }
    public String foo2(boolean cond) {
        String x = null;

        if (cond) {
            x = new String("hello");
            _cache = x;       // object 'x' escapes
        }

        return x;
    }

    public String foo3(String str, boolean cond) {
        if (str == null) {
            str = new String("hello");
        }
        if (cond) {
            _cache = str;
        }
        return str;
    }
    public static void main(String[] args)  {
        Example2b kase = new Example2b();
        int iterations = 0;
        try {
            while (true) {
                boolean cond = 0 == (iterations & 0xf);
                String s = kase.foo(cond);
                if (s != null && !s.equals("hello")) {
                    throw new RuntimeException("e");
                }

                kase.foo2(cond);
                if (kase._cache != null && !kase._cache.equals("hello")) {
                    throw new RuntimeException("e2");
                }

                if (0 == (iterations % 2)) {
                    s = kase.foo3(null, cond);
                } else {
                    s= kase.foo3(kase._cache, cond);
                }

                if (s != null && !s.equals("hello")) {
                    throw new RuntimeException("e3");
                }

                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
