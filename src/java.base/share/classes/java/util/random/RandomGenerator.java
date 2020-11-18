/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.util.random;

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomSupport.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * The {@link RandomGenerator} interface is designed to provide a common protocol for objects that
 * generate random or (more typically) pseudorandom sequences of numbers (or Boolean values).
 * Such a sequence may be obtained by either repeatedly invoking a method that returns a single
 * (pseudo)randomly chosen value, or by invoking a method that returns a stream of (pseudo)randomly
 * chosen values.
 * <p>
 * Ideally, given an implicitly or explicitly specified range of values, each value would be chosen
 * independently and uniformly from that range. In practice, one may have to settle for some
 * approximation to independence and uniformity.
 * <p>
 * In the case of {@code int}, {@code long}, and {@code boolean} values, if there is no explicit
 * specification of range, then the range includes all possible values of the type.  In the case of
 * {@code float} and {@code double} values, first a value is always chosen uniformly from the set of
 * 2<sup><i>w</i></sup> values between 0.0 (inclusive) and 1.0 (exclusive), where <i>w</i> is 23 for
 * {@code float} values and 52 for {@code double} values, such that adjacent values differ by
 * 2<sup>&minus;<i>w</i></sup> (notice that this set is a <i>subset</i> of the set of
 * <i>all representable floating-point values</i> between 0.0 (inclusive) and 1.0 (exclusive));
 * then if an explicit range was specified, then the chosen number is computationally scaled
 * and translated so as to appear to have been chosen approximately uniformly from that explicit range.
 * <p>
 * Each method that returns a stream produces a stream of values each of which is chosen in the same
 * manner as for a method that returns a single (pseudo)randomly chosen value.  For example, if
 * {@code r} implements {@link RandomGenerator}, then the method call {@code r.ints(100)} returns a
 * stream of 100 {@code int} values.  These are not necessarily the exact same values that would
 * have been returned if instead {@code r.nextInt()} had been called 100 times; all that is
 * guaranteed is that each value in the stream is chosen in a similar (pseudo)random manner from the
 * same range.
 * <p>
 * Every object that implements the {@link RandomGenerator} interface by using a
 * pseudorandom algorithm is assumed to contain a finite amount of state.  Using such an object to
 * generate a pseudorandomly chosen value alters its state by computing a new state as a function
 * of the current state, without reference to any information other than the current state.
 * The number of distinct possible states of such an object is called its <i>period</i>.
 * (Some implementations of the {@link RandomGenerator} interface may be truly random
 * rather than pseudorandom, for example relying on the statistical behavior of a physical
 * object to derive chosen values.  Such implementations do not have a fixed period.)
 * <p>
 * As a rule, objects that implement the {@link RandomGenerator} interface need not be thread-safe.
 * It is recommended that multithreaded applications use either {@link ThreadLocalRandom} or
 * (preferably) pseudorandom number generators that implement the {@link SplittableGenerator} or
 * {@link JumpableGenerator} interface.
 * <p>
 * To implement this interface, a class only needs to provide concrete definitions for the methods
 * {@code nextLong()} and {@code period()}. Default implementations are provided for all other
 * methods (but it may be desirable to override some of them, especially {@code nextInt()} if the
 * underlying algorithm is {@code int}-based). Moreover, it may be preferable instead to implement
 * a more specialized interface such as {@link JumpableGenerator} or {@link LeapableGenerator},
 * or to extend an abstract implementation-support class such as {@link AbstractSplittableGenerator}
 * or {@link AbstractArbitrarilyJumpableGenerator}.
 * <p>
 * Objects that implement {@link RandomGenerator} are typically not cryptographically secure.
 * Consider instead using {@link java.security.SecureRandom} to get a cryptographically secure
 * pseudorandom number generator for use by security-sensitive applications.  Note, however, that
 * {@code java.security.SecureRandom} does implement the {@link RandomGenerator} interface, so that
 * instances of {@code java.security.SecureRandom} may be used interchangeably with other types of
 * pseudorandom generators in applications that do not require a secure generator.
 *
 * @since 16
 */
public interface RandomGenerator {
    /**
     * Returns an instance of {@link RandomGenerator} that utilizes the
     * {@code name} algorithm.
     *
     * @param name  Name of random number generator algorithm
     *
     * @return An instance of {@link RandomGenerator}
     */
    public static RandomGenerator of(String name) {
        Objects.requireNonNull(name);
        return RandomGeneratorFactory.of(name, RandomGenerator.class);
    }

    /**
     * Returns a {@link RandomGeneratorFactory} that can produce instances
     * of {@link RandomGenerator} that utilize the {@code name} algorithm.
     *
     * @param name  Name of random number generator algorithm
     *
     * @return {@link RandomGeneratorFactory} of {@link RandomGenerator}
     */
    public static RandomGeneratorFactory<RandomGenerator> factoryOf(String name) {
        Objects.requireNonNull(name);
        return RandomGeneratorFactory.factoryOf(name, RandomGenerator.class);
    }

    /**
     * Returns a stream of all available RandomGeneratorFactory(s).
     *
     * @return Stream of all available RandomGeneratorFactory(s).
     */
    public static Stream<RandomGeneratorFactory<RandomGenerator>> all() {
        return RandomGeneratorFactory.all(RandomGenerator.class);
    }

    /**
     * Returns an effectively unlimited stream of (pseudo)randomly chosen
     * {@code double} values.
     *
     * @return a stream of (pseudo)randomly chosen {@code double} values
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code doubles(Long.MAX_VALUE)}.
     *
     * @implNote The default implementation produces a sequential stream
     *           that repeatedly calls {@code nextDouble()}.
     */
    default DoubleStream doubles() {
        return DoubleStream.generate(this::nextDouble).sequential();
    }

