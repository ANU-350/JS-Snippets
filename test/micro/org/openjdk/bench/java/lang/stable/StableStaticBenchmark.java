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

package org.openjdk.bench.java.lang.stable;

import jdk.internal.lang.StableValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Benchmark measuring StableValue performance in a static context
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.lang=ALL-UNNAMED", "--enable-preview"})
@Threads(Threads.MAX)   // Benchmark under contention
public class StableStaticBenchmark {

    private static final int VALUE = 42;

    private static final StableValue<Integer> STABLE = init(StableValue.of());
    private static final StableValue<Integer> STABLE_NULL = StableValue.of();
    private static final Supplier<Integer> DCL = new Dcl<>(() -> VALUE);
    private static final List<StableValue<Integer>> LIST = StableValue.ofList(1);
    private static final AtomicReference<Integer> ATOMIC = new AtomicReference<>(VALUE);
    private static final DclHolder DCL_HOLDER = new DclHolder();
    private static final StableHolder STABLE_HOLDER = new StableHolder();
    private static final StableRecordHolder STABLE_RECORD_HOLDER = new StableRecordHolder();

    static {
        LIST.getFirst().trySet(VALUE);
        STABLE_NULL.trySet(null);
    }

    @Benchmark
    public int atomic() {
        return (int)ATOMIC.get();
    }

    @Benchmark
    public int dcl() {
        return (int)DCL.get();
    }

    @Benchmark
    public int dclHolder() {
        return (int)DCL_HOLDER.get();
    }

    @Benchmark
    public int stable() {
        return (int)STABLE.orThrow();
    }

    @Benchmark
    public int stableNull() {
        return STABLE_NULL.orThrow() == null ? VALUE : 13;
    }

    @Benchmark
    public int stableHolder() {
        return (int)STABLE_HOLDER.get();
    }

    @Benchmark
    public int stableRecordHolder() {
        return (int)STABLE_RECORD_HOLDER.get();
    }

    @Benchmark
    public int stableList() {
        return (int)LIST.get(0).orThrow();
    }

    @Benchmark
    public int staticCHI() {
        class Holder {
            static final int VALUE = 42;
        }
        return (int) Holder.VALUE;
    }

    private static StableValue<Integer> init(StableValue<Integer> m) {
        var result = m.trySet(VALUE);
        assert result;
        return m;
    }

    // Handles null values
    private static class Dcl<V> implements Supplier<V> {

        private final Supplier<V> supplier;

        private volatile V value;
        private boolean bound;

        public Dcl(Supplier<V> supplier) {
            this.supplier = supplier;
        }

        @Override
        public V get() {
            V v = value;
            if (v == null) {
                if (!bound) {
                    synchronized (this) {
                        v = value;
                        if (v == null) {
                            if (!bound) {
                                value = v = supplier.get();
                                bound = true;
                            }
                        }
                    }
                }
            }
            return v;
        }
    }

    private static final class DclHolder {
        private final Dcl<Integer> delegate = new Dcl<>(() -> VALUE);

        public Integer get() {
            return delegate.get();
        }
    }

    private static final class StableHolder {
        private final StableValue<Integer> delegate;

        public StableHolder() {
            delegate = StableValue.of();
            var result = delegate.trySet(VALUE);
            assert result;
        }

        public Integer get() {
            return delegate.orThrow();
        }
    }

    private record StableRecordHolder(StableValue<Integer> delegate) {

        public StableRecordHolder() {
            this(StableValue.of());
            var result = delegate.trySet(VALUE);
            assert result;
        }

        public Integer get() {
            return delegate.orThrow();
        }
    }

}
