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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Performance test of Arrays.sort() methods
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time=60)
@Measurement(iterations = 3, time=120)
@Fork(value = 1)
public class ArraysSort {


    @Param({"100", "1000", "10000", "100000"})
    private int size;

    private int[] ints_unsorted;
    private long[] longs_unsorted;
    private float[] floats_unsorted;
    private double[] doubles_unsorted;

    private int[] ints_sorted;
    private long[] longs_sorted;
    private float[] floats_sorted;
    private double[] doubles_sorted;


    @Setup
    public void setup() throws UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, Throwable {
        Random rnd = new Random(42);

        ints_unsorted = new int[size];
        longs_unsorted = new long[size];
        floats_unsorted = new float[size];
        doubles_unsorted = new double[size];

        for (int i = 0; i < size; i++) {
            ints_unsorted[i] = rnd.nextInt();
            longs_unsorted[i] = rnd.nextLong();
            floats_unsorted[i] = rnd.nextFloat();
            doubles_unsorted[i] = rnd.nextDouble();
        }
    }

    @Benchmark
    public void intSort() throws Throwable {
        ints_sorted = ints_unsorted.clone();
        Arrays.sort(ints_sorted);
    }

    @Benchmark
    public void longSort() throws Throwable {
        longs_sorted = longs_unsorted.clone();
        Arrays.sort(longs_sorted);
    }

    @Benchmark
    public void floatSort() throws Throwable {
        floats_sorted = floats_unsorted.clone();
        Arrays.sort(floats_sorted);
    }

    @Benchmark
    public void doubleSort() throws Throwable {
        doubles_sorted = doubles_unsorted.clone();
        Arrays.sort(doubles_sorted);
    }

}
