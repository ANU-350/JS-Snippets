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
 * @bug 6983726
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 * @summary Basic sanity tests for MethodHandleProxies
 * @build BasicTest Untrusted
 * @run junit BasicTest
 */

import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.Classfile;

import java.io.Closeable;
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleProxies.*;
import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.classfile.Classfile.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class BasicTest {

    @Test
    public void testUsual() throws Throwable {
        AtomicInteger ai = new AtomicInteger(5);
        var mh = MethodHandles.lookup().findVirtual(AtomicInteger.class, "getAndIncrement", methodType(int.class));
        IntSupplier is = asInterfaceInstance(IntSupplier.class, mh.bindTo(ai));
        assertEquals(5, is.getAsInt());
        assertEquals(6, is.getAsInt());
        assertEquals(7, is.getAsInt());
    }

    @Test
    public void testThrowables() throws Throwable {
        // don't wrap
        assertThrows(Error.class, throwing(Error.class, new Error())::close);
        assertThrows(RuntimeException.class, throwing(RuntimeException.class, new RuntimeException())::close);
        assertThrows(IOException.class, throwing(IOException.class, new IOException())::close);
        // wrap
        assertThrows(UndeclaredThrowableException.class, throwing(IllegalAccessException.class,
                new IllegalAccessException())::close);
    }

    @Test
    public void testWrapperInstance() throws Throwable {
        Comparator<Integer> lambda = Integer::compareTo;
        var mh = MethodHandles.publicLookup()
                .findVirtual(Integer.class, "compareTo", methodType(int.class, Integer.class));
        @SuppressWarnings("unchecked")
        Comparator<Integer> proxy = (Comparator<Integer>) asInterfaceInstance(Comparator.class, mh);

        assertTrue(isWrapperInstance(proxy));
        assertFalse(isWrapperInstance(lambda));
        assertSame(mh, wrapperInstanceTarget(proxy));
        assertThrows(IllegalArgumentException.class, () -> wrapperInstanceTarget(lambda));
        assertSame(Comparator.class, wrapperInstanceType(proxy));
        assertThrows(IllegalArgumentException.class, () -> wrapperInstanceType(lambda));
    }

    private <T extends Throwable> Closeable throwing(Class<T> clz, T value) {
        return asInterfaceInstance(Closeable.class, MethodHandles.throwException(void.class, clz).bindTo(value));
    }

    private static long mul(int i) {
        return (long) i * i;
    }

    @Test
    public void testConversion() throws Throwable {
        var mh = MethodHandles.lookup().findStatic(BasicTest.class, "mul", methodType(long.class, int.class));
        @SuppressWarnings("unchecked")
        Function<Integer, Long> func = (Function<Integer, Long>) asInterfaceInstance(Function.class, mh);
        assertEquals(32423432L * 32423432L, func.apply(32423432));
        @SuppressWarnings("unchecked")
        ToLongFunction<Integer> func1 = (ToLongFunction<Integer>) asInterfaceInstance(ToLongFunction.class, mh);
        assertEquals(32423432L * 32423432L, func1.applyAsLong(32423432));
        @SuppressWarnings("unchecked")
        IntFunction<Long> func2 = (IntFunction<Long>) asInterfaceInstance(IntFunction.class, mh);
        assertEquals(32423432L * 32423432L, func2.apply(32423432));
    }

    @Test
    public void testModule() throws Throwable {
        var mh = MethodHandles.lookup().findStatic(BasicTest.class, "mul", methodType(long.class, int.class));

        @SuppressWarnings("unchecked")
        Function<Integer, Long> func1 = (Function<Integer, Long>) asInterfaceInstance(Function.class, mh);
        assertEquals(32423432L * 32423432L, func1.apply(32423432));
        Class<?> c1 = func1.getClass();
        Module m1 = c1.getModule();

        String pn = c1.getPackageName();
        assertFalse(m1.isExported(pn));
        assertTrue(m1.isExported(pn, MethodHandleProxies.class.getModule()));
        assertTrue(Object.class.getModule().isExported("sun.invoke", m1));
        assertTrue(Object.class.getModule().isExported("sun.invoke.empty", m1));
        assertTrue(m1.isNamed());
        assertTrue(m1.getName().startsWith("jdk.MHProxy"));
    }

    @Test
    public void testMultiAbstract() throws Throwable {
        var baseAndChild = loadBaseAndChild();
        var baseClass = baseAndChild.get(0);
        var childClass = baseAndChild.get(1);
        checkMethods(childClass.getMethods());
        checkMethods(childClass.getDeclaredMethods());

        var lookup = MethodHandles.lookup();
        var baseValueMh = lookup.findVirtual(baseClass, "value", genericMethodType(0))
                .asType(genericMethodType(1));
        var childIntegerValueMh = lookup.findVirtual(childClass, "value", methodType(Integer.class))
                .asType(methodType(Integer.class, Object.class));
        var childIntValueMh = lookup.findVirtual(childClass, "value", methodType(int.class))
                .asType(methodType(int.class, Object.class));

        Object child = asInterfaceInstance(childClass, MethodHandles.constant(Integer.class, 7));

        assertEquals(7, (Object) baseValueMh.invokeExact(child));
        assertEquals(7, (Integer) childIntegerValueMh.invokeExact(child));
        assertEquals(7, (int) childIntValueMh.invokeExact(child));
    }

    @Test
    public void testRejects() {
        var mh = MethodHandles.constant(String.class, "42");
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(Inaccessible.class, mh));
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(loadHidden(), mh));
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(MultiAbstractMethods.class, mh));
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(NoAbstractMethods.class, mh));
        assertThrows(WrongMethodTypeException.class, () -> asInterfaceInstance(IntSupplier.class, mh));
    }

    @Test
    public void testNoAccess() {
        Untrusted untrusted = asInterfaceInstance(Untrusted.class, MethodHandles.zero(void.class));
        var instanceClass = untrusted.getClass();
        var leakLookup = Untrusted.leakLookup();
        assertEquals(Lookup.ORIGINAL, leakLookup.lookupModes() & Lookup.ORIGINAL, "Leaked lookup original flag");
        assertThrows(IllegalAccessException.class, () -> MethodHandles.privateLookupIn(instanceClass, Untrusted.leakLookup()));
    }

    @Test
    public void testNoInstantiation() throws ReflectiveOperationException {
        var mh = MethodHandles.zero(void.class);
        var instanceClass = asInterfaceInstance(Untrusted.class, mh).getClass();
        var ctor = instanceClass.getDeclaredConstructor(Lookup.class, MethodHandle.class, MethodHandle.class);

        assertThrows(IllegalAccessException.class, () -> ctor.newInstance(Untrusted.leakLookup(), mh, mh));
        assertThrows(IllegalAccessException.class, () -> ctor.newInstance(MethodHandles.publicLookup(), mh, mh));
    }

    void checkMethods(Method[] methods) {
        assertTrue(methods.length > 1, () -> "Should have more than 1 declared methods, found only " + Arrays.toString(methods));
        for (Method method : methods) {
            assertTrue(method.accessFlags().contains(AccessFlag.ABSTRACT), () -> method + " is not abstract");
        }
    }

    private Class<?> loadHidden() throws IllegalAccessException {
        ClassDesc baseCd = ClassDesc.of("BasicTest$HiddenItf");
        var objMtd = MethodTypeDesc.of(CD_Object);
        var baseBytes = Classfile.build(baseCd, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
            clb.withMethod("value", objMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
        });

        var lookup = MethodHandles.lookup();
        return lookup.defineHiddenClass(baseBytes, true).lookupClass();
    }

    // Base: Object value();
    // Child: Integer value(); int value();
    private List<Class<?>> loadBaseAndChild() throws IllegalAccessException {
        ClassDesc baseCd = ClassDesc.of("BasicTest$Base");
        ClassDesc childCd = ClassDesc.of("BasicTest$Child");
        var objMtd = MethodTypeDesc.of(CD_Object);
        var integerMtd = MethodTypeDesc.of(CD_Integer);
        var intMtd = MethodTypeDesc.of(CD_int);
        var chi = ClassHierarchyResolver.DEFAULT_CLASS_HIERARCHY_RESOLVER.orElse(
                ClassHierarchyResolver.of(List.of(baseCd, childCd), Map.ofEntries(Map.entry(baseCd, CD_Object),
                        Map.entry(childCd, CD_Object))));

        var baseBytes = Classfile.build(baseCd, List.of(Option.classHierarchyResolver(chi)), clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
            clb.withMethod("value", objMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
        });

        var lookup = MethodHandles.lookup();
        var base = lookup.ensureInitialized(lookup.defineClass(baseBytes));

        var childBytes = Classfile.build(childCd, List.of(Option.classHierarchyResolver(chi)), clb -> {
            clb.withSuperclass(CD_Object);
            clb.withInterfaceSymbols(baseCd);
            clb.withFlags(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
            clb.withMethod("value", integerMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
            clb.withMethod("value", intMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
        });

        var child = lookup.ensureInitialized(lookup.defineClass(childBytes));
        return List.of(base, child);
    }

    public interface MultiAbstractMethods {
        String a();
        String b();
    }

    public interface NoAbstractMethods {
        String toString();
    }
}

interface Inaccessible {
    Object value();
}

