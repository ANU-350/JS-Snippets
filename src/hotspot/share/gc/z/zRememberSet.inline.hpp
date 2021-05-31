/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZREMEMBERSET_INLINE_HPP
#define SHARE_GC_Z_ZREMEMBERSET_INLINE_HPP

#include "gc/z/zRememberSet.hpp"

inline CHeapBitMap* ZRememberSet::current() {
  return &_bitmap[_current];
}

inline const CHeapBitMap* ZRememberSet::current() const {
  return &_bitmap[_current];
}

inline CHeapBitMap* ZRememberSet::previous() {
  return &_bitmap[_current ^ 1];
}

inline bool ZRememberSet::get(uintptr_t local_offset) const {
  const size_t index = local_offset / oopSize;
  return current()->at(index);
}

inline bool ZRememberSet::set(uintptr_t local_offset) {
  const size_t index = local_offset / oopSize;
  return current()->par_set_bit(index);
}

template <typename Function>
void ZRememberSet::oops_do_function(Function function, zoffset page_start) {
  previous()->iterate_f([&](BitMap::idx_t index) {
    const size_t local_offset = index * oopSize;
    const zoffset offset = page_start + local_offset;
    const zaddress addr = ZOffset::address(offset);

    function((volatile zpointer*)addr);

    return true;
  });
}

template <typename Function>
void ZRememberSet::oops_do_current_function(Function function, zoffset page_start) {
  current()->iterate_f([&](BitMap::idx_t index) {
    const size_t local_offset = index * oopSize;
    const zoffset offset = page_start + local_offset;
    const zaddress addr = ZOffset::address(offset);

    function((volatile zpointer*)addr);

    return true;
  });
}

#endif // SHARE_GC_Z_ZREMEMBERSET_INLINE_HPP