    /**
     * Returns an effectively unlimited stream of (pseudo)randomly chosen
     * {@code double} values, where each value is between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     *
     * @return a stream of (pseudo)randomly chosen {@code double} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code randomNumberOrigin} is not finite,
     *         or {@code randomNumberBound} is not finite, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     *
     * @implNote It is permitted to implement this method in a manner equivalent to
     *           {@code doubles(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     * @implNote The default implementation produces a sequential stream that repeatedly
     *           calls {@code nextDouble(randomNumberOrigin, randomNumberBound)}.
     */
    default DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return DoubleStream.generate(() -> nextDouble(randomNumberOrigin, randomNumberBound)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * (pseudo)randomly chosen {@code double} values.
     *
     * @param streamSize the number of values to generate
     *
     * @return a stream of (pseudo)randomly chosen {@code double} values
     *
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextDouble()}.
     */
    default DoubleStream doubles(long streamSize) {
        RandomSupport.checkStreamSize(streamSize);
        return doubles().limit(streamSize);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * (pseudo)randomly chosen {@code double} values, where each value is between
     * the specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     *
     * @return a stream of (pseudo)randomly chosen {@code double} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code streamSize} is less than zero,
     *         or {@code randomNumberOrigin} is not finite,
     *         or {@code randomNumberBound} is not finite, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     *
     * @implNote The default implementation produces a sequential stream that repeatedly
     *           calls {@code nextDouble(randomNumberOrigin, randomNumberBound)}.
     */
    default DoubleStream doubles(long streamSize, double randomNumberOrigin,
                double randomNumberBound) {
        RandomSupport.checkStreamSize(streamSize);
        RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return doubles(randomNumberOrigin, randomNumberBound).limit(streamSize);
    }

    /**
     * Returns an effectively unlimited stream of (pseudo)randomly chosen
     * {@code int} values.
     *
     * @return a stream of (pseudo)randomly chosen {@code int} values
     *
     * @implNote It is permitted to implement this method in a manner
     *           equivalent to {@code ints(Long.MAX_VALUE)}.
     * @implNote The default implementation produces a sequential stream
     *           that repeatedly calls {@code nextInt()}.
     */
    default IntStream ints() {
        return IntStream.generate(this::nextInt).sequential();
    }

