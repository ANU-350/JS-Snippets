/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * The interface Decoder.
 *
 * @param <T> the type parameter
 */
public interface Decoder<T> {
    /**
     * Decode t.
     *
     * @param <S>    the type parameter
     * @param string the string
     * @param tClass the t class
     * @return the t
     * @throws IOException the io exception
     */
    <S extends T> S decode(String string, Class <S> tClass) throws IOException;

    /**
     * Decode t.
     *
     * @param <S>    the type parameter
     * @param reader the reader
     * @param tClass the t class
     * @return the t
     * @throws IOException the io exception
     */
    <S extends T> S decode(InputStream reader, Class <S> tClass) throws IOException;

    /**
     * Decode t.
     *
     * @param string the string
     * @return the t
     * @throws IOException the io exception
     */
    T decode(String string) throws IOException;

    /**
     * Decode t.
     *
     * @param reader the reader
     * @return the t
     * @throws IOException the io exception
     */
    T decode(InputStream reader) throws IOException;
}

