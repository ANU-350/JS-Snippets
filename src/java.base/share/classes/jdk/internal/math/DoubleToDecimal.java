/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.math;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import static java.lang.Double.PRECISION;
import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Long.numberOfLeadingZeros;
import static java.lang.Math.*;
import static jdk.internal.math.MathUtils.*;

/**
 * This class exposes a method to render a {@code double} as a string.
 */
public final class DoubleToDecimal {
    /*
     * For full details about this code see the following references:
     *
     * [1] Giulietti, "The Schubfach way to render doubles",
     *     https://drive.google.com/file/d/1gp5xv4CAa78SVgCeWfGqqI4FfYYYuNFb
     *
     * [2] IEEE Computer Society, "IEEE Standard for Floating-Point Arithmetic"
     *
     * [3] Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"
     *
     * Divisions are avoided altogether for the benefit of those architectures
     * that do not provide specific machine instructions or where they are slow.
     * This is discussed in section 10 of [1].
     */

    /* The precision in bits */
    static final int P = PRECISION;

    /* Exponent width in bits */
    private static final int W = (Double.SIZE - 1) - (P - 1);

    /* Minimum value of the exponent: -(2^(W-1)) - P + 3 */
    static final int Q_MIN = (-1 << (W - 1)) - P + 3;

    /* Maximum value of the exponent: 2^(W-1) - P */
    static final int Q_MAX = (1 << (W - 1)) - P;

    /* 10^(E_MIN - 1) <= MIN_VALUE < 10^E_MIN */
    static final int E_MIN = -323;

    /* 10^(E_MAX - 1) <= MAX_VALUE < 10^E_MAX */
    static final int E_MAX = 309;

    /* Threshold to detect tiny values, as in section 8.2.1 of [1] */
    static final long C_TINY = 3;

    /* The minimum and maximum k, as in section 8 of [1] */
    static final int K_MIN = -324;
    static final int K_MAX = 292;

    /* H is as in section 8.1 of [1] */
    static final int H = 17;

    /* Minimum value of the significand of a normal value: 2^(P-1) */
    private static final long C_MIN = 1L << (P - 1);

    /* Mask to extract the biased exponent */
    private static final int BQ_MASK = (1 << W) - 1;

    /* Mask to extract the fraction bits */
    private static final long T_MASK = (1L << (P - 1)) - 1;

    /* Used in rop() */
    private static final long MASK_63 = (1L << 63) - 1;

    /* Used for left-to-tight digit extraction */
    private static final int MASK_28 = (1 << 28) - 1;

    private static final int NON_SPECIAL    = 0;
    private static final int PLUS_ZERO      = 1;
    private static final int MINUS_ZERO     = 2;
    private static final int PLUS_INF       = 3;
    private static final int MINUS_INF      = 4;
    private static final int NAN            = 5;

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private byte[] bytes;

    /* Index into bytes */
    private int index;

    private DoubleToDecimal() {
    }

    /**
     * Returns a string representation of the {@code double}
     * argument. All characters mentioned below are ASCII characters.
     *
     * @param   v   the {@code double} to be converted.
     * @return a string representation of the argument.
     * @see Double#toString(double)
     */
    public static String toString(double v) {
        return new DoubleToDecimal().toDecimalString(v);
    }

    /**
     * Splits the decimal <i>d</i> described in
     * {@link Double#toString(double)} in integers <i>f</i> and <i>e</i>
     * such that <i>d</i> = <i>f</i> 10<sup><i>e</i></sup>.
     *
     * <p>Further, determines integer <i>n</i> such that <i>n</i> = 0 when
     * <i>f</i> = 0, and
     * 10<sup><i>n</i>-1</sup> &le; <i>f</i> &lt; 10<sup><i>n</i></sup>
     * otherwise.
     *
     * <p>The argument {@code v} is assumed to be a positive finite value or
     * positive zero.
     * Further, {@code fd} must not be {@code null}.
     *
     * @param v     the finite {@code double} to be split.
     * @param fd    the object that will carry <i>f</i>, <i>e</i>, and <i>n</i>.
     */
    public static void split(double v, FormattedFPDecimal fd) {
        new DoubleToDecimal().toDecimal(v, fd);
    }

    /**
     * Appends the rendering of the {@code v} to {@code app}.
     *
     * <p>The outcome is the same as if {@code v} were first
     * {@link #toString(double) rendered} and the resulting string were then
     * {@link Appendable#append(CharSequence) appended} to {@code app}.
     *
     * @param v the {@code double} whose rendering is appended.
     * @param app the {@link Appendable} to append to.
     * @throws IOException If an I/O error occurs
     */
    public static Appendable appendTo(double v, Appendable app)
            throws IOException {
        return new DoubleToDecimal().appendDecimalTo(v, app);
    }