    /**
     * Returns an effectively unlimited stream of (pseudo)randomly chosen
     * {@code int} values, where each value is between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     *
     * @return a stream of (pseudo)randomly chosen {@code int} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     *
     * @implNote It is permitted to implement this method in a manner equivalent to
     *           {@code ints(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     * @implNote The default implementation produces a sequential stream that repeatedly
     *           calls {@code nextInt(randomNumberOrigin, randomNumberBound)}.
     */
    default IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return IntStream.generate(() -> nextInt(randomNumberOrigin, randomNumberBound)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * (pseudo)randomly chosen {@code int} values.
     *
     * @param streamSize the number of values to generate
     *
     * @return a stream of (pseudo)randomly chosen {@code int} values
     *
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     *
     * @implNote The default implementation produces a sequential stream
     *           that repeatedly calls {@code nextInt()}.
     */
    default IntStream ints(long streamSize) {
        RandomSupport.checkStreamSize(streamSize);
        return ints().limit(streamSize);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * (pseudo)randomly chosen {@code int} values, where each value is between
     * the specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     *
     * @return a stream of (pseudo)randomly chosen {@code int} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     *
     * @implNote The default implementation produces a sequential stream that repeatedly
     *           calls {@code nextInt(randomNumberOrigin, randomNumberBound)}.
     */
    default IntStream ints(long streamSize, int randomNumberOrigin,
              int randomNumberBound) {
        RandomSupport.checkStreamSize(streamSize);
        RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return ints(randomNumberOrigin, randomNumberBound).limit(streamSize);
    }

    /**
     * Returns an effectively unlimited stream of (pseudo)randomly chosen
     * {@code long} values.
     *
     * @return a stream of (pseudo)randomly chosen {@code long} values
     *
     * @implNote It is permitted to implement this method in a manner
     *           equivalent to {@code longs(Long.MAX_VALUE)}.
     * @implNote The default implementation produces a sequential stream
     *           that repeatedly calls {@code nextLong()}.
     */
    default LongStream longs() {
        return LongStream.generate(this::nextLong).sequential();
    }

    /**
     * Returns an effectively unlimited stream of (pseudo)randomly chosen
     * {@code long} values, where each value is between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     *
     * @return a stream of (pseudo)randomly chosen {@code long} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     *
     * @implNote It is permitted to implement this method in a manner equivalent to
     *           {@code longs(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     * @implNote The default implementation produces a sequential stream that repeatedly
     *           calls {@code nextLong(randomNumberOrigin, randomNumberBound)}.
     */
    default LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return LongStream.generate(() -> nextLong(randomNumberOrigin, randomNumberBound)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * (pseudo)randomly chosen {@code long} values.
     *
     * @param streamSize the number of values to generate
     *
     * @return a stream of (pseudo)randomly chosen {@code long} values
     *
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextLong()}.
     */
    default LongStream longs(long streamSize) {
        RandomSupport.checkStreamSize(streamSize);
        return longs().limit(streamSize);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * (pseudo)randomly chosen {@code long} values, where each value is between
     * the specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     *
     * @return a stream of (pseudo)randomly chosen {@code long} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     *
     * @implNote The default implementation produces a sequential stream that repeatedly
     *            calls {@code nextLong(randomNumberOrigin, randomNumberBound)}.
     */
    default LongStream longs(long streamSize, long randomNumberOrigin,
                long randomNumberBound) {
        RandomSupport.checkStreamSize(streamSize);
        RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return longs(randomNumberOrigin, randomNumberBound).limit(streamSize);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code boolean} value.
     * <p>
     * The default implementation tests the high-order bit (sign bit) of a value produced by
     * {@code nextInt()}, on the grounds that some algorithms for pseudorandom number generation
     * produce values whose high-order bits have better statistical quality than the low-order bits.
     *
     * @return a (pseudo)randomly chosen {@code boolean} value
     */
    default boolean nextBoolean() {
        return nextInt() < 0;
    }

    /**
     * Fills a user-supplied byte array with generated byte values (pseudo)randomly chosen
     * uniformly from the range of values between -128 (inclusive) and 255 (inclusive).
     *
     * @param  bytes the byte array to fill with pseudorandom bytes
     * @throws NullPointerException if bytes is null
     * @since  10
     */
    default void nextBytes(byte[] bytes) {
        int i = 0;
        int len = bytes.length;
        for (int words = len >> 3; words--> 0; ) {
            long rnd = nextLong();
            for (int n = 8; n--> 0; rnd >>>= Byte.SIZE)
                bytes[i++] = (byte)rnd;
        }
        if (i < len)
            for (long rnd = nextLong(); i < len; rnd >>>= Byte.SIZE)
                bytes[i++] = (byte)rnd;
    }

    /**
     * Returns a pseudorandom {@code float} value between zero (inclusive) and one (exclusive).
     * <p>
     * The default implementation uses the 24 high-order bits from a call to {@code nextInt()}.
     *
     * @return a pseudorandom {@code float} value between zero (inclusive) and one (exclusive)
     */
    default float nextFloat() {
        return (nextInt() >>> 8) * 0x1.0p-24f;
    }

    /**
     * Returns a (pseudo)randomly chosen {@code float} value between zero
     * (inclusive) and the specified bound (exclusive).
     *
     * @param bound the upper bound (exclusive) for the returned value.
     *        Must be positive and finite
     *
     * @return a (pseudo)randomly chosen {@code float} value between
     *         zero (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} is not
     *         both positive and finite
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkBound(bound)} and then
     *           {@code RandomSupport.boundedNextFloat(this, bound)}.
     */
    default float nextFloat(float bound) {
        RandomSupport.checkBound(bound);
        return RandomSupport.boundedNextFloat(this, bound);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code float} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive)
     *
     * @return a (pseudo)randomly chosen {@code float} value between the
     *         origin (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code origin} is not finite,
     *         or {@code bound} is not finite, or {@code origin}
     *         is greater than or equal to {@code bound}
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkRange(origin, bound)} and then
     *           {@code RandomSupport.boundedNextFloat(this, origin, bound)}.
     */
    default float nextFloat(float origin, float bound) {
        RandomSupport.checkRange(origin, bound);
        return RandomSupport.boundedNextFloat(this, origin, bound);
    }

    /**
     * Returns a pseudorandom {@code double} value between zero (inclusive) and one (exclusive).
     * <p>
     * The default implementation uses the 53 high-order bits from a call to {@code nextLong()}.
     *
     * @return a pseudorandom {@code double} value between zero (inclusive) and one (exclusive)
     */
    default double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /**
     * Returns a (pseudo)randomly chosen {@code double} value between zero
     * (inclusive) and the specified bound (exclusive).
     *
     * @param bound the upper bound (exclusive) for the returned value.
     *        Must be positive and finite
     *
     * @return a (pseudo)randomly chosen {@code double} value between
     *         zero (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} is not
     *         both positive and finite
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkBound(bound)} and then
     *           {@code RandomSupport.boundedNextDouble(this, bound)}.
     */
    default double nextDouble(double bound) {
        RandomSupport.checkBound(bound);
        return RandomSupport.boundedNextDouble(this, bound);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code double} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     *
     * @return a (pseudo)randomly chosen {@code double} value between the
     *         origin (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code origin} is not finite,
     *         or {@code bound} is not finite, or {@code origin}
     *         is greater than or equal to {@code bound}
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkRange(origin, bound)} and then
     *           {@code RandomSupport.boundedNextDouble(this, origin, bound)}.
     */
    default double nextDouble(double origin, double bound) {
        RandomSupport.checkRange(origin, bound);
        return RandomSupport.boundedNextDouble(this, origin, bound);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code int} value.
     * <p>
     * The default implementation uses the 32 high-order bits from a call to {@code nextLong()}.
     *
     * @return a (pseudo)randomly chosen {@code int} value
     */
    default public int nextInt() {
        return (int)(nextLong() >>> 32);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code int} value between
     * zero (inclusive) and the specified bound (exclusive).
     *
     * @param bound the upper bound (exclusive) for the returned value. Must be positive.
     *
     * @return a (pseudo)randomly chosen {@code int} value between
     *         zero (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} is not positive
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkBound(bound)} and then
     *           {@code RandomSupport.boundedNextInt(this, bound)}.
     */
    default int nextInt(int bound) {
        RandomSupport.checkBound(bound);
        return RandomSupport.boundedNextInt(this, bound);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code int} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     *
     * @return a (pseudo)randomly chosen {@code int} value between the
     *         origin (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkRange(origin, bound)} and then
     *           {@code RandomSupport.boundedNextInt(this, origin, bound)}.
     */
    default int nextInt(int origin, int bound) {
        RandomSupport.checkRange(origin, bound);
        return RandomSupport.boundedNextInt(this, origin, bound);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code long} value.
     *
     * @return a (pseudo)randomly chosen {@code long} value
     */
    long nextLong();

    /**
     * Returns a (pseudo)randomly chosen {@code long} value between
     * zero (inclusive) and the specified bound (exclusive).
     *
     * @param bound the upper bound (exclusive) for the returned value.  Must be positive.
     *
     * @return a (pseudo)randomly chosen {@code long} value between
     *         zero (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} is not positive
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkBound(bound)} and then
     *           {@code RandomSupport.boundedNextLong(this, bound)}.
     */
    default long nextLong(long bound) {
        RandomSupport.checkBound(bound);
        return RandomSupport.boundedNextLong(this, bound);
    }

    /**
     * Returns a (pseudo)randomly chosen {@code long} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     *
     * @return a (pseudo)randomly chosen {@code long} value between the
     *         origin (inclusive) and the bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     *
     * @implNote The default implementation simply calls
     *           {@code RandomSupport.checkRange(origin, bound)} and then
     *           {@code RandomSupport.boundedNextInt(this, origin, bound)}.
     *
     */
    default long nextLong(long origin, long bound) {
        RandomSupport.checkRange(origin, bound);
        return RandomSupport.boundedNextLong(this, origin, bound);
    }

    /**
     * Returns a {@code double} value (pseudo)randomly chosen from
     * a Gaussian (normal) distribution whose mean is 0 and whose
     * standard deviation is 1.
     *
     * @return a {@code double} value (pseudo)randomly chosen from a
     *         Gaussian distribution
     */
    default double nextGaussian() {
        return RandomSupport.computeNextGaussian(this);
    }

    /**
     * Returns a {@code double} value (pseudo)randomly chosen from
     * a Gaussian (normal) distribution with a mean and
     * standard deviation specified by the arguments.
     *
     * @param mean the mean of the Gaussian distribution to be drawn from
     * @param stddev the standard deviation (square root of the variance)
     *        of the Gaussian distribution to be drawn from
     *
     * @return a {@code double} value (pseudo)randomly chosen from the
     *         specified Gaussian distribution
     *
     * @throws IllegalArgumentException if {@code stddev} is negative
     */
    default double nextGaussian(double mean, double stddev) {
        if (stddev < 0.0) throw new IllegalArgumentException("standard deviation must be non-negative");
        return mean + stddev * RandomSupport.computeNextGaussian(this);
    }

    /**
     * Returns a nonnegative {@code double} value (pseudo)randomly chosen
     * from an exponential distribution whose mean is 1.
     *
     * @return a nonnegative {@code double} value (pseudo)randomly chosen from an
     *         exponential distribution
     */
    default double nextExponential() {
        return RandomSupport.computeNextExponential(this);
    }

    /**
     * Returns the period of this {@link RandomGenerator} object.
     *
     * @return a {@link BigInteger} whose value is the number of distinct possible states of this
     *         {@link RandomGenerator} object, or 0 if unknown, or negative if extremely
     *         large.
     */
    BigInteger period();

    /**
     * The value (0) returned by the {@code period()} method if the period is unknown.
     */
    static final BigInteger UNKNOWN_PERIOD = BigInteger.ZERO;

    /**
     * The (negative) value returned by the {@code period()} method if this generator
     * has no period because it is truly random rather than just pseudorandom.
     */
    static final BigInteger TRULY_RANDOM = BigInteger.valueOf(-1);

    /**
     * The (negative) value that may be returned by the {@code period()} method
     * if this generator has a huge period (larger than 2**(2**16)).
     */
    static final BigInteger HUGE_PERIOD = BigInteger.valueOf(-2);

    /**
     * The {@link StreamableGenerator} interface augments the {@link RandomGenerator} interface
     * to provide methods that return streams of {@link RandomGenerator} objects.
     * Ideally, such a stream of objects would have the property that the
     * behavior of each object is statistically independent of all the others.
     * In practice, one may have to settle for some approximation to this property.
     *
     * A generator that implements interface {@link SplittableGenerator}
     * may choose to use its {@link SplittableGenerator#splits splits} method to implement the {@link RandomGenerator#rngs() rngs()}
     * method required by this interface.
     *
     * A generator that implements interface {@link JumpableGenerator}
     * may choose to use its {@link JumpableGenerator#jumps} method to implement the {@link RandomGenerator#rngs() rngs()}
     * method required by this interface.
     *
     * A generator that implements interface {@link LeapableGenerator}
     * may choose to use its {@link LeapableGenerator#leaps} method to implement the {@link RandomGenerator#rngs() rngs()}
     * method required by this interface.
     * <p>
     * An implementation of the {@link StreamableGenerator} interface must provide
     * concrete definitions for the methods {@link StreamableGenerator#nextLong}, {@link RandomGenerator#period()}, and
     * {@link RandomGenerator#rngs() rngs()}.  Default implementations are provided for all other methods.
     * <p>
     * Objects that implement {@link StreamableGenerator} are typically
     * not cryptographically secure.  Consider instead using
     * {@link java.security.SecureRandom} to get a cryptographically
     * secure pseudo-random number generator for use by
     * security-sensitive applications.
     *
     * @since   16
     */
    public interface StreamableGenerator extends RandomGenerator {

        /**
         * Returns a stream of all available StreamableGenerator factories.
         *
         * @return Stream of all available StreamableGenerator factories.
         */
        public static Stream<RandomGeneratorFactory<StreamableGenerator>> all() {
            return RandomGeneratorFactory.all(StreamableGenerator.class);
        }

        /**
         * Returns an instance of {@link StreamableGenerator} that utilizes the
         * {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return An instance of {@link StreamableGenerator}
         */
        public static StreamableGenerator of(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.of(name, StreamableGenerator.class);
        }

        /**
         * Returns a {@link RandomGeneratorFactory} that can produce instances
         * of {@link StreamableGenerator} that utilize the {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return {@link RandomGeneratorFactory} of {@link StreamableGenerator}
         */
        public static RandomGeneratorFactory<StreamableGenerator> factoryOf(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.factoryOf(name, StreamableGenerator.class);
        }

        /**
         * Returns an effectively unlimited stream of objects, each of
         * which implements the {@link RandomGenerator} interface.  Ideally the
         * generators in the stream will appear to be statistically
         * independent.  The new generators should be of the same kind
         * as this generator.
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote It is permitted to implement this method in a manner
         *           equivalent to {@code rngs(Long.MAX_VALUE)}.
         */
        Stream<RandomGenerator> rngs();

        /**
         * Returns an effectively unlimited stream of objects, each of
         * which implements the {@link RandomGenerator} interface.  Ideally the
         * generators in the stream will appear to be statistically
         * independent.  The new generators should be of the same kind
         * as this generator.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is
         *         less than zero
         *
         * @implNote The default implementation calls {@code rngs()} and
         *           then limits its length to {@code streamSize}.
         */
        default Stream<RandomGenerator> rngs(long streamSize) {
            RandomSupport.checkStreamSize(streamSize);
            return rngs().limit(streamSize);
        }
    }

    /**
     * This interface is designed to provide a common protocol for objects
     * that generate sequences of pseudorandom numbers (or Boolean values)
     * and furthermore can be <i>split</i> into two objects (the original
     * one and a new one) each of which obey that same protocol (and therefore
     * can be recursively split indefinitely).
     * <p>
     * Ideally, all {@link SplittableGenerator} objects produced by recursive
     * splitting from a single original {@link SplittableGenerator} object are
     * statistically independent of one another and individually uniform.
     * Therefore we would expect the set of values collectively generated
     * by a set of such objects to have the same statistical properties as
     * if the same quantity of values were generated by a single thread
     * using a single {@link SplittableGenerator} object.  In practice, one must
     * settle for some approximation to independence and uniformity.
     * <p>
     * Methods are provided to perform a single splitting operation and
     * also to produce a stream of generators split off from the original
     * (by either iterative or recursive splitting, or a combination).
     * <p>
     * An implementation of the {@link SplittableGenerator} interface must
     * provide concrete definitions for the methods {@code nextLong},
     * {@code period()}, {@code split()}, {@code split(SplittableGenerator)},
     * {@code splits()}, {@code splits(long)}, {@code splits(SplittableGenerator)},
     * and {@code splits(long, SplittableGenerator)}.  Perhaps the most convenient
     * way to implement this interface is to extend the abstract class
     * {@link AbstractSplittableGenerator}.
     * <p>
     * Objects that implement {@link SplittableGenerator} are
     * typically not cryptographically secure.  Consider instead using
     * {@link java.security.SecureRandom} to get a cryptographically
     * secure pseudo-random number generator for use by
     * security-sensitive applications.
     *
     * @since   16
     */
    public interface SplittableGenerator extends StreamableGenerator {

        /**
         * Returns a stream of all available SplittableGenerator factories.
         *
         * @return Stream of all available SplittableGenerator factories.
         */
        public static Stream<RandomGeneratorFactory<SplittableGenerator>> all() {
            return RandomGeneratorFactory.all(SplittableGenerator.class);
        }

        /**
         * Returns an instance of {@link SplittableGenerator} that utilizes the
         * {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return An instance of {@link SplittableGenerator}
         */
        public static SplittableGenerator of(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.of(name, SplittableGenerator.class);
        }

        /**
         * Returns a {@link RandomGeneratorFactory} that can produce instances
         * of {@link SplittableGenerator} that utilize the {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return {@link RandomGeneratorFactory} of {@link SplittableGenerator}
         */
        public static RandomGeneratorFactory<SplittableGenerator> factoryOf(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.factoryOf(name, SplittableGenerator.class);
        }

        /**
         * Returns a new pseudorandom number generator, split off from
         * this one, that implements the {@link RandomGenerator} and {@link SplittableGenerator}
         * interfaces.
         *
         * This pseudorandom number generator may be used as a source of
         * pseudorandom bits used to initialize the state the new one.
         *
         * @return a new object that implements the {@link RandomGenerator} and
         *         {@link SplittableGenerator} interfaces
         */
        SplittableGenerator split();

        /**
         * Returns a new pseudorandom number generator, split off from
         * this one, that implements the {@link RandomGenerator} and {@link SplittableGenerator}
         * interfaces.
         *
         * @param source a {@link SplittableGenerator} instance to be used instead
         *               of this one as a source of pseudorandom bits used to
         *               initialize the state of the new ones.
         *
         * @return an object that implements the {@link RandomGenerator} and
         *         {@link SplittableGenerator} interfaces
         */
        SplittableGenerator split(SplittableGenerator source);

        /**
         * Returns an effectively unlimited stream of new pseudorandom
         * number generators, each of which implements the {@link SplittableGenerator}
         * interface.
         *
         * This pseudorandom number generator may be used as a source of
         * pseudorandom bits used to initialize the state the new ones.
         *
         * @implNote It is permitted to implement this method in a manner
         * equivalent to {@code splits(Long.MAX_VALUE)}.
         *
         * @return a stream of {@link SplittableGenerator} objects
         */
        default Stream<SplittableGenerator> splits() {
            return this.splits(this);
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of
         * new pseudorandom number generators, each of which implements the
         * {@link SplittableGenerator} interface.
         *
         * This pseudorandom number generator may be used as a source of
         * pseudorandom bits used to initialize the state the new ones.
         *
         * @param streamSize the number of values to generate
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @throws IllegalArgumentException if {@code streamSize} is
         *         less than zero
         */
        Stream<SplittableGenerator> splits(long streamSize);

        /**
         * Returns an effectively unlimited stream of new pseudorandom
         * number generators, each of which implements the {@link SplittableGenerator}
         * interface.
         *
         * @param source a {@link SplittableGenerator} instance to be used instead
         *               of this one as a source of pseudorandom bits used to
         *               initialize the state of the new ones.
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @implNote It is permitted to implement this method in a manner
         *           equivalent to {@code splits(Long.MAX_VALUE, source)}.
         */
        Stream<SplittableGenerator> splits(SplittableGenerator source);

        /**
         * Returns a stream producing the given {@code streamSize} number of
         * new pseudorandom number generators, each of which implements the
         * {@link SplittableGenerator} interface.
         *
         * @param streamSize the number of values to generate
         * @param source a {@link SplittableGenerator} instance to be used instead
         *               of this one as a source of pseudorandom bits used to
         *               initialize the state of the new ones.
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @throws IllegalArgumentException if {@code streamSize} is
         *         less than zero
         */
        Stream<SplittableGenerator> splits(long streamSize, SplittableGenerator source);

        /**
         * Returns an effectively unlimited stream of new pseudorandom
         * number generators, each of which implements the {@link RandomGenerator}
         * interface.  Ideally the generators in the stream will appear
         * to be statistically independent.
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote The default implementation calls {@code splits()}.
         */
        default Stream<RandomGenerator> rngs() {
            return this.splits().map(x -> (RandomGenerator)x);
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of
         * new pseudorandom number generators, each of which implements the
         * {@link RandomGenerator} interface.  Ideally the generators in the stream will
         * appear to be statistically independent.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is
         *         less than zero
         *
         * @implNote The default implementation calls {@code splits(streamSize)}.
         */
        default Stream<RandomGenerator> rngs(long streamSize) {
            return this.splits(streamSize).map(x -> (RandomGenerator)x);
        }
    }

    /**
     * This interface is designed to provide a common protocol for objects that generate
     * pseudorandom sequences of numbers (or Boolean values) and furthermore can easily <i>jump</i>
     * forward (by a fixed amount) to a distant point in the state cycle.
     * <p>
     * Ideally, all {@link JumpableGenerator} objects produced by iterative jumping from a single
     * original {@link JumpableGenerator} object are statistically independent of one another and
     * individually uniform. In practice, one must settle for some approximation to independence and
     * uniformity.  In particular, a specific implementation may assume that each generator in a
     * stream produced by the {@code jumps} method is used to produce a number of values no larger
     * than either 2<sup>64</sup> or the square root of its period.  Implementors are advised to use
     * algorithms whose period is at least 2<sup>127</sup>.
     * <p>
     * Methods are provided to perform a single jump operation and also to produce a stream of
     * generators produced from the original by iterative copying and jumping of internal state.  A
     * typical strategy for a multithreaded application is to create a single {@link
     * JumpableGenerator} object, calls its {@code jumps} method exactly once, and then parcel out
     * generators from the resulting stream, one to each thread.  It is generally not a good idea to
     * call {@code jump} on a generator that was itself produced by the {@code jumps} method,
     * because the result may be a generator identical to another generator already produce by that
     * call to the {@code jumps} method. For this reason, the return type of the {@code jumps}
     * method is {@code Stream<RandomGenerator>} rather than {@code Stream<JumpableGenerator>}, even
     * though the actual generator objects in that stream likely do also implement the {@link
     * JumpableGenerator} interface.
     * <p>
     * An implementation of the {@link JumpableGenerator} interface must provide concrete
     * definitions for the methods {@code nextLong}, {@code period()}, {@code copy()},
     * {@code jump()}, and {@code defaultJumpDistance()}. Default implementations are
     * provided for all other methods.
     * <p>
     * Objects that implement {@link JumpableGenerator} are typically not cryptographically secure.
     * Consider instead using {@link java.security.SecureRandom} to get a cryptographically secure
     * pseudo-random number generator for use by security-sensitive applications.
     *
     * @since   16
     */
    public interface JumpableGenerator extends StreamableGenerator {

        /**
         * Returns a stream of all available JumpableGenerator factories.
         *
         * @return Stream of all available JumpableGenerator factories.
         */
        public static Stream<RandomGeneratorFactory<JumpableGenerator>> all() {
            return RandomGeneratorFactory.all(JumpableGenerator.class);
        }

        /**
         * Returns an instance of {@link JumpableGenerator} that utilizes the
         * {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return An instance of {@link JumpableGenerator}
         */
        public static JumpableGenerator of(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.of(name, JumpableGenerator.class);
        }

        /**
         * Returns a {@link RandomGeneratorFactory} that can produce instances
         * of {@link JumpableGenerator} that utilize the {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return {@link RandomGeneratorFactory} of {@link JumpableGenerator}
         */
        public static RandomGeneratorFactory<JumpableGenerator> factoryOf(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.factoryOf(name, JumpableGenerator.class);
        }

        /**
         * Returns a new generator whose internal state is an exact copy of this generator (therefore
         * their future behavior should be identical if subjected to the same series of operations).
         *
         * @return a new object that is a copy of this generator
         */
        JumpableGenerator copy();

        /**
         * Alter the state of this pseudorandom number generator so as to jump forward a large, fixed
         * distance (typically 2<sup>64</sup> or more) within its state cycle.
         */
        void jump();

        /**
         * Returns the distance by which the {@code jump()} method will jump forward within the state
         * cycle of this generator object.
         *
         * @return the default jump distance (as a {@code double} value)
         */
        double defaultJumpDistance();

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link RandomGenerator} interface.
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote It is permitted to implement this method in a manner equivalent to
         *         {@code jumps(Long.MAX_VALUE)}.
         * @implNote The default implementation produces a sequential stream that  repeatedly
         *         calls {@code copy()} and {@code jump()} on this generator, and the copies become the
         *         generators produced by the stream.
         */
        default Stream<RandomGenerator> jumps() {
            return Stream.generate(this::copyAndJump).sequential();
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link RandomGenerator} interface.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         * @implNote The default implementation produces a sequential stream that  repeatedly
         *         calls {@code copy()} and {@code jump()} on this generator, and the copies become the
         *         generators produced by the stream.
         */
        default Stream<RandomGenerator> jumps(long streamSize) {
            return jumps().limit(streamSize);
        }

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link RandomGenerator} interface.  Ideally the generators in the stream
         * will appear to be statistically independent.
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote The default implementation calls {@code jumps()}.
         */
        default Stream<RandomGenerator> rngs() {
            return this.jumps();
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link RandomGenerator} interface.  Ideally
         * the generators in the stream will appear to be statistically independent.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         * @implNote The default implementation calls {@code jumps(streamSize)}.
         */
        default Stream<RandomGenerator> rngs(long streamSize) {
            return this.jumps(streamSize);
        }

        /**
         * Copy this generator, jump this generator forward, then return the copy.
         *
         * @return a copy of this generator object before the jump occurred
         */
        default RandomGenerator copyAndJump() {
            RandomGenerator result = copy();
            jump();
            return result;
        }

    }

    /**
     * This interface is designed to provide a common protocol for objects that generate sequences
     * of pseudorandom numbers (or Boolean values) and furthermore can easily not only jump but
     * also
     * <i>leap</i> to a very distant point in the state cycle.
     * <p>
     * Typically one will construct a series of {@link LeapableGenerator} objects by iterative
     * leaping from a single original {@link LeapableGenerator} object, and then for each such
     * object produce a subseries of objects by iterative jumping.  There is little conceptual
     * difference between leaping and jumping, but typically a leap will be a very long jump in the
     * state cycle (perhaps distance 2<sup>128</sup> or so).
     * <p>
     * Ideally, all {@link LeapableGenerator} objects produced by iterative leaping and jumping from
     * a single original {@link LeapableGenerator} object are statistically independent of one
     * another and individually uniform. In practice, one must settle for some approximation to
     * independence and uniformity.  In particular, a specific implementation may assume that each
     * generator in a stream produced by the {@code leaps} method is used to produce (by jumping) a
     * number of objects no larger than 2<sup>64</sup>.  Implementors are advised to use algorithms
     * whose period is at least 2<sup>191</sup>.
     * <p>
     * Methods are provided to perform a single leap operation and also to produce a stream of
     * generators produced from the original by iterative copying and leaping of internal state.
     * The generators produced must implement the {@link JumpableGenerator} interface but need not
     * also implement the {@link LeapableGenerator} interface.  A typical strategy for a
     * multithreaded application is to create a single {@link LeapableGenerator} object, calls its
     * {@code leaps} method exactly once, and then parcel out generators from the resulting stream,
     * one to each thread.  Then the {@code jumps} method of each such generator be called to
     * produce a substream of generator objects.
     * <p>
     * An implementation of the {@link LeapableGenerator} interface must provide concrete
     * definitions for the methods {@code nextLong}, {@code period()}, {@code copy()},
     * {@code jump()}, {@code defaultJumpDistance()}, {@code leap()}, and {@code defaultLeapDistance()}.
     * Default implementations are provided for all other methods.
     * <p>
     * Objects that implement {@link LeapableGenerator} are typically not cryptographically secure.
     * Consider instead using {@link java.security.SecureRandom} to get a cryptographically secure
     * pseudo-random number generator for use by security-sensitive applications.
     *
     * @since   16
     */
    public interface LeapableGenerator extends JumpableGenerator {

        /**
         * Returns a stream of all available LeapableGenerator factories.
         *
         * @return Stream of all available LeapableGenerator factories.
         */
        public static Stream<RandomGeneratorFactory<LeapableGenerator>> all() {
            return RandomGeneratorFactory.all(LeapableGenerator.class);
        }

        /**
         * Returns an instance of {@link LeapableGenerator} that utilizes the
         * {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return An instance of {@link LeapableGenerator}
         */
        public static LeapableGenerator of(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.of(name, LeapableGenerator.class);
        }

        /**
         * Returns a {@link RandomGeneratorFactory} that can produce instances
         * of {@link LeapableGenerator} that utilize the {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return {@link RandomGeneratorFactory} of {@link LeapableGenerator}
         */
        public static RandomGeneratorFactory<LeapableGenerator> factoryOf(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.factoryOf(name, LeapableGenerator.class);
        }

        /**
         * Returns a new generator whose internal state is an exact copy of this generator (therefore
         * their future behavior should be identical if subjected to the same series of operations).
         *
         * @return a new object that is a copy of this generator
         */
        LeapableGenerator copy();

        /**
         * Alter the state of this pseudorandom number generator so as to leap forward a large, fixed
         * distance (typically 2<sup>96</sup> or more) within its state cycle.
         */
        void leap();

        /**
         * Returns the distance by which the {@code leap()} method will leap forward within the state
         * cycle of this generator object.
         *
         * @return the default leap distance (as a {@code double} value)
         */
        double defaultLeapDistance();

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link JumpableGenerator} interface.
         *
         * @return a stream of objects that implement the {@link JumpableGenerator} interface
         *
         * @implNote It is permitted to implement this method in a manner equivalent to {@code
         *         leaps(Long.MAX_VALUE)}.
         * @implNote The default implementation produces a sequential stream that  repeatedly
         *         calls {@code copy()} and {@code leap()} on this generator, and the copies become the
         *         generators produced by the stream.
         */
        default Stream<JumpableGenerator> leaps() {
            return Stream.generate(this::copyAndLeap).sequential();
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link JumpableGenerator} interface.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link JumpableGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         * @implNote The default implementation produces a sequential stream that  repeatedly
         *         calls {@code copy()} and {@code leap()} on this generator, and the copies become the
         *         generators produced by the stream.
         */
        default Stream<JumpableGenerator> leaps(long streamSize) {
            return leaps().limit(streamSize);
        }

        /**
         * Copy this generator, leap this generator forward, then return the copy.
         *
         * @return a copy of this generator object before the leap occurred
         */
        default JumpableGenerator copyAndLeap() {
            JumpableGenerator result = copy();
            leap();
            return result;
        }

    }

    /**
     * This interface is designed to provide a common protocol for objects that generate sequences
     * of pseudorandom numbers (or Boolean values) and furthermore can easily <i>jump</i> to an
     * arbitrarily specified distant point in the state cycle.
     * <p>
     * Ideally, all {@link ArbitrarilyJumpableGenerator} objects produced by iterative jumping from
     * a single original {@link ArbitrarilyJumpableGenerator} object are statistically independent
     * of one another and individually uniform, provided that they do not traverse overlapping
     * portions of the state cycle. In practice, one must settle for some approximation to
     * independence and uniformity.  In particular, a specific implementation may assume that each
     * generator in a stream produced by the {@code jumps} method is used to produce a number of
     * values no larger than the jump distance specified.  Implementors are advised to use
     * algorithms whose period is at least 2<sup>127</sup>.
     * <p>
     * For many applications, it suffices to jump forward by a power of two or some small multiple
     * of a power of two, but this power of two may not be representable as a {@code long} value.
     * To avoid the use of {@link java.math.BigInteger} values as jump distances, {@code double}
     * values are used instead.
     * <p>
     * Methods are provided to perform a single jump operation and also to produce a stream of
     * generators produced from the original by iterative copying and jumping of internal state.  A
     * typical strategy for a multithreaded application is to create a single
     * {@link ArbitrarilyJumpableGenerator} object, call its {@code jumps} method exactly once, and
     * then parcel out generators from the resulting stream, one to each thread.  However, each
     * generator produced also has type {@link ArbitrarilyJumpableGenerator}; with care, different
     * jump distances can be used to traverse the entire state cycle in various ways.
     * <p>
     * An implementation of the {@link ArbitrarilyJumpableGenerator} interface must provide concrete
     * definitions for the methods {@code nextLong}, {@code period()}, {@code copy()},
     * {@code jump(double)}, {@code defaultJumpDistance()}, and {@code defaultLeapDistance()}.
     * Default implementations are provided for all other methods.
     * Perhaps the most convenient way to implement this interface is to extend the abstract class
     * {@link AbstractArbitrarilyJumpableGenerator}, which provides spliterator-based implementations
     * of the methods {@code ints}, {@code longs}, {@code doubles}, {@code rngs}, {@code jumps},
     * and {@code leaps}.
     * <p>
     * Objects that implement {@link ArbitrarilyJumpableGenerator} are typically not
     * cryptographically secure. Consider instead using {@link java.security.SecureRandom} to get a
     * cryptographically secure pseudo-random number generator for use by security-sensitive
     * applications.
     *
     * @since   16
     */
    public interface ArbitrarilyJumpableGenerator extends LeapableGenerator {

        /**
         * Returns a stream of all available ArbitrarilyJumpableGenerator factories.
         *
         * @return Stream of all available ArbitrarilyJumpableGenerator factories.
         */
        public static Stream<RandomGeneratorFactory<ArbitrarilyJumpableGenerator>> all() {
            return RandomGeneratorFactory.all(ArbitrarilyJumpableGenerator.class);
        }

        /**
         * Returns an instance of {@link ArbitrarilyJumpableGenerator} that utilizes the
         * {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return An instance of {@link ArbitrarilyJumpableGenerator}
         */
        public static ArbitrarilyJumpableGenerator of(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.of(name, ArbitrarilyJumpableGenerator.class);
        }

        /**
         * Returns a {@link RandomGeneratorFactory} that can produce instances
         * of {@link ArbitrarilyJumpableGenerator} that utilize the {@code name} algorithm.
         *
         * @param name  Name of random number generator algorithm
         *
         * @return {@link RandomGeneratorFactory} of {@link ArbitrarilyJumpableGenerator}
         */
        public static RandomGeneratorFactory<ArbitrarilyJumpableGenerator> factoryOf(String name) {
            Objects.requireNonNull(name);
            return RandomGeneratorFactory.factoryOf(name, ArbitrarilyJumpableGenerator.class);
        }

        /**
         * Returns a new generator whose internal state is an exact copy of this generator (therefore
         * their future behavior should be identical if subjected to the same series of operations).
         *
         * @return a new object that is a copy of this generator
         */
        ArbitrarilyJumpableGenerator copy();

        /**
         * Alter the state of this pseudorandom number generator so as to jump forward a distance equal
         * to 2<sup>{@code logDistance}</sup> within its state cycle.
         *
         * @param logDistance the base-2 logarithm of the distance to jump forward within the state
         *                    cycle
         *
         * @throws IllegalArgumentException if {@code logDistance} is NaN or negative, or if
         *                                  2<sup>{@code logDistance}</sup> is greater than the period
         *                                  of this generator
         */
        void jumpPowerOfTwo(int logDistance);

        /**
         * Alter the state of this pseudorandom number generator so as to jump forward a specified
         * distance within its state cycle.
         *
         * @param distance the distance to jump forward within the state cycle
         *
         * @throws IllegalArgumentException if {@code distance} is Nan, negative, or greater than the
         *                                  period of this generator
         */
        void jump(double distance);

        /**
         * Alter the state of this pseudorandom number generator so as to jump forward a large, fixed
         * distance (typically 2<sup>64</sup> or more) within its state cycle.  The distance used is
         * that returned by method {@code defaultJumpDistance()}.
         */
        default void jump() { jump(defaultJumpDistance()); }

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of
         * which implements the {@link ArbitrarilyJumpableGenerator} interface, produced by jumping
         * copies of this generator by different integer multiples of the specified jump distance.
         *
         * @param distance a distance to jump forward within the state cycle
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote This method is implemented to be equivalent to {@code jumps(Long.MAX_VALUE)}.
         */
        default Stream<ArbitrarilyJumpableGenerator> jumps(double distance) {
            return Stream.generate(() -> copyAndJump(distance)).sequential();
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link ArbitrarilyJumpableGenerator} interface,
         * produced by jumping copies of this generator by different integer multiples of the
         * specified jump distance.
         *
         * @param streamSize the number of generators to generate
         * @param distance   a distance to jump forward within the state cycle
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        default Stream<ArbitrarilyJumpableGenerator> jumps(long streamSize, double distance) {
            return jumps(distance).limit(streamSize);
        }

        /**
         * Alter the state of this pseudorandom number generator so as to jump forward a very large,
         * fixed distance (typically 2<sup>128</sup> or more) within its state cycle.  The distance
         * used is that returned by method {@code defaultJLeapDistance()}.
         */
        default void leap() { jump(defaultLeapDistance()); }

        /**
         * Copy this generator, jump this generator forward, then return the copy.
         *
         * @param distance a distance to jump forward within the state cycle
         *
         * @return a copy of this generator object before the jump occurred
         */
        default ArbitrarilyJumpableGenerator copyAndJump(double distance) {
            ArbitrarilyJumpableGenerator result = copy();
            jump(distance);
            return result;
        }

    }
}
