/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.InvalidKeyException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Objects;

import sun.security.util.math.*;
import sun.security.util.math.intpoly.*;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.internal.vm.annotation.ForceInline;

/**
 * This class represents the Poly1305 function defined in RFC 7539.
 *
 * This function is used in the implementation of ChaCha20/Poly1305
 * AEAD mode.
 */
final class Poly1305 {

    private static final int KEY_LENGTH = 32;
    private static final int RS_LENGTH = KEY_LENGTH / 2;
    private static final int BLOCK_LENGTH = 16;
    private static final int TAG_LENGTH = 16;

    private static final IntegerFieldModuloP ipl1305
            = IntegerPolynomial1305.ONE;

    private byte[] keyBytes;
    private final byte[] block = new byte[BLOCK_LENGTH];
    private int blockOffset;

    private IntegerModuloP r;
    private IntegerModuloP s;
    private MutableIntegerModuloP a;
    private final MutableIntegerModuloP n = ipl1305.get1().mutable();
    private final boolean checkWeakKey;

    Poly1305() { this(true); }
    Poly1305(boolean checkKey) { checkWeakKey = checkKey; }

    /**
     * Initialize the Poly1305 object
     *
     * @param newKey the {@code Key} which will be used for the authentication.
     * @param params this parameter is unused.
     *
     * @throws InvalidKeyException if {@code newKey} is {@code null} or is
     *      not 32 bytes in length.
     */
    void engineInit(Key newKey, AlgorithmParameterSpec params)
            throws InvalidKeyException {
        Objects.requireNonNull(newKey, "Null key provided during init");
        keyBytes = newKey.getEncoded();
        if (keyBytes == null) {
            throw new InvalidKeyException("Key does not support encoding");
        } else if (keyBytes.length != KEY_LENGTH) {
            throw new InvalidKeyException("Incorrect length for key: " +
                    keyBytes.length);
        }

        engineReset();
        setRSVals();
    }

    /**
     * Returns the length of the MAC (authentication tag).
     *
     * @return the length of the auth tag, which is always 16 bytes.
     */
    int engineGetMacLength() {
        return TAG_LENGTH;
    }

    /**
     * Reset the Poly1305 object, discarding any current operation but
     *      maintaining the same key.
     */
    void engineReset() {
        // Clear the block and reset the offset
        Arrays.fill(block, (byte)0);
        blockOffset = 0;
        // Discard any previous accumulator and start at zero
        a = ipl1305.get0().mutable();
    }

    /**
     * Update the MAC with bytes from a {@code ByteBuffer}
     *
     * @param buf the {@code ByteBuffer} containing the data to be consumed.
     *      Upon return the buffer's position will be equal to its limit.
     */
    void engineUpdate(ByteBuffer buf) {
        int remaining = buf.remaining();
        while (remaining > 0) {
            int bytesToWrite = Integer.min(remaining,
                    BLOCK_LENGTH - blockOffset);

            if (bytesToWrite >= BLOCK_LENGTH) {
                // Have at least one full block in the buf, process all full blocks
                int blockMultipleLength = remaining & (~(BLOCK_LENGTH-1));
                processMultipleBlocks(buf, blockMultipleLength);
                remaining -= blockMultipleLength;
            } else {
                // We have some left-over data from previous updates, so
                // copy that into the holding block until we get a full block.
                buf.get(block, blockOffset, bytesToWrite);
                blockOffset += bytesToWrite;

                if (blockOffset >= BLOCK_LENGTH) {
                    processBlock(block, 0, BLOCK_LENGTH);
                    blockOffset = 0;
                }
                remaining -= bytesToWrite;
            }
        }
    }

    /**
     * Update the MAC with bytes from an array.
     *
     * @param input the input bytes.
     * @param offset the starting index from which to update the MAC.
     * @param len the number of bytes to process.
     */
    void engineUpdate(byte[] input, int offset, int len) {
        Objects.checkFromIndexSize(offset, len, input.length);
        if (blockOffset > 0) {
            // We have some left-over data from previous updates
            int blockSpaceLeft = BLOCK_LENGTH - blockOffset;
            if (len < blockSpaceLeft) {
                System.arraycopy(input, offset, block, blockOffset, len);
                blockOffset += len;
                return; // block wasn't filled
            } else {
                System.arraycopy(input, offset, block, blockOffset,
                        blockSpaceLeft);
                offset += blockSpaceLeft;
                len -= blockSpaceLeft;
                processBlock(block, 0, BLOCK_LENGTH);
                blockOffset = 0;
            }
        }

        int blockMultipleLength = len & (~(BLOCK_LENGTH-1));
        long[] aLimbs = a.getLimbs();
        long[] rLimbs = r.getLimbs();
        processMultipleBlocksCheck(input, offset, blockMultipleLength, aLimbs, rLimbs);
        processMultipleBlocks(input, offset, blockMultipleLength, aLimbs, rLimbs);
        offset += blockMultipleLength;
        len -= blockMultipleLength;

        if (len > 0) { // and len < BLOCK_LENGTH
            System.arraycopy(input, offset, block, 0, len);
            blockOffset = len;
        }
    }

