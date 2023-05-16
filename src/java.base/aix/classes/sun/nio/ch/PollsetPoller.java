/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, IBM Corp.
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

import java.io.IOException;
import java.time.Instant;
import sun.nio.ch.Pollset;

/**
 * Poller implementation based on the AIX Pollset library.
 */

class PollsetPoller extends Poller {

    static { Pollset.init(); /* Dynamically loads pollset C functions */ }

    private int setid;
    private int setsize;

    PollsetPoller(boolean read) throws IOException {
        super(read);
        this.setsize = 0;
        this.setid = Pollset.pollsetCreate();
    }

    @Override
    int fdVal() {
        return setid;
    }

    @Override
    void implRegister(int fd) throws IOException {
        int ret = Pollset.pollsetCtl(setid, Pollset.PS_MOD, fd,
        Pollset.PS_POLLPRI | (this.reading() ? Net.POLLIN : Net.POLLOUT));
        if (ret != 0) {
            throw new IOException("Unable to register fd " + fd);
        }
        setsize++;
    }

    @Override
    void implDeregister(int fd) {
        assert (setsize > 0);
        int ret = Pollset.pollsetCtl(setid, Pollset.PS_DELETE, fd, 0);
        assert ret == 0;
        setsize--;
    }

    /**
      * Main poll method. The AIX Pollset library does not appear to pick up changes to the pollset
      * (the set of fds being polled) while blocked on a call to this method. These changes happen
      * regularly in the poll-loop thread and update thread from Poller.java.
      * To address this difficulty, we break poll calls into 100ms sub-calls and emulate the timout.
      */
    @Override
    int poll(int timeout) throws IOException {
        int n;
        switch (timeout) {
            case 0:
                n = pollInner(0);
                break;
            case Pollset.PS_NO_TIMEOUT:
                do { n = pollInner(100); } while (n == 0);
                break;
            default:
                Instant end = Instant.now().plusMillis(timeout);
                do { n = pollInner(100); } while (n == 0 && Instant.now().isBefore(end));
                break;
        }
        return n;
    }

    int pollInner(int subInterval) throws IOException {
        // The poll loop may start polling before any fds have been registered. But, if we use set
        // size 0 to allocatePollArray, it will return the null address and pollsetPoll will complain.
        // We avoid that by just passing set size 1, and letting poll block for subInterval.
        long buffer = Pollset.allocatePollArray(setsize > 0 ? setsize : 1);
        int n = Pollset.pollsetPoll(setid, buffer, setsize, subInterval);
        for(int i=0; i<n; i++) {
            long eventAddress = Pollset.getEvent(buffer, i);
            int fd = Pollset.getDescriptor(eventAddress);
            polled(fd);
        }
        Pollset.freePollArray(buffer);
        return n;
    }
}

