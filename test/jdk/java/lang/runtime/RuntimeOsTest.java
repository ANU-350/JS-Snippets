/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.Architecture;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.util.Locale;

import static java.lang.Runtime.OperatingSystem.AIX;
import static java.lang.Runtime.OperatingSystem.Linux;
import static java.lang.Runtime.OperatingSystem.MacOSX;
import static java.lang.Runtime.OperatingSystem.Windows;

/**
 * @test
 * @summary test platform enum
 * @run testng RuntimeOsTest
 */

@Test
public class RuntimeOsTest {
    /**
     * Test consistency of System property "os.name" with Runtime.OperatingSystem.current().
     */
    @Test
    void test1() {
        String osName = System.getProperty("os.name").substring(0, 3).toLowerCase(Locale.ROOT);
        Runtime.OperatingSystem os = switch (osName) {
            case "win" -> Windows;
            case "lin" -> Linux;
            case "mac" -> MacOSX;
            case "aix" -> AIX;
            default    -> throw new RuntimeException("unknown OS kind: " + osName);
        };
        Assert.assertEquals(os, Runtime.OperatingSystem.current(), "mismatch in OperatingSystem.current vs " + osName);
    }


    /**
     * Test consistency of System property "os.arch" with Runtime.Architecture.current().
     */
    @Test
    void Test2() {
        String archName = System.getProperty("os.arch");
        Architecture arch = switch (archName) {
            case "x86"             -> Architecture.X86;
            case "amd64", "x86_64" -> Architecture.X64;
            case "arm"             -> Architecture.ARM;
            case "aarch64"         -> Architecture.AARCH64;
            default                -> throw new RuntimeException("Unknown architecture: " + archName);
        };
        Assert.assertEquals(arch, Architecture.current(), "Mismatch in architecture");
    }

    @Test
    void Test3() {
        int count = 0;
        for (Runtime.OperatingSystem os : Runtime.OperatingSystem.values()) {
            System.out.println("os: " + os + ", current: " + os.isCurrent());
            if  (os.isCurrent()) {
                count++;
            }
        }
        Assert.assertEquals(count, 1, "More than 1 OperatingSystem is 'current()'");
    }

 }
