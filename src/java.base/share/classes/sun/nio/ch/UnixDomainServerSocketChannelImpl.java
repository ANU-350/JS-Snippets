/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.BindException;
import java.net.NetPermission;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;

/**
 * An implementation of ServerSocketChannels
 */

public class UnixDomainServerSocketChannelImpl
    extends ServerSocketChannelImpl
{
    public UnixDomainServerSocketChannelImpl(SelectorProvider sp) throws IOException {
        super(sp, UnixDomainNet.socket(), false);
    }

    public UnixDomainServerSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd, bound);
    }

    SocketAddress implLocalAddress(FileDescriptor fd) throws IOException {
        return UnixDomainNet.localAddress(fd);
    }

    @Override
    public ServerSocket socket() {
        throw new UnsupportedOperationException("socket not supported");
    }

    @Override
    <T> void implSetOption(SocketOption<T> name, T value) throws IOException {
        Net.setSocketOption(getFD(), Net.UNSPEC, name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    <T> T implGetOption(SocketOption<T> name) throws IOException {
        return (T) Net.getSocketOption(getFD(), Net.UNSPEC, name);
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_RCVBUF);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    public SocketAddress implBind(SocketAddress local, int backlog) throws IOException {
        boolean found = false;

        UnixDomainNet.checkCapability();

        // Attempt up to 10 times to find an unused name in temp directory
        // Unlikely to fail
        for (int i = 0; i < 10; i++) {
            UnixDomainSocketAddress usa = null;
            if (local == null) {
                usa = getTempName();
            } else {
                usa = UnixDomainNet.checkAddress(local);
            }
            try {
                UnixDomainNet.bind(getFD(), usa.getPath());
                found = true;
                break;
            } catch (BindException e) {
                if (local != null) {
                    throw e;
                }
            }
        }
        if (!found)
            throw new IOException("could not bind to temporary name");
        Net.listen(getFD(), backlog < 1 ? 50 : backlog);
        return UnixDomainNet.localAddress(getFD());
    }

    private static Random getRandom() {
        try {
            return SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (NoSuchAlgorithmException e) {
            return new SecureRandom(); // This should not fail
        }
    }

    private static final Random random = getRandom();;

    /**
     * Return a possible temporary name to bind to, which is different for each call
     * Name is of the form <temp dir>/niosocket_<pid>_<random>
     */
    private static UnixDomainSocketAddress getTempName() throws IOException {
        int rnd = random.nextInt(Integer.MAX_VALUE);
        StringBuilder sb = new StringBuilder();
        sb.append(UnixDomainNet.tempDir).append("/niosocket_").append(rnd);
        return UnixDomainSocketAddress.of(sb.toString());
    }

    @Override
    protected int implAccept(FileDescriptor fd, FileDescriptor newfd, SocketAddress[] addrs)
        throws IOException
    {
        UnixDomainNet.checkCapability();
        String[] addrArray = new String[1];
        int n = UnixDomainNet.accept(fd, newfd, addrArray);
        if (n > 0) {
            addrs[0] = UnixDomainSocketAddress.of(addrArray[0]);
        }
        return n;
    }

    @Override
    String getRevealedLocalAddressAsString(SocketAddress addr) {
        return UnixDomainNet.getRevealedLocalAddressAsString((UnixDomainSocketAddress)addr);
    }

    @Override
    SocketAddress getRevealedLocalAddress(SocketAddress addr) {
        return UnixDomainNet.getRevealedLocalAddress((UnixDomainSocketAddress)addr);
    }

    SocketChannel implFinishAccept(FileDescriptor newfd, SocketAddress sa)
        throws IOException
    {
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        return new UnixDomainSocketChannelImpl(provider(), newfd, usa);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName());
        sb.append('[');
        if (!isOpen()) {
            sb.append("closed");
        } else {
            UnixDomainSocketAddress addr = (UnixDomainSocketAddress) localAddress();
            if (addr == null) { // TODO: ???
                sb.append("unbound");
            } else {
                sb.append(getRevealedLocalAddressAsString(addr));
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
