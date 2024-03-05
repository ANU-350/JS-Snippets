/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.inline.hpp"

#include "utilities/ostream.hpp"

#include "utilities/vmassert_uninstall.hpp"
#include <iostream>
#include "utilities/vmassert_reinstall.hpp"

#include "unittest.hpp"


class ShenandoahSimpleBitMapTest: public ::testing::Test {
protected:
  const ssize_t SMALL_BITMAP_SIZE =  512;
  const ssize_t LARGE_BITMAP_SIZE = 4096;

  ShenandoahSimpleBitMap bm_small;
  ShenandoahSimpleBitMap bm_large;

  ShenandoahSimpleBitMapTest():
      bm_small(SMALL_BITMAP_SIZE),
      bm_large(LARGE_BITMAP_SIZE) {
  }
};

class BasicShenandoahSimpleBitMapTest: public ShenandoahSimpleBitMapTest {
protected:

  // set_bits[] is an array of indexes holding bits that are supposed to be set, in increasing order.
  void verifyBitMapState(ShenandoahSimpleBitMap& bm, ssize_t size, ssize_t set_bits[], ssize_t num_set_bits) {

    // Verify number of bits
    ASSERT_EQ(bm.number_of_bits(), size);

    ssize_t set_bit_index = 0;
    // Check that is_set(idx) for every possible idx
    for (ssize_t i = 0; i < size; i++) {
      bool is_set = bm_small.is_set(i);
      bool intended_value = false;;
      if (set_bit_index < num_set_bits) {
        if (set_bits[set_bit_index] == i) {
          intended_value = true;
          set_bit_index++;
        }
      }
      ASSERT_EQ(is_set, intended_value);
    }

    // Check that bits_at(array_idx) matches intended value for every valid array_idx value
    set_bit_index = 0;
    ssize_t alignment = bm_small.alignment();
    ssize_t small_words = size / alignment;
    for (ssize_t i = 0; i < small_words; i += alignment) {
      size_t bits = bm_small.bits_at(i);
      for (ssize_t b = 0; b < alignment; b++) {
        ssize_t bit_value = i * alignment + b;
        bool intended_value = false;;
        if (set_bit_index < num_set_bits) {
          if (set_bits[set_bit_index] == bit_value) {
            intended_value = true;
          }
        }
        size_t bit_mask = ((size_t) 0x01) << b;
        bool is_set = (bits & bit_mask) != 0;
        ASSERT_EQ(is_set, intended_value);
      }
    }

    // Make sure find_next_set_bit() works correctly
    ssize_t probe_point = 0;
    for (ssize_t i = 0; i < num_set_bits; i++) {
      ssize_t next_expected_bit = set_bits[i];
      probe_point = bm.find_next_set_bit(probe_point);
      ASSERT_EQ(probe_point, next_expected_bit);
      probe_point++;            // Prepare to look beyond the most recent bit.
    }
    probe_point = bm.find_next_set_bit(probe_point);
    ASSERT_EQ(probe_point, size); // Verify that last failed search returns sentinel value: num bits in bit map

    // Confirm that find_next_set_bit() with a bounded search space works correctly
    // Limit this search to the first 3/4 of the full bit map
    ssize_t boundary_idx = 3 * size / 4;
    probe_point = 0;
    for (ssize_t i = 0; i < num_set_bits; i++) {
      ssize_t next_expected_bit = set_bits[i];
      probe_point = bm.find_next_set_bit(probe_point, boundary_idx);
      if (next_expected_bit >= boundary_idx) {
        // Verify that last failed search returns sentinel value: boundary_idx
        ASSERT_EQ(probe_point, boundary_idx);
        break;
      } else {
        ASSERT_EQ(probe_point, next_expected_bit);
        probe_point++;            // Prepare to look beyond the most recent bit.
      }
    }
    if (probe_point < boundary_idx) {
      // In case there are no set bits in the last 1/4 of bit map, confirm that last failed search returns sentinel: boundary_idx
      probe_point = bm.find_next_set_bit(probe_point, boundary_idx);
      ASSERT_EQ(probe_point, boundary_idx);
    }

    // Make sure find_prev_set_bit() works correctly
    probe_point = size - 1;
    for (ssize_t i = num_set_bits - 1; i >= 0; i--) {
      ssize_t next_expected_bit = set_bits[i];
      probe_point = bm.find_prev_set_bit(probe_point);
      ASSERT_EQ(probe_point, next_expected_bit);
      probe_point--;            // Prepare to look before the most recent bit.
    }
    probe_point = bm.find_prev_set_bit(probe_point);
    ASSERT_EQ(probe_point, -1); // Verify that last failed search returns sentinel value: -1

    // Confirm that find_prev_set_bit() with a bounded search space works correctly
    // Limit this search to the last 3/4 of the full bit map
    boundary_idx = size / 4;
    probe_point = size - 1;
    for (ssize_t i = num_set_bits - 1; i >= 0; i--) {
      ssize_t next_expected_bit = set_bits[i];
      probe_point = bm.find_next_set_bit(probe_point, boundary_idx);
      if (next_expected_bit <= boundary_idx) {
        // Verify that last failed search returns sentinel value: boundary_idx
        ASSERT_EQ(probe_point, boundary_idx);
        break;
      } else {
        ASSERT_EQ(probe_point, next_expected_bit);
        probe_point--;            // Prepare to look beyond the most recent bit.
      }
    }
    if (probe_point >= boundary_idx) {
      probe_point = bm.find_next_set_bit(probe_point, boundary_idx);
        // Verify that last failed search returns sentinel value: boundary_idx
      ASSERT_EQ(probe_point, boundary_idx);
    }

    // What's the longest cluster of consecutive bits
    ssize_t previous_value = -2;
    ssize_t longest_run = 0;
    ssize_t current_run = 0;
    for (ssize_t i = 0; i < num_set_bits; i++) {
      ssize_t next_expected_bit = set_bits[i];
      if (next_expected_bit == previous_value + 1) {
        current_run++;
      } else {
        previous_value = next_expected_bit;
        current_run = 1;
      }
      if (current_run > longest_run) {
        longest_run = current_run;
      }
      previous_value = next_expected_bit;
    }

    // Confirm that find_next_consecutive_bits() works for each cluster size known to have at least one match
    for (ssize_t cluster_size = 1; cluster_size <= longest_run; cluster_size++) {

      // Verify that find_next_consecutive_bits() works
      ssize_t bit_idx = 0;
      ssize_t probe_point = 0;
      while (probe_point <= size - cluster_size) {
        bool cluster_found = false;
        while (!cluster_found && (bit_idx <= num_set_bits - cluster_size)) {
          cluster_found = true;
          for (ssize_t i = 1; i < cluster_size; i++) {
            if (set_bits[bit_idx] + i != set_bits[bit_idx + i]) {
              cluster_found = false;
              break;
            }
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = bit_idx;
          probe_point = bm.find_next_consecutive_bits(cluster_size, probe_point);
          ASSERT_EQ(next_expected_cluster, probe_point);
          probe_point++;
          bit_idx++;
        } else {
          bit_idx++;
        }
      }
      // Confirm that the last request, which fails to find a cluster, returns sentinel value: num_bits
      probe_point = bm.find_next_consecutive_bits(cluster_size, probe_point);
      ASSERT_EQ(probe_point, size);

      // Repeat the above experiment, using 3/4 size as the search boundary_idx
      bit_idx = 0;
      probe_point = 0;
      boundary_idx = 4 * size / 4;
      while (probe_point <= boundary_idx - cluster_size) {
        bool cluster_found = false;
        while (!cluster_found && (bit_idx <= num_set_bits - cluster_size)) {
          cluster_found = true;
          for (int i = 1; i < cluster_size; i++) {
            if (set_bits[bit_idx] + i != set_bits[bit_idx + i]) {
              cluster_found = false;
              break;
            }
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = set_bits[bit_idx];
          probe_point = bm.find_next_consecutive_bits(cluster_size, probe_point, boundary_idx);
          ASSERT_EQ(next_expected_cluster, probe_point);
          probe_point++;
          bit_idx++;
        } else {
          bit_idx++;
        }
      }
      // Confirm that the last request, which fails to find a cluster, returns sentinel value: boundary_idx
      probe_point = bm.find_prev_consecutive_bits(cluster_size, probe_point, boundary_idx);
      ASSERT_EQ(probe_point, boundary_idx);

      // Verify that find_prev_consecutive_bits() works
      bit_idx = num_set_bits - 1;
      probe_point = size - 1;
      while (probe_point >= cluster_size - 1) {
        bool cluster_found = false;
        while (!cluster_found && (bit_idx - cluster_size >= -1)) {
          cluster_found = true;
          for (int i = 1; i < cluster_size; i++) {
            if (set_bits[bit_idx] - i != set_bits[bit_idx - i]) {
              cluster_found = false;
              break;
            }
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = set_bits[bit_idx];
          probe_point = bm.find_prev_consecutive_bits(cluster_size, probe_point);
          ASSERT_EQ(next_expected_cluster, probe_point);
          probe_point--;
          bit_idx--;
        } else {
          bit_idx--;
        }
      }
      // Confirm that the last request, which fails to find a cluster, returns sentinel value: -1
      probe_point = bm.find_prev_consecutive_bits(cluster_size, probe_point);
      ASSERT_EQ(probe_point, -1);

      // Verify that find_prev_consecutive_bits() works with the search range bounded at 1/4 size
      bit_idx = num_set_bits - 1;
      probe_point = size - 1;
      boundary_idx = size / 4;
      while (probe_point >= boundary_idx - 1 + cluster_size) {
        bool cluster_found = false;
        while (!cluster_found && (bit_idx - cluster_size >= -1)) {
          cluster_found = true;
          for (int i = 1; i < cluster_size; i++) {
            if (set_bits[bit_idx] - i != set_bits[bit_idx - i]) {
              cluster_found = false;
              break;
            }
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = set_bits[bit_idx];
          probe_point = bm.find_prev_consecutive_bits(cluster_size, probe_point, boundary_idx);
          ASSERT_EQ(next_expected_cluster, probe_point);
          probe_point--;
          bit_idx--;
        } else {
          bit_idx--;
        }
      }
      // Confirm that the last request, which fails to find a cluster, returns sentinel value: boundary_idx
      probe_point = bm.find_prev_consecutive_bits(cluster_size, probe_point, boundary_idx);
      ASSERT_EQ(probe_point, boundary_idx);
    }

    // Confirm that find_next_consecutive_bits() works for each cluster sizes known not to have any matches
    probe_point = bm.find_next_consecutive_bits(longest_run + 1, 0);
    ASSERT_EQ(probe_point, size);  // Confirm: failed search returns sentinel: size

    probe_point = bm.find_prev_consecutive_bits(longest_run + 1, size);
    ASSERT_EQ(probe_point, -1);    // Confirm: failed search returns sentinel: -1

    boundary_idx = 3 * size / 4;
    probe_point = bm.find_next_consecutive_bits(longest_run + 1, 0, boundary_idx);
    ASSERT_EQ(probe_point, boundary_idx); // Confirm: failed search returns sentinel: boundary_idx

    boundary_idx = size / 4;
    probe_point = bm.find_prev_consecutive_bits(longest_run + 1, size, boundary_idx);
    ASSERT_EQ(probe_point, -1);           // Confirm: failed search returns sentinel: -1
  }


  BasicShenandoahSimpleBitMapTest() {

    // Initial state of each bitmap is all bits are clear.  Confirm this:
    ssize_t set_bits_0[] = { };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_0, 0);
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_0, 0);

    bm_small.set_bit(5);
    bm_small.set_bit(63);
    bm_small.set_bit(128);
    ssize_t set_bits_1[3] = { 5, 63, 128 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_1, 3);

    bm_large.set_bit(5);
    bm_large.set_bit(63);
    bm_large.set_bit(128);
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_1, 3);

    // Test some consecutive bits
    bm_small.set_bit(140);
    bm_small.set_bit(141);
    bm_small.set_bit(142);

    bm_small.set_bit(253);
    bm_small.set_bit(254);
    bm_small.set_bit(255);

    bm_small.set_bit(271);
    bm_small.set_bit(272);

    bm_small.set_bit(320);
    bm_small.set_bit(321);
    bm_small.set_bit(322);

    bm_small.set_bit(361);

    ssize_t set_bits_2[15] = { 5, 63, 128, 140, 141, 142, 253, 254, 255, 271, 272, 320, 321, 322, 361 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_2, 15);

    bm_large.set_bit(140);
    bm_large.set_bit(141);
    bm_large.set_bit(142);

    bm_large.set_bit(1021);
    bm_large.set_bit(1022);
    bm_large.set_bit(1023);

    bm_large.set_bit(1051);

    bm_large.set_bit(1280);
    bm_large.set_bit(1281);
    bm_large.set_bit(1282);

    bm_large.set_bit(1300);
    bm_large.set_bit(1301);
    bm_large.set_bit(1302);

    ssize_t set_bits_3[16] = { 5, 63, 128, 140, 141, 142, 1021, 1022, 1023, 1051, 1280, 1281, 1282, 1300, 1301, 1302 };
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_3, 16);

    // Test clear_bit
    bm_small.clear_bit(141);
    bm_small.clear_bit(253);
    ssize_t set_bits_4[13] = { 5, 63, 128, 140, 142, 254, 255, 271, 272, 320, 321, 322, 361 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_2, 13);

    bm_large.clear_bit(5);
    bm_large.clear_bit(63);
    bm_large.clear_bit(128);
    bm_large.clear_bit(141);
    ssize_t set_bits_5[12] = { 140, 142, 1021, 1022, 1023, 1051, 1280, 1281, 1282, 1300, 1301, 1302 };
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_5, 12);

    // Test clear_all()
    bm_small.clear_all();
    bm_large.clear_all();

  }
};

TEST(BasicShenandoahSimpleBitMapTest, minimum_test) {
}
