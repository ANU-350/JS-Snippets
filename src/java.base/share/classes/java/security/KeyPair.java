/*
 * Copyright (c) 1996, 2021, Oracle and/or its affiliates. All rights reserved.
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

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.io.Serializable;

/**
 * This class is a simple holder for a key pair (a public key and a
 * private key). It does not enforce any security, and, when initialized,
 * should be treated like a PrivateKey.
 *
 * @see PublicKey
 * @see PrivateKey
 *
 * @author Benjamin Renaud
 * @since 1.1
 */

public final class KeyPair implements Serializable, Destroyable {

    @java.io.Serial
    private static final long serialVersionUID = -7565189502268009837L;

    /** The private key. */
    private final PrivateKey privateKey;

    /** The public key. */
    private final PublicKey publicKey;

    /**
     * Constructs a key pair from the given public key and private key.
     *
     * <p>Note that this constructor only stores references to the public
     * and private key components in the generated key pair. This is safe,
     * because {@code Key} objects are immutable.
     *
     * @param publicKey the public key.
     *
     * @param privateKey the private key.
     */
    public KeyPair(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    /**
     * Returns a reference to the public key component of this key pair.
     *
     * @return a reference to the public key.
     */
    public PublicKey getPublic() {
        return publicKey;
    }

    /**
     * Returns a reference to the private key component of this key pair.
     *
     * @return a reference to the private key.
     */
    public PrivateKey getPrivate() {
        return privateKey;
    }

    /**
     * Check if the private key has been destroyed.
     *
     * @return true is if the private key has been destroyed.
     *
     * @since 18
     */
    @Override
    public boolean isDestroyed() {
        return (privateKey == null || privateKey.isDestroyed());
    }

    /**
     * Call to destroy the private key in this key pair. DestroyFailedException
     * will be thrown if the private key object does not implement a destroy
     * method.
     *
     * @throws DestroyFailedException if the destroy operation fails or there is
     * no underlying destroy method.
     *
     * @since 18
     */
    @Override
    public void destroy() throws DestroyFailedException {
        if (privateKey != null) {
            privateKey.destroy();
        }
    }
}
