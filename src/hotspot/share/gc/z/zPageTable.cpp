/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGlobals.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"

ZPageTable::ZPageTable() :
    _map(ZAddressOffsetMaxSize) {}

void ZPageTable::insert(ZPage* page) {
  const zoffset offset = page->start();
  const size_t size = page->size();

  // Make sure a newly created page is
  // visible before updating the page table.
  OrderAccess::storestore();

  assert(_map.get(offset) == NULL, "Invalid entry");
  _map.put(offset, size, page);
}

void ZPageTable::remove(ZPage* page) {
  const zoffset offset = page->start();
  const size_t size = page->size();

  assert(_map.get(offset) == page, "Invalid entry");
  _map.put(offset, size, NULL);
}

void ZPageTable::replace(ZPage* old_page, ZPage* new_page) {
  const zoffset offset = old_page->start();
  const size_t size = old_page->size();

  assert(_map.get(offset) == old_page, "Invalid entry");
  _map.release_put(offset, size, new_page);
}

ZGenerationPagesIterator::ZGenerationPagesIterator(const ZPageTable* page_table, ZGenerationId generation, ZPageAllocator* page_allocator) :
    _iterator(page_table),
    _generation_id(generation),
    _page_allocator(page_allocator) {
  _page_allocator->enable_deferred_destroy();
  _page_allocator->enable_deferred_recycle();
}

ZGenerationPagesIterator::~ZGenerationPagesIterator() {
  _page_allocator->disable_deferred_recycle();
  _page_allocator->disable_deferred_destroy();
}
