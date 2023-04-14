/*
 * Copyright 2012 Google, Inc.  All Rights Reserved.
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

/**
 * @test
 * @bug 8056934
 * @summary Verify the ability to read zip files whose local header
 * data descriptor is missing the optional signature
 * <p>
 * No way to adapt the technique in this test to get a ZIP64 zip file
 * without data descriptors was found.
 * @run testng DataDescriptorSignatureMissing
 */

import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class DataDescriptorSignatureMissing {

    /**
     * Verify that ZipInputStream correctly parses a ZIP with a Data Descriptor without
     * the recommended but optional signature.
     */
    @Test
    public void shouldParseSignaturelessDescriptor() throws IOException {
        // The ZIP with a signature-less descriptor
        byte[] zip = makeZipWithSignaturelessDescriptor();

        // ZipInputStream should read the signature-less data descriptor
        try (ZipInputStream in = new ZipInputStream(
                new ByteArrayInputStream(zip))) {
            ZipEntry first = in.getNextEntry();
            assertNotNull(first, "Zip file is unexpectedly missing first entry");
            assertEquals(first.getName(), "first");
            assertEquals(in.readAllBytes(), "first".getBytes(StandardCharsets.UTF_8));

            ZipEntry second = in.getNextEntry();
            assertNotNull(second, "Zip file is unexpectedly missing second entry");
            assertEquals(second.getName(), "second");
            assertEquals(in.readAllBytes(), "second".getBytes(StandardCharsets.UTF_8));
        }

    }

    /**
     * Produce a ZIP file where the first entry has a signature-less data descriptor
     */
    private static byte[] makeZipWithSignaturelessDescriptor() throws IOException {
        // Offset of the signed data descriptor
        int sigOffset;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            // Write a first entry
            zo.putNextEntry(new ZipEntry("first"));
            zo.write("first".getBytes(StandardCharsets.UTF_8));
            // Force the data descriptor to be written out
            zo.closeEntry();
            // Signed data descriptor starts 16 bytes before current offset
            sigOffset = out.size() - 4 * Integer.BYTES;
            // Add a second entry
            zo.putNextEntry(new ZipEntry("second"));
            zo.write("second".getBytes(StandardCharsets.UTF_8));
        }

        // The generated ZIP file with a signed data descriptor
        byte[] sigZip = out.toByteArray();

        // The offset of the CRC immediately following the 4-byte signature
        int crcOffset = sigOffset + Integer.BYTES;

        // Create a ZIP file with a signature-less data descriptor for the first entry
        ByteArrayOutputStream sigLess = new ByteArrayOutputStream();
        sigLess.write(sigZip, 0, sigOffset);
        // Skip the signature
        sigLess.write(sigZip, crcOffset, sigZip.length - crcOffset);

        byte[] siglessZip = sigLess.toByteArray();

        // Adjust the CEN offset in the END header
        ByteBuffer buffer = ByteBuffer.wrap(siglessZip).order(ByteOrder.LITTLE_ENDIAN);
        // Reduce cenOffset by 4 bytes
        int cenOff = siglessZip.length - ZipFile.ENDHDR + ZipFile.ENDOFF;
        int realCenOff = buffer.getInt(cenOff) - Integer.BYTES;
        buffer.putInt(cenOff, realCenOff);

        // Adjust the LOC offset in the second CEN header
        int cen = realCenOff;
        // Skip past the first CEN header
        int nlen = buffer.getShort(cen + ZipFile.CENNAM);
        cen += ZipFile.CENHDR + nlen;

        // Reduce LOC offset by 4 bytes
        int locOff = cen + ZipFile.CENOFF;
        buffer.putInt(locOff, buffer.getInt(locOff) - Integer.BYTES);

        return siglessZip;
    }
}
