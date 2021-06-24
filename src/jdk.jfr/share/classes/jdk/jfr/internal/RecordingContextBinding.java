/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.util.Objects;
import java.util.Set;
import jdk.jfr.RecordingContextKey;
import jdk.jfr.internal.JVM;

/**
 * @since 17
 */
public abstract sealed class RecordingContextBinding implements AutoCloseable
    permits InheritableRecordingContextBinding, NonInheritableRecordingContextBinding {

    private final static Cleaner cleaner = Cleaner.create();

    // double linked-list of contexts
    private final RecordingContextBinding previous;
    private RecordingContextBinding next;

    private final NativeBindingWrapper nativeWrapper;

    private final Cleanable closer;

    private boolean closed = false;

    protected RecordingContextBinding(RecordingContextBinding previous, Set<RecordingContextEntry> entries) {
        this.previous = previous;
        if (this.previous != null) {
            if (this.previous.next != null) {
                // we didn't peel the onion properly, make sure any outer layer is closed properly
                this.previous.next.close();
            }
            this.previous.next = this;
        }

        this.nativeWrapper = new NativeBindingWrapper(
            this.previous != null ? this.previous.nativeWrapper : null, entries, isInheritable());

        this.closer = cleaner.register(
            this, new NativeBindingCloser(this.nativeWrapper));
    }

    protected RecordingContextBinding previous() {
        return previous;
    }

    protected abstract boolean isInheritable();

    protected static void set(RecordingContextBinding context, boolean isInheritable) {
        NativeBindingWrapper.setCurrent(
            context != null ? context.nativeWrapper : null, isInheritable);
    }

    // public boolean containsKey(RecordingContextKey key) {
    //     if (closed) {
    //         throw new UnsupportedOperationException("binding is closed");
    //     }

    //     return JVM.getJVM().recordingContextContainsKey(cid,
    //                 Objects.requireNonNull(key).getName());
    // }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        // close any outer layer of the onion
        if (next != null) {
            // we didn't peel the onion properly, make sure any outer layer is closed properly
            next.close();
            next = null;
        }

        nativeWrapper.close();

        if (previous != null) {
            previous.next = null;
        }
    }

    static class NativeBindingWrapper implements AutoCloseable {

        private final long id;
        private final NativeBindingWrapper previous;
        private final boolean isInheritable;

        private boolean closed = false;

        public NativeBindingWrapper(NativeBindingWrapper previous, Set<RecordingContextEntry> entries, boolean isInheritable) {
            this.previous = previous;
            this.isInheritable = isInheritable;

            // convert entries to an array of contiguous pair of String
            //  [ "key1", "value1", "key2", "value2", ... ]
            long[] entriesAsPooledStrings = new long[entries.size() * 2];
            int i = 0;
            for (RecordingContextEntry entry : entries) {
                entriesAsPooledStrings[i * 2 + 0] =
                    StringPool.addStringWithoutPreCache(entry.getKey().getName());
                entriesAsPooledStrings[i * 2 + 1] =
                    StringPool.addStringWithoutPreCache(entry.getValue());
                i += 1;
            }

            this.id = JVM.getJVM().recordingContextNew(this.previous != null ? this.previous.id : 0,
                            Objects.requireNonNull(entriesAsPooledStrings));

            setCurrent(this, this.isInheritable);
        }

        public static void setCurrent(NativeBindingWrapper context, boolean isInheritable) {
            if (context != null && context.closed) {
                throw new UnsupportedOperationException("binding is closed");
            }

            JVM.getJVM().recordingContextSet(context != null ? context.id : 0, isInheritable);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            setCurrent(previous, previous.isInheritable);

            JVM.getJVM().recordingContextDelete(id);
        }
    }

    static class NativeBindingCloser implements Runnable {

        private final NativeBindingWrapper nativeWrapper;

        public NativeBindingCloser(NativeBindingWrapper nativeWrapper) {
            this.nativeWrapper = nativeWrapper;
        }

        public void run() {
            this.nativeWrapper.close();
        }
    }
}