    private String toDecimalString(double v) {
        return switch (toDecimal(v, null)) {
            case NON_SPECIAL -> charsToString();
            case PLUS_ZERO -> "0.0";
            case MINUS_ZERO -> "-0.0";
            case PLUS_INF -> "Infinity";
            case MINUS_INF -> "-Infinity";
            default -> "NaN";
        };
    }

    private Appendable appendDecimalTo(double v, Appendable app)
            throws IOException {
        return switch (toDecimal(v, null)) {
            case NON_SPECIAL -> app.append(charsToString());
            case PLUS_ZERO -> app.append("0.0");
            case MINUS_ZERO -> app.append("-0.0");
            case PLUS_INF -> app.append("Infinity");
            case MINUS_INF -> app.append("-Infinity");
            default -> app.append("NaN");
        };
    }

    /*
     * Returns
     *     PLUS_ZERO       iff v is 0.0
     *     MINUS_ZERO      iff v is -0.0
     *     PLUS_INF        iff v is POSITIVE_INFINITY
     *     MINUS_INF       iff v is NEGATIVE_INFINITY
     *     NAN             iff v is NaN
     */
    private int toDecimal(double v, FormattedFPDecimal fd) {
        /*
         * For full details see references [2] and [1].
         *
         * For finite v != 0, determine integers c and q such that
         *     |v| = c 2^q    and
         *     Q_MIN <= q <= Q_MAX    and
         *         either    2^(P-1) <= c < 2^P                 (normal)
         *         or        0 < c < 2^(P-1)  and  q = Q_MIN    (subnormal)
         */
        long bits = doubleToRawLongBits(v);
        long t = bits & T_MASK;
        int bq = (int) (bits >>> P - 1) & BQ_MASK;
        if (bq < BQ_MASK) {
            index = bits < 0 ? 1 : 0;
            if (bq != 0) {
                /* normal value. Here mq = -q */
                int mq = -Q_MIN + 1 - bq;
                long c = C_MIN | t;
                /* The fast path discussed in section 8.3 of [1] */
                if (0 < mq & mq < P) {
                    long f = c >> mq;
                    if (f << mq == c) {
                        return toChars(f, 0, fd);
                    }
                }
                return toDecimal(-mq, c, 0, fd);
            }
            if (t != 0) {
                /* subnormal value */
                return t < C_TINY
                       ? toDecimal(Q_MIN, 10 * t, -1, fd)
                       : toDecimal(Q_MIN, t, 0, fd);
            }
            return bits == 0 ? PLUS_ZERO : MINUS_ZERO;
        }
        if (t != 0) {
            return NAN;
        }
        return bits > 0 ? PLUS_INF : MINUS_INF;
    }

