/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile;

import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import jdk.internal.classfile.impl.Util;

import jdk.internal.classfile.impl.ClassHierarchyImpl;

/**
 * Provides class hierarchy information for generating correct stack maps
 * during code building.
 */
@FunctionalInterface
public interface ClassHierarchyResolver {

    /**
     * Default singleton instance of {@linkplain ClassHierarchyResolver}
     * using {@link ClassLoader#getSystemResourceAsStream(String)}
     * as the {@code ClassStreamResolver}
     */
    ClassHierarchyResolver DEFAULT_CLASS_HIERARCHY_RESOLVER
            = new ClassHierarchyImpl.CachedClassHierarchyResolver(
            new Function<ClassDesc, InputStream>() {
                @Override
                public InputStream apply(ClassDesc classDesc) {
                    return ClassLoader.getSystemResourceAsStream(Util.toInternalName(classDesc) + ".class");
                }
            }).orElse(of(ClassLoader.getSystemClassLoader()));

    /**
     * {@return the {@link ClassHierarchyInfo} for a given class name, or null
     * if the name is unknown to the resolver}
     * @param classDesc descriptor of the class
     */
    ClassHierarchyInfo getClassInfo(ClassDesc classDesc);

    /**
     * Chains this {@linkplain ClassHierarchyResolver} with another to be
     * consulted if this resolver does not know about the specified class.
     *
     * @param other the other resolver
     * @return the chained resolver
     */
    default ClassHierarchyResolver orElse(ClassHierarchyResolver other) {
        return new ClassHierarchyResolver() {
            @Override
            public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
                var chi = ClassHierarchyResolver.this.getClassInfo(classDesc);
                if (chi == null)
                    chi = other.getClassInfo(classDesc);
                return chi;
            }
        };
    }

    /**
     * Information about a resolved class.
     * @param thisClass descriptor of this class
     * @param isInterface whether this class is an interface
     * @param superClass descriptor of the superclass (not relevant for interfaces)
     */
    public record ClassHierarchyInfo(ClassDesc thisClass, boolean isInterface, ClassDesc superClass) {
    }

    /**
     * Returns a {@linkplain  ClassHierarchyResolver} that extracts class hierarchy
     * information from classfiles located by a mapping function
     *
     * @param classStreamResolver maps class descriptors to classfile input streams
     * @return the {@linkplain ClassHierarchyResolver}
     */
    public static ClassHierarchyResolver ofCached(Function<ClassDesc, InputStream> classStreamResolver) {
        return new ClassHierarchyImpl.CachedClassHierarchyResolver(classStreamResolver);
    }

    /**
     * Returns a {@linkplain  ClassHierarchyResolver} that extracts class hierarchy
     * information from collections of class hierarchy metadata
     *
     * @param interfaces a collection of classes known to be interfaces
     * @param classToSuperClass a map from classes to their super classes
     * @return the {@linkplain ClassHierarchyResolver}
     */
    public static ClassHierarchyResolver of(Collection<ClassDesc> interfaces,
                                            Map<ClassDesc, ClassDesc> classToSuperClass) {
        return new ClassHierarchyImpl.StaticClassHierarchyResolver(interfaces, classToSuperClass);
    }

    /**
     * Returns a ClassHierarchyResolver that extracts class hierarchy information via
     * the Reflection API with a {@linkplain ClassLoader}.
     *
     * @param loader the class loader
     * @return the class hierarchy resolver
     */
    static ClassHierarchyResolver of(ClassLoader loader) {
        return new ClassHierarchyImpl.ReflectionClassHierarchyResolver() {
            @Override
            protected Class<?> resolve(ClassDesc cd) {
                try {
                    return Class.forName(Util.toBinaryName(cd.descriptorString()), false, loader);
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            }
        };
    }

    /**
     * Returns a ClassHierarchyResolver that extracts class hierarchy information via
     * the Reflection API with a {@linkplain MethodHandles.Lookup Lookup}. The classes
     * resolved must be accessible to this given lookup.
     *
     * @param lookup the lookup, must be able to access classes to resolve
     * @return the class hierarchy resolver
     */
    static ClassHierarchyResolver of(MethodHandles.Lookup lookup) {
        return new ClassHierarchyImpl.ReflectionClassHierarchyResolver() {
            @Override
            protected Class<?> resolve(ClassDesc cd) {
                try {
                    return (Class<?>) cd.resolveConstantDesc(lookup);
                } catch (ReflectiveOperationException ex) {
                    // Should we handle IllegalAccessException differently?
                    return null;
                }
            }
        };
    }
}
