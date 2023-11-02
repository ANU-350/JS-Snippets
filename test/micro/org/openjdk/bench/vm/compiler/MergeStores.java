/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;


import jdk.internal.misc.Unsafe;
import jdk.internal.util.ByteArrayLittleEndian;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(value = 3, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.util=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class MergeStores {

    public static final int RANGE = 100;

    static Unsafe UNSAFE = Unsafe.getUnsafe();

    @Param("1")
    public static short vS;

    @Param("1")
    public static int vI;

    @Param("1")
    public static long vL;

    public static int offset = 5; // TODO randomize?
    public static byte[] a = new byte[RANGE];

    @Benchmark
    public void baseline() {
    }

    @Benchmark
    public byte[] baseline_allocate() {
        byte[] a = new byte[RANGE];
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_adr0_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[0] = (byte)0x01;
        a[1] = (byte)0x02;
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_adr1_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[1] = (byte)0x01;
        a[2] = (byte)0x02;
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)0x01;
        a[offset + 1] = (byte)0x02;
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putShortUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, (short)0x0201);
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setShort(a, offset, (short)0x0201);
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_offs_nonalloc_direct() {
        a[offset + 0] = (byte)0x01;
        a[offset + 1] = (byte)0x02;
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_offs_nonalloc_unsafe() {
        UNSAFE.putShortUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, (short)0x0201);
        return a;
    }

    @Benchmark
    public byte[] store_2B_con_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setShort(a, offset, (short)0x0201);
        return a;
    }

    @Benchmark
    public byte[] store_2B_S_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)(vS >> 0 );
        a[offset + 1] = (byte)(vS >> 8 );
        return a;
    }

    @Benchmark
    public byte[] store_2B_S_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putShortUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vS);
        return a;
    }

    @Benchmark
    public byte[] store_2B_S_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setShort(a, offset, vS);
        return a;
    }

    @Benchmark
    public byte[] store_2B_S_offs_nonalloc_direct() {
        a[offset + 0] = (byte)(vS >> 0 );
        a[offset + 1] = (byte)(vS >> 8 );
        return a;
    }

    @Benchmark
    public byte[] store_2B_S_offs_nonalloc_unsafe() {
        UNSAFE.putShortUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vS);
        return a;
    }

    @Benchmark
    public byte[] store_2B_S_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setShort(a, offset, vS);
        return a;
    }


    @Benchmark
    public byte[] store_4B_con_adr0_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[0] = (byte)0x01;
        a[1] = (byte)0x02;
        a[2] = (byte)0x03;
        a[3] = (byte)0x04;
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_adr1_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[1] = (byte)0x01;
        a[2] = (byte)0x02;
        a[3] = (byte)0x03;
        a[4] = (byte)0x04;
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)0x01;
        a[offset + 1] = (byte)0x02;
        a[offset + 2] = (byte)0x03;
        a[offset + 3] = (byte)0x04;
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x04030201);
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setInt(a, offset, 0x04030201);
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_offs_nonalloc_direct() {
        a[offset + 0] = (byte)0x01;
        a[offset + 1] = (byte)0x02;
        a[offset + 2] = (byte)0x03;
        a[offset + 3] = (byte)0x04;
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_offs_nonalloc_unsafe() {
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x04030201);
        return a;
    }

    @Benchmark
    public byte[] store_4B_con_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setInt(a, offset, 0x04030201);
        return a;
    }

    @Benchmark
    public byte[] store_4B_I_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)(vI >> 0 );
        a[offset + 1] = (byte)(vI >> 8 );
        a[offset + 2] = (byte)(vI >> 16);
        a[offset + 3] = (byte)(vI >> 24);
        return a;
    }

    @Benchmark
    public byte[] store_4B_I_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vI);
        return a;
    }

    @Benchmark
    public byte[] store_4B_I_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setInt(a, offset, vI);
        return a;
    }

    @Benchmark
    public byte[] store_4B_I_offs_nonalloc_direct() {
        a[offset + 0] = (byte)(vI >> 0 );
        a[offset + 1] = (byte)(vI >> 8 );
        a[offset + 2] = (byte)(vI >> 16);
        a[offset + 3] = (byte)(vI >> 24);
        return a;
    }

    @Benchmark
    public byte[] store_4B_I_offs_nonalloc_unsafe() {
        UNSAFE.putIntUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vI);
        return a;
    }

    @Benchmark
    public byte[] store_4B_I_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setInt(a, offset, vI);
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_adr0_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[0] = (byte)0x01;
        a[1] = (byte)0x02;
        a[2] = (byte)0x03;
        a[3] = (byte)0x04;
        a[4] = (byte)0x05;
        a[5] = (byte)0x06;
        a[6] = (byte)0x07;
        a[7] = (byte)0x08;
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_adr1_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[1] = (byte)0x01;
        a[2] = (byte)0x02;
        a[3] = (byte)0x03;
        a[4] = (byte)0x04;
        a[5] = (byte)0x05;
        a[6] = (byte)0x06;
        a[7] = (byte)0x07;
        a[8] = (byte)0x08;
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)0x01;
        a[offset + 1] = (byte)0x02;
        a[offset + 2] = (byte)0x03;
        a[offset + 3] = (byte)0x04;
        a[offset + 4] = (byte)0x05;
        a[offset + 5] = (byte)0x06;
        a[offset + 6] = (byte)0x07;
        a[offset + 7] = (byte)0x08;
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x0807060504030201L);
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setLong(a, offset, 0x0807060504030201L);
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_offs_nonalloc_direct() {
        a[offset + 0] = (byte)0x01;
        a[offset + 1] = (byte)0x02;
        a[offset + 2] = (byte)0x03;
        a[offset + 3] = (byte)0x04;
        a[offset + 4] = (byte)0x05;
        a[offset + 5] = (byte)0x06;
        a[offset + 6] = (byte)0x07;
        a[offset + 7] = (byte)0x08;
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_offs_nonalloc_unsafe() {
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x0807060504030201L);
        return a;
    }

    @Benchmark
    public byte[] store_8B_con_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setLong(a, offset, 0x0807060504030201L);
        return a;
    }

    @Benchmark
    public byte[] store_8B_L_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)(vL >> 0 );
        a[offset + 1] = (byte)(vL >> 8 );
        a[offset + 2] = (byte)(vL >> 16);
        a[offset + 3] = (byte)(vL >> 24);
        a[offset + 4] = (byte)(vL >> 32);
        a[offset + 5] = (byte)(vL >> 40);
        a[offset + 6] = (byte)(vL >> 48);
        a[offset + 7] = (byte)(vL >> 56);
        return a;
    }

    @Benchmark
    public byte[] store_8B_L_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vL);
        return a;
    }

    @Benchmark
    public byte[] store_8B_L_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setLong(a, offset, vL);
        return a;
    }

    @Benchmark
    public byte[] store_8B_L_offs_nonalloc_direct() {
        a[offset + 0] = (byte)(vL >> 0 );
        a[offset + 1] = (byte)(vL >> 8 );
        a[offset + 2] = (byte)(vL >> 16);
        a[offset + 3] = (byte)(vL >> 24);
        a[offset + 4] = (byte)(vL >> 32);
        a[offset + 5] = (byte)(vL >> 40);
        a[offset + 6] = (byte)(vL >> 48);
        a[offset + 7] = (byte)(vL >> 56);
        return a;
    }

    @Benchmark
    public byte[] store_8B_L_offs_nonalloc_unsafe() {
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vL);
        return a;
    }

    @Benchmark
    public byte[] store_8B_L_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setLong(a, offset, vL);
        return a;
    }

    @Benchmark
    public byte[] store_8B_2I_offs_allocate_direct() {
        byte[] a = new byte[RANGE];
        a[offset + 0] = (byte)(vI >> 0 );
        a[offset + 1] = (byte)(vI >> 8 );
        a[offset + 2] = (byte)(vI >> 16);
        a[offset + 3] = (byte)(vI >> 24);
        a[offset + 4] = (byte)(vI >> 0 );
        a[offset + 5] = (byte)(vI >> 8 );
        a[offset + 6] = (byte)(vI >> 16);
        a[offset + 7] = (byte)(vI >> 24);
        return a;
    }

    @Benchmark
    public byte[] store_8B_2I_offs_allocate_unsafe() {
        byte[] a = new byte[RANGE];
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 0, vI);
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 4, vI);
        return a;
    }

    @Benchmark
    public byte[] store_8B_2I_offs_allocate_bale() {
        byte[] a = new byte[RANGE];
        ByteArrayLittleEndian.setInt(a, offset + 0, vI);
        ByteArrayLittleEndian.setInt(a, offset + 4, vI);
        return a;
    }

    @Benchmark
    public byte[] store_8B_2I_offs_nonalloc_direct() {
        a[offset + 0] = (byte)(vI >> 0 );
        a[offset + 1] = (byte)(vI >> 8 );
        a[offset + 2] = (byte)(vI >> 16);
        a[offset + 3] = (byte)(vI >> 24);
        a[offset + 4] = (byte)(vI >> 0 );
        a[offset + 5] = (byte)(vI >> 8 );
        a[offset + 6] = (byte)(vI >> 16);
        a[offset + 7] = (byte)(vI >> 24);
        return a;
    }

    @Benchmark
    public byte[] store_8B_2I_offs_nonalloc_unsafe() {
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 0, vI);
        UNSAFE.putLongUnaligned(a, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 4, vI);
        return a;
    }

    @Benchmark
    public byte[] store_8B_2I_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setInt(a, offset + 0, vI);
        ByteArrayLittleEndian.setInt(a, offset + 4, vI);
        return a;
    }
}