    private int toDecimal(int q, long c, int dk, FormattedFPDecimal fd) {
        /*
         * The skeleton corresponds to figure 7 of [1].
         * The efficient computations are those summarized in figure 9.
         *
         * Here's a correspondence between Java names and names in [1],
         * expressed as approximate LaTeX source code and informally.
         * Other names are identical.
         * cb:     \bar{c}     "c-bar"
         * cbr:    \bar{c}_r   "c-bar-r"
         * cbl:    \bar{c}_l   "c-bar-l"
         *
         * vb:     \bar{v}     "v-bar"
         * vbr:    \bar{v}_r   "v-bar-r"
         * vbl:    \bar{v}_l   "v-bar-l"
         *
         * rop:    r_o'        "r-o-prime"
         */
        int out = (int) c & 0x1;
        long cb = c << 2;
        long cbr = cb + 2;
        long cbl;
        int k;
        /*
         * flog10pow2(e) = floor(log_10(2^e))
         * flog10threeQuartersPow2(e) = floor(log_10(3/4 2^e))
         * flog2pow10(e) = floor(log_2(10^e))
         */
        if (c != C_MIN | q == Q_MIN) {
            /* regular spacing */
            cbl = cb - 2;
            k = flog10pow2(q);
        } else {
            /* irregular spacing */
            cbl = cb - 1;
            k = flog10threeQuartersPow2(q);
        }
        int h = q + flog2pow10(-k) + 2;

        /* g1 and g0 are as in section 9.8.3 of [1], so g = g1 2^63 + g0 */
        long g1 = g1(k);
        long g0 = g0(k);

        long vb = rop(g1, g0, cb << h);
        long vbl = rop(g1, g0, cbl << h);
        long vbr = rop(g1, g0, cbr << h);

        long s = vb >> 2;
        if (s >= 100) {
            /*
             * For n = 17, m = 1 the table in section 10 of [1] shows
             *     s' = floor(s / 10) = floor(s 115_292_150_460_684_698 / 2^60)
             *        = floor(s 115_292_150_460_684_698 2^4 / 2^64)
             *
             * sp10 = 10 s'
             * tp10 = 10 t'
             * upin    iff    u' = sp10 10^k in Rv
             * wpin    iff    w' = tp10 10^k in Rv
             * See section 9.3 of [1].
             */
            long sp10 = 10 * multiplyHigh(s, 115_292_150_460_684_698L << 4);
            long tp10 = sp10 + 10;
            boolean upin = vbl + out <= sp10 << 2;
            boolean wpin = (tp10 << 2) + out <= vbr;
            if (upin != wpin) {
                return toChars(upin ? sp10 : tp10, k, fd);
            }
        }

        /*
         * 10 <= s < 100    or    s >= 100  and  u', w' not in Rv
         * uin    iff    u = s 10^k in Rv
         * win    iff    w = t 10^k in Rv
         * See section 9.3 of [1].
         */
        long t = s + 1;
        boolean uin = vbl + out <= s << 2;
        boolean win = (t << 2) + out <= vbr;
        if (uin != win) {
            /* Exactly one of u or w lies in Rv */
            return toChars(uin ? s : t, k + dk, fd);
        }
        /*
         * Both u and w lie in Rv: determine the one closest to v.
         * See section 9.3 of [1].
         */
        long cmp = vb - (s + t << 1);
        return toChars(cmp < 0 || cmp == 0 && (s & 0x1) == 0 ? s : t, k + dk, fd);
    }

    /*
     * Computes rop(cp g 2^(-127)), where g = g1 2^63 + g0
     * See section 9.9 and figure 8 of [1].
     */
    private static long rop(long g1, long g0, long cp) {
        long x1 = multiplyHigh(g0, cp);
        long y0 = g1 * cp;
        long y1 = multiplyHigh(g1, cp);
        long z = (y0 >>> 1) + x1;
        long vbp = y1 + (z >>> 63);
        return vbp | (z & MASK_63) + MASK_63 >>> 63;
    }

    /*
     * Formats the decimal f 10^e.
     */
    private int toChars(long f, int e, FormattedFPDecimal fd) {
        /*
         * For details not discussed here see section 10 of [1].
         *
         * Determine len such that
         *     10^(len-1) <= f < 10^len
         */
        int len = flog10pow2(Long.SIZE - numberOfLeadingZeros(f));
        if (f >= pow10(len)) {
            len += 1;
        }
        if (fd != null) {
            fd.set(f, e, len);
            return NON_SPECIAL;
        }

        /*
         * Let fp and ep be the original f and e, respectively.
         * Transform f and e to ensure
         *     10^(H-1) <= f < 10^H
         *     fp 10^ep = f 10^(e-H) = f_0.f_1...f_{H-1} 10^e
         */
        f *= pow10(H - len);
        e += len - 1;

        /*
         * The toChars?() methods perform left-to-right digits extraction
         * using ints, provided that the arguments are limited to 8 digits.
         * Therefore, split the H = 17 digits of f into:
         *     h = the most significant digit of f
         *     m = the next 8 most significant digits of f
         *     l = the last 8, least significant digits of f
         *
         * For n = 17, m = 8 the table in section 10 of [1] shows
         *     floor(f / 10^8) = floor(193_428_131_138_340_668 f / 2^84) =
         *     floor(floor(193_428_131_138_340_668 f / 2^64) / 2^20)
         * and for n = 9, m = 8
         *     floor(hm / 10^8) = floor(1_441_151_881 hm / 2^57)
         *
         * dlen is the index of the least significant non-zero decimal in
         *     f_0f_1...f_{H-1}
         * which is the expansion of f.
         * For example, when f = 12_345_670_000_000_000 then dlen = 6.
         */
        long hm = multiplyHigh(f, 193_428_131_138_340_668L) >>> 20;
        int l = (int) (f - 100_000_000L * hm);
        int h = (int) (hm * 1_441_151_881L >>> 57);
        int m = (int) (hm - 100_000_000 * h);
        int dlen = l != 0
            ? 16 - decNumberOfTrailingZeros(l)
            : m != 0
            ? 8 - decNumberOfTrailingZeros(m)
            : 0;

        if (0 <= e && e < 7) {
            if (dlen <= e) {
                return toChars0(h, m, e);
            }
            return toChars1(h, m, l, e, dlen);
        }
        if (-3 <= e && e < 0) {
            return toChars2(h, m, l, e, dlen);
        }
        return toChars3(h, m, l, e, dlen);
    }

    private int toChars0(int h, int m, int e) {
        /*
         * 0 <= e < 7: plain format without leading zeroes, integer values.
         * Left-to-right digits extraction:
         * algorithm 1 in [3], with b = 10, k = 8, n = 28.
         */
        bytes = new byte[index + e + 3];
        appendSign();
        appendDigit(h);
        int y = y(m);
        for (; e > 0; --e) {
            int t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
        append('.');
        append('0');
        return NON_SPECIAL;
    }

    private int toChars1(int h, int m, int l, int e, int dlen) {
        /*
         * 0 <= e < 7: plain format without leading zeroes.
         * Left-to-right digits extraction:
         * algorithm 1 in [3], with b = 10, k = 8, n = 28.
         */
        bytes = new byte[index + dlen + 2];
        appendSign();
        appendDigit(h);
        int y = y(m);
        int min = min(dlen, 8);
        for (int i = 0; i < min; ++i) {
            if (i == e) {
                append('.');
            }
            int t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
        if (l != 0) {
            appendLow(l, dlen);
        }
        return NON_SPECIAL;
    }

    private int toChars2(int h, int m, int l, int e, int dlen) {
        /* -3 <= e < 0: plain format with leading zeroes */
        bytes = new byte[index + 2 - e + dlen];
        appendSign();
        append('0');
        append('.');
        for (; e < -1; ++e) {
            append('0');
        }
        appendDigit(h);
        int y = y(m);
        for (int min = min(dlen, 8); min > 0; --min) {
            int t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
        if (l != 0) {
            appendLow(l, dlen);
        }
        return NON_SPECIAL;
    }

    private int toChars3(int h, int m, int l, int e, int dlen) {
        /* -3 > e | e >= 7: computerized scientific notation */
        if (dlen == 0) {
            dlen = 1;
        }
        int ea = abs(e);
        int elen = ea < 10 ? 1 : ea < 100 ? 2 : 3;
        bytes = new byte[index + dlen + elen + (e < 0 ? 4 : 3)];
        appendSign();
        appendDigit(h);
        append('.');
        int y = y(m);
        for (int min = min(dlen, 8); min > 0; --min) {
            int t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
        if (l != 0) {
            appendLow(l, dlen);
        }
        append('E');
        if (e < 0) {
            append('-');
        }
        exponent(ea);
        return NON_SPECIAL;
    }

    private void appendLow(int l, int dlen) {
        int y = y(l);
        for (; dlen > 8; --dlen) {
            int t = 10 * y;
            appendDigit(t >>> 28);
            y = t & MASK_28;
        }
    }

    private static int y(int a) {
        /*
         * Algorithm 1 in [3] needs computation of
         *     floor((a + 1) 2^n / b^k) - 1
         * with a < 10^8, b = 10, k = 8, n = 28.
         * Noting that
         *     (a + 1) 2^n <= 10^8 2^28 < 10^17
         * For n = 17, m = 8 the table in section 10 of [1] leads to:
         */
        return (int) (multiplyHigh(
                (long) (a + 1) << 28,
                193_428_131_138_340_668L) >>> 20) - 1;
    }

    private void exponent(int ea) {
        if (ea < 10) {
            appendDigit(ea);
            return;
        }
        int d;
        if (ea >= 100) {
            /*
             * For n = 3, m = 2 the table in section 10 of [1] shows
             *     floor(ea / 100) = floor(1_311 ea / 2^17)
             */
            d = ea * 1_311 >>> 17;
            appendDigit(d);
            ea -= 100 * d;
        }
        /*
         * For n = 2, m = 1 the table in section 10 of [1] shows
         *     floor(ea / 10) = floor(103 ea / 2^10)
         */
        d = ea * 103 >>> 10;
        appendDigit(d);
        appendDigit(ea - 10 * d);
    }

    private void appendSign() {
        if (index != 0) {
            bytes[0] = '-';
        }
    }

    private void append(int c) {
        bytes[index++] = (byte) c;
    }

    private void appendDigit(int d) {
        bytes[index++] = (byte) ('0' + d);
    }

    private String charsToString() {
        try {
            return JLA.newStringNoRepl(bytes, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException e) {
            throw new AssertionError(e);
        }
    }

}
