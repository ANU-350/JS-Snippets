/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255968
 * @summary Confusing error message for inaccessible constructor
 * @run compile/fail/ref=T8255968_1.out -XDrawDiagnostics T8255968_1.java
 * @run compile/fail/ref=T8255968_2.out -XDrawDiagnostics T8255968_2.java
 * @run compile/fail/ref=T8255968_3.out -XDrawDiagnostics T8255968_3.java
 * @run compile/fail/ref=T8255968_4.out -XDrawDiagnostics T8255968_4.java
 * @run compile/fail/ref=T8255968_5.out -XDrawDiagnostics T8255968_5.java
 * @run compile/fail/ref=T8255968_6.out -XDrawDiagnostics T8255968_6.java
 * @run compile/fail/ref=T8255968_7.out -XDrawDiagnostics T8255968_7.java
 * @run compile -XDrawDiagnostics T8255968_8.java
 * @run compile -XDrawDiagnostics T8255968_9.java
 * @run compile/fail/ref=T8255968_10.out -XDrawDiagnostics T8255968_10.java
 * @run compile/fail/ref=T8255968_11.out -XDrawDiagnostics T8255968_11.java
 * @run compile/fail/ref=T8255968_12.out -XDrawDiagnostics T8255968_12.java
 * @run compile/fail/ref=T8255968_13.out -XDrawDiagnostics T8255968_13.java
 * @run compile/fail/ref=T8255968_14.out -XDrawDiagnostics T8255968_14.java
 * @run compile/fail/ref=T8255968_15.out -XDrawDiagnostics T8255968_15.java
 * @run compile/fail/ref=T8255968_16.out -XDrawDiagnostics T8255968_16.java
 */
