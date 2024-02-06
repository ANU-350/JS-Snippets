/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @enablePreview
 * @compile SimpleWithers.java
 * @run main SimpleWithers
 */
public class SimpleWithers {
    public static void main(String... args) {
        R r = new R(1, 2, 3);
        r = r with {
            val1 = -1;
            val3 = -3;
        };
        if (r.val1() != (-1) ||
            r.val2() != 2 ||
            r.val3() != (-3)) {
            throw new AssertionError("Incorrect value: " + r);
        }
        R rp = r;
        boolean match = switch (rp) {
            case R(var i1, var i2, var i3) when rp with {
                val1 = -1;
                val3 = -3;
            }.val1() == -1 -> true;
            default -> false;
        };
        if (!match) {
            throw new AssertionError("Did not match.");
        }
        //shadowing:
        R2 r2 = new R2(r);
        R2 r2p = r2 with {
            val1 = val1 with {
                val1 = -2;
                val3 = -6;
            };
        };
        if (r2p.val1().val1() != (-2) ||
            r2p.val1().val2() != 2 ||
            r2p.val1().val3() != (-6)) {
            throw new AssertionError("Incorrect value: " + r);
        }
        {
            int val1 = 0;
            if (r with {
                    val1 = -3;
                } instanceof R(var v1, var v2, var v3) && v1 != (-3)) {
                throw new AssertionError("Incorrect value: " + v1);
            }
        }
        if (r instanceof R(var val1, var val2, var val3) && r with {
                val1 = -3;
            } instanceof R(var v1, var v2, var v3) && v1 != (-3)) {
            throw new AssertionError("Incorrect value: " + v1);
        }
        C c = l -> l with {
            val1 = -4;
            val3 = -8;
        };
    }
    record R(int val1, int val2, int val3) {}
    record R2(R val1) {}
    interface C {
        R apply(R r);
    }
}