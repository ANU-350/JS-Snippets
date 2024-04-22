/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8305007
 * @summary Within-lambda subclass of local class using method param causes compiler crash
 * @compile CompilerCrashLambdaPlusLocalClass.java
 * @run main CompilerCrashLambdaPlusLocalClass
 */

public abstract class CompilerCrashLambdaPlusLocalClass {
    public abstract void consume(Runnable r);

    public void doThing(String parameter1, int parameter2) {
        class LocalClass {
            @Override
            public String toString() {
                return "" + parameter2 + parameter1;
            }
        }
        consume(() -> {
            class LambdaLocalClass extends LocalClass {}
            new LambdaLocalClass();
            new LocalClass();
        });
    }

    public static void main(String... args) {
        new CompilerCrashLambdaPlusLocalClass() {
            @Override
            public void consume(Runnable r) {
                r.run();
            }
        }.doThing("test", 0);
    }
}