    /**
     * Update the MAC with a single byte of input
     *
     * @param input the byte to update the MAC with.
     */
    void engineUpdate(byte input) {
        assert (blockOffset < BLOCK_LENGTH);
        // we can't hold fully filled unprocessed block
        block[blockOffset++] = input;

        if (blockOffset == BLOCK_LENGTH) {
            processBlock(block, 0, BLOCK_LENGTH);
            blockOffset = 0;
        }
    }


    /**
     * Finish the authentication operation and reset the MAC for a new
     * authentication operation.
     *
     * @return the authentication tag as a byte array.
     */
    byte[] engineDoFinal() {
        byte[] tag = new byte[BLOCK_LENGTH];

        // Finish up: process any remaining data < BLOCK_SIZE, then
        // create the tag from the resulting little-endian integer.
        if (blockOffset > 0) {
            processBlock(block, 0, blockOffset);
            blockOffset = 0;
        }

        // Add in the s-half of the key to the accumulator
        a.addModPowerTwo(s, tag);

        // Reset for the next auth
        engineReset();
        return tag;
    }

    private static final String BLAH = System.getenv("APH_FOO_BAX");

    static {
        if (BLAH == null) {
            System.out.println("BLAH not set");
        }
    }

    /**
     * Process a single block of data.  This should only be called
     * when the block array is complete.  That may not necessarily
     * be a full 16 bytes if the last block has less than 16 bytes.
     */
    private void processBlock(ByteBuffer buf, int len) {
        var printing = ("yes".equals(BLAH));
        n.setValue(buf, len, (byte)0x01);
        if (printing) {
            System.out.println("n = " + n.asBigInteger().toString(16));
        }
        a.setSum(n);                    // a += (n | 0x01)
        a.setProduct(r);                // a = (a * r) % p
    }

    private void processBlock1(byte[] block, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, block.length);
        n.setValue(block, offset, length, (byte)0x01);
        var printing = ("yes".equals(BLAH));
        if (printing) {
            System.out.println("a0 = " + a.asBigInteger().toString(16));
            System.out.println("n = " + n.asBigInteger().toString(16));
        }
        a.setSum(n);                    // a += (n | 0x01)
        if (printing) {
            System.out.println("a = " + a.asBigInteger().toString(16));
        }
        a.setProduct(r);                // a = (a * r) % p
        if (printing) {
            System.out.println("r = " + r.asBigInteger().toString(16));
            System.out.println("x = " + a.asBigInteger().toString(16));
            System.out.print("limbs = ");
            long[] limbs = a.getLimbs();
            for (int i = limbs.length - 1; i >= 0; --i) {
                System.out.print(Long.toString(limbs[i], 16) + ":");
            }
            System.out.println();
        }
    }

    private void processBlock(byte[] block, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, block.length);
        n.setValue(block, offset, length, (byte)0x01);
        a.setSum(n);                    // a += (n | 0x01)
        a.setProduct(r);                // a = (a * r) % p
    }

    boolean printing = ("yes".equals(BLAH));

    // This is an intrinsified method. The unused parameters aLimbs and rLimbs are used by the intrinsic.
    // They correspond to this.a and this.r respectively
    @ForceInline
    @IntrinsicCandidate
    private void processMultipleBlocks(byte[] input, int offset, int length, long[] aLimbs, long[] rLimbs) {
        if (length >= BLOCK_LENGTH * 2) {
            IntegerModuloP rSquared = r.square();
            IntegerModuloP a0 = a.mutable();
            IntegerModuloP a1 = ipl1305.get0();

            MutableIntegerModuloP n0 = ipl1305.get1().mutable();
            MutableIntegerModuloP n1 = ipl1305.get1().mutable();

            if (printing) {
                System.out.printf("init:\n    a0 = %33.33s  r = %33.33s \n", a0, r);
                System.out.printf("  r**2 = %33.33s \n", rSquared);
            }

            while (length >= BLOCK_LENGTH * 4) {

                n0.setValue(input, offset, BLOCK_LENGTH, (byte) 0x01);
                offset += BLOCK_LENGTH;
                length -= BLOCK_LENGTH;
                n1.setValue(input, offset, BLOCK_LENGTH, (byte) 0x01);
                offset += BLOCK_LENGTH;
                length -= BLOCK_LENGTH;

                var s0 = a0.add(n0);
                a0 = s0.multiply(rSquared);
                var s1 = a1.add(n1);
                a1 = s1.multiply(rSquared);
                if (printing) {
                    System.out.printf("##  n0 = %33.33s  n1 = %33.33s\n", n0, n1);
                    System.out.printf("##  s0 = %33.33s  s1 = %33.33s\n", s0, s1);
                    System.out.printf("##  a0 = %33.33s  a1 = %33.33s\n", a0, a1);
                }

            }

            if ("".length() != 0) {
                n0.setValue(input, offset, BLOCK_LENGTH, (byte) 0x01);

                offset += BLOCK_LENGTH;
                length -= BLOCK_LENGTH;

                var s0 = a0.add(n0);
                a0 = s0.multiply(rSquared);

                n1.setValue(input, offset, BLOCK_LENGTH, (byte) 0x01);
                offset += BLOCK_LENGTH;
                length -= BLOCK_LENGTH;

                var s1 = a1.add(n1);
                a1 = s1.multiply(r);

                a.setValue(a0.add(a1));
                if (printing) {
                    System.out.printf("    n0 = %33.33s  n1 = %33.33s\n", n0, n1);
                    System.out.printf("    s0 = %33.33s  s1 = %33.33s\n", s0, s1);
                    System.out.printf("    a0 = %33.33s  a1 = %33.33s\n", a0, a1);
                    System.out.printf("     a = %33.33s\n", a);
                    System.out.println("");
                }
            } else {
                var sum = ipl1305.get0();

                n0.setValue(input, offset, BLOCK_LENGTH, (byte) 0x01);
                offset += BLOCK_LENGTH;
                length -= BLOCK_LENGTH;

                if (printing) {
                    System.out.printf("    n0 = %33.33s              \n", n0);
		}
                sum = sum.add(a0);
                if (printing) {
                    System.out.printf("   sum = %33.33s              \n", sum);
		}
                sum = sum.add(n0);
                if (printing) {
                    System.out.printf("   sum = %33.33s              \n", sum);
		}
                sum = sum.multiply(r);
                if (printing) {
                    System.out.printf("   sum = %33.33s              \n", sum);
		    System.out.println();
		}

                n1.setValue(input, offset, BLOCK_LENGTH, (byte) 0x01);
                offset += BLOCK_LENGTH;
                length -= BLOCK_LENGTH;

                if (printing) {
                    System.out.printf("    n1 = %33.33s              \n", n1);
		}
                sum = sum.add(a1);
                if (printing) {
                    System.out.printf("   sum = %33.33s              \n", sum);
		}
                sum = sum.add(n1);
                if (printing) {
                    System.out.printf("   sum = %33.33s              \n", sum);
		}
                sum = sum.multiply(r);
                if (printing) {
                    System.out.printf("   sum = %33.33s              \n", sum);
		    System.out.println();
		}

                a.setValue(sum);
            }
        }

        while (length >= BLOCK_LENGTH) {
            processBlock(input, offset, BLOCK_LENGTH);
            offset += BLOCK_LENGTH;
            length -= BLOCK_LENGTH;
        }
        System.out.print("");
    }

    private void processMultipleBlocks(ByteBuffer buf, int blockMultipleLength) {
        if (buf.hasArray()) {
            byte[] input = buf.array();
            int offset = buf.arrayOffset() + buf.position();
            long[] aLimbs = a.getLimbs();
            long[] rLimbs = r.getLimbs();

            processMultipleBlocksCheck(input, offset, blockMultipleLength, aLimbs, rLimbs);
            processMultipleBlocks(input, offset, blockMultipleLength, aLimbs, rLimbs);
            buf.position(offset + blockMultipleLength);
        } else {
            while (blockMultipleLength >= BLOCK_LENGTH) {
                processBlock(buf, BLOCK_LENGTH);
                blockMultipleLength -= BLOCK_LENGTH;
            }
        }
    }

    private static void processMultipleBlocksCheck(byte[] input, int offset, int length, long[] aLimbs, long[] rLimbs) {
        Objects.checkFromIndexSize(offset, length, input.length);
        final int numLimbs = 5; // Intrinsic expects exactly 5 limbs
        if (aLimbs.length != numLimbs) {
            throw new RuntimeException("invalid accumulator length: " + aLimbs.length);
        }
        if (rLimbs.length != numLimbs) {
            throw new RuntimeException("invalid R length: " + rLimbs.length);
        }
    }

    /**
     * Partition the authentication key into the R and S components, clamp
     * the R value, and instantiate IntegerModuloP objects to R and S's
     * numeric values.
     */
    private void setRSVals() throws InvalidKeyException {
        // Clamp the bytes in the "r" half of the key.
        keyBytes[3] &= 15;
        keyBytes[7] &= 15;
        keyBytes[11] &= 15;
        keyBytes[15] &= 15;
        keyBytes[4] &= (byte)252;
        keyBytes[8] &= (byte)252;
        keyBytes[12] &= (byte)252;

        if (checkWeakKey) {
            byte keyIsZero = 0;
            for (int i = 0; i < RS_LENGTH; i++) {
                keyIsZero |= keyBytes[i];
            }
            if (keyIsZero == 0) {
                throw new InvalidKeyException("R is set to zero");
            }

            keyIsZero = 0;
            for (int i = RS_LENGTH; i < 2*RS_LENGTH; i++) {
                keyIsZero |= keyBytes[i];
            }
            if (keyIsZero == 0) {
                throw new InvalidKeyException("S is set to zero");
            }
        }

        // Create IntegerModuloP elements from the r and s values
        r = ipl1305.getElement(keyBytes, 0, RS_LENGTH, (byte)0);
        s = ipl1305.getElement(keyBytes, RS_LENGTH, RS_LENGTH, (byte)0);
    }
}
