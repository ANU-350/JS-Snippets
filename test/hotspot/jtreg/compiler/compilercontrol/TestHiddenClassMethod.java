/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @bug 8271461
 * @summary the CompileCommand support for hidden class methods
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.compiler1.enabled | vm.compiler2.enabled
 *
 * @run driver compiler.compilercontrol.TestHiddenClassMethod
 */

package compiler.compilercontrol;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHiddenClassMethod {
    public static void main(String[] args) throws Exception {
        String err_msg = "Error: Method pattern uses '/' together with '::'";

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CompileCommand=exclude,java.util.ResourceBundle$$Lambda$1/0x00000008010413c8::run",
                "-version");
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldNotContain(err_msg);

        pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CompileCommand=exclude,java.util.ResourceBundle$$Lambda$1/0x*::run",
                "-version");
        analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldNotContain(err_msg);

        pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CompileCommand=exclude,java.util.ResourceBundle$$Lambda$1/01234::run",
                "-version");
        analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain(err_msg);

        pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CompileCommand=exclude,java.util.ResourceBundle$$Lambda$1/0x23u*::run",
                "-version");
        analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain(err_msg);

        pb = ProcessTools.createJavaProcessBuilder(
                "-XX:CompileCommand=exclude,java/*::run",
                "-version");
        analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain(err_msg);
    }
}
