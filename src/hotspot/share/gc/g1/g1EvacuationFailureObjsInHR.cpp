/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "gc/g1/g1EvacuationFailureObjsInHR.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "utilities/quickSort.hpp"


const uint G1EvacuationFailureObjsInHR::MaxBufferLength =
  static_cast<uint>(1u << (HeapRegion::LogOfHRGrainBytes-LogHeapWordSize));

const G1SegmentedArrayAllocOptions G1EvacuationFailureObjsInHR::_alloc_options =
  G1SegmentedArrayAllocOptions(uint(sizeof (Elem)), BufferLength, UINT_MAX, Alignment);
G1SegmentedArrayBufferList<mtGC> G1EvacuationFailureObjsInHR::_free_buffer_list;

void G1EvacuationFailureObjsInHR::visit_buffer(G1SegmentedArrayBuffer<mtGC>* node, uint limit) {
  node->copy_to(&_offset_array[_objs_num]);
  _objs_num += limit;
  // Verify elements in the buffer
  DEBUG_ONLY(node->iterate_elems(*this));
}

void G1EvacuationFailureObjsInHR::visit_elem(void* elem) {
  uint* ptr = (uint*)elem;
  assert(*ptr < _max_offset, "must be, %u", *ptr);
}

void G1EvacuationFailureObjsInHR::compact() {
  assert(_offset_array == NULL, "must be");
  uint num = _nodes_array.num_allocated_nodes();
  _offset_array = NEW_C_HEAP_ARRAY(Elem, num, mtGC);
  // Copy buffers' data to local array
  _nodes_array.iterate_nodes(*this);
  assert(_objs_num == num, "must be %u, %u", _objs_num, num);
  _nodes_array.drop_all();
}

static int order_oop(G1EvacuationFailureObjsInHR::Elem a,
                     G1EvacuationFailureObjsInHR::Elem b) {
  return static_cast<int>(a-b);
}

void G1EvacuationFailureObjsInHR::sort() {
  QuickSort::sort(_offset_array, _objs_num, order_oop, true);
}

void G1EvacuationFailureObjsInHR::clear_array() {
  FREE_C_HEAP_ARRAY(Elem, _offset_array);
  _offset_array = NULL;
  _objs_num = 0;
}

void G1EvacuationFailureObjsInHR::iterate_internal(ObjectClosure* closure) {
  Elem prev = 0;
  for (uint i = 0; i < _objs_num; i++) {
    assert(i == 0 ? (prev <= _offset_array[i]) : (prev < _offset_array[i]),
           "must be, %u, %u, %u", i, prev, _offset_array[i]);
    assert(prev < _max_offset, "must be, %u", prev);
    closure->do_object(cast_from_offset(prev = _offset_array[i]));
  }
  clear_array();
}

G1EvacuationFailureObjsInHR::G1EvacuationFailureObjsInHR(uint region_idx, HeapWord* bottom) :
  _max_offset(static_cast<Elem>(1u << (HeapRegion::LogOfHRGrainBytes-LogHeapWordSize))),
  _region_idx(region_idx),
  _bottom(bottom),
  _nodes_array("", &_alloc_options, &_free_buffer_list),
  _offset_array(NULL),
  _objs_num(0) {
  assert(HeapRegion::LogOfHRGrainBytes < 32, "must be");
}

G1EvacuationFailureObjsInHR::~G1EvacuationFailureObjsInHR() {
  assert(_offset_array == NULL, "must be");
}

void G1EvacuationFailureObjsInHR::record(oop obj) {
  assert(obj != NULL, "must be");
  assert(_region_idx == G1CollectedHeap::heap()->heap_region_containing(obj)->hrm_index(), "must be");
  Elem offset = cast_from_oop_addr(obj);
  assert(obj == cast_from_offset(offset), "must be");
  assert(offset < _max_offset, "must be, %u", offset);
  Elem* e = _nodes_array.allocate();
  *e = offset;
}

void G1EvacuationFailureObjsInHR::iterate(ObjectClosure* closure) {
  compact();
  sort();
  iterate_internal(closure);
}
