/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zCycle.inline.hpp"
#include "gc/z/zCycleId.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageAllocator.inline.hpp"
#include "gc/z/zPageCache.hpp"
#include "gc/z/zSafeDelete.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncommitter.hpp"
#include "gc/z/zUnmapper.hpp"
#include "gc/z/zWorkers.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static const ZStatCounter       ZCounterMutatorAllocationRate("Memory", "Allocation Rate", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterPageCacheFlush("Memory", "Page Cache Flush", ZStatUnitBytesPerSecond);
static const ZStatCriticalPhase ZCriticalPhaseAllocationStall("Allocation Stall");

void ZPageRecycle::immediate_delete(ZPage* page) {
  ZHeap::heap()->recycle_page(page);
}

void ZPageRecycle::deferred_delete(ZPage* page) {
  ZHeap::heap()->safe_destroy_page(page);
}

void ZPageRecycle::deferring_deletion(ZPage* page) {
  ZPage* cloned_page = new ZPage(*page);
  ZHeap::heap()->recycle_page(cloned_page);
}

enum ZPageAllocationStall {
  ZPageAllocationStallSuccess,
  ZPageAllocationStallFailed,
  ZPageAllocationStallStartGC
};

class ZPageAllocation : public StackObj {
  friend class ZList<ZPageAllocation>;

private:
  const uint8_t                 _type;
  const size_t                  _size;
  const ZAllocationFlags        _flags;
  const uint32_t                _seqnum;
  size_t                        _flushed;
  size_t                        _committed;
  ZList<ZPage>                  _pages;
  ZListNode<ZPageAllocation>    _node;
  ZFuture<ZPageAllocationStall> _stall_result;
  ZCycle*                       _cycle;
  ZGenerationId                 _generation;

public:
  ZPageAllocation(uint8_t type, size_t size, ZAllocationFlags flags, ZCycle* cycle, ZGenerationId generation) :
      _type(type),
      _size(size),
      _flags(flags),
      _seqnum(ZHeap::heap()->major_cycle()->seqnum()),
      _flushed(0),
      _committed(0),
      _pages(),
      _node(),
      _stall_result(),
      _cycle(cycle),
      _generation(generation) {}

  uint8_t type() const {
    return _type;
  }

  size_t size() const {
    return _size;
  }

  ZAllocationFlags flags() const {
    return _flags;
  }

  uint32_t seqnum() const {
    return _seqnum;
  }

  size_t flushed() const {
    return _flushed;
  }

  void set_flushed(size_t flushed) {
    _flushed = flushed;
  }

  size_t committed() const {
    return _committed;
  }

  void set_committed(size_t committed) {
    _committed = committed;
  }

  ZPageAllocationStall wait() {
    return _stall_result.get();
  }

  ZList<ZPage>* pages() {
    return &_pages;
  }

  void satisfy(ZPageAllocationStall result) {
    _stall_result.set(result);
  }

  ZCycle* cycle() {
    return _cycle;
  }

  ZGenerationId generation() const {
    return _generation;
  }
};

ZPageAllocator::ZPageAllocator(size_t min_capacity,
                               size_t initial_capacity,
                               size_t max_capacity) :
    _lock(),
    _cache(),
    _virtual(max_capacity),
    _physical(max_capacity),
    _min_capacity(min_capacity),
    _initial_capacity(initial_capacity),
    _max_capacity(max_capacity),
    _current_max_capacity(max_capacity),
    _capacity(0),
    _claimed(0),
    _used(0),
    _stalled(),
    _satisfied(),
    _unmapper(new ZUnmapper(this)),
    _uncommitter(new ZUncommitter(this)),
    _safe_destroy(),
    _safe_recycle(),
    _initialized(false) {

  if (!_virtual.is_initialized() || !_physical.is_initialized()) {
    return;
  }

  log_info_p(gc, init)("Min Capacity: " SIZE_FORMAT "M", min_capacity / M);
  log_info_p(gc, init)("Initial Capacity: " SIZE_FORMAT "M", initial_capacity / M);
  log_info_p(gc, init)("Max Capacity: " SIZE_FORMAT "M", max_capacity / M);
  if (ZPageSizeMedium > 0) {
    log_info_p(gc, init)("Medium Page Size: " SIZE_FORMAT "M", ZPageSizeMedium / M);
  } else {
    log_info_p(gc, init)("Medium Page Size: N/A");
  }
  log_info_p(gc, init)("Pre-touch: %s", AlwaysPreTouch ? "Enabled" : "Disabled");

  // Warn if system limits could stop us from reaching max capacity
  _physical.warn_commit_limits(max_capacity);

  // Check if uncommit should and can be enabled
  _physical.try_enable_uncommit(min_capacity, max_capacity);

  // Successfully initialized
  _initialized = true;
}

class ZPreTouchTask : public ZTask {
private:
  const ZPhysicalMemoryManager* const _physical;
  volatile zoffset                    _start;
  const zoffset                       _end;

public:
  ZPreTouchTask(const ZPhysicalMemoryManager* physical, zoffset start, zoffset end) :
      ZTask("ZPreTouchTask"),
      _physical(physical),
      _start(start),
      _end(end) {}

  virtual void work() {
    for (;;) {
      // Get granule offset
      const size_t size = ZGranuleSize;
      const zoffset offset = to_zoffset(Atomic::fetch_and_add((uintptr_t*)&_start, size));
      if (offset >= _end) {
        // Done
        break;
      }

      // Pre-touch granule
      _physical->pretouch(offset, size);
    }
  }
};

bool ZPageAllocator::prime_cache(ZWorkers* workers, size_t size) {
  ZAllocationFlags flags;

  flags.set_non_blocking();
  flags.set_low_address();

  ZPage* const page = alloc_page(ZPageTypeLarge, size, flags, NULL /* cycle */, ZGenerationId::young, ZPageAge::eden);
  if (page == NULL) {
    return false;
  }

  if (AlwaysPreTouch) {
    // Pre-touch page
    ZPreTouchTask task(&_physical, page->start(), page->end());
    workers->run_parallel(&task);
  }

  free_page(page, NULL /* cycle */);

  return true;
}

bool ZPageAllocator::initialize_heap(ZWorkers* workers) {
  if (!_initialized) {
    return false;
  }

  if (!prime_cache(workers, _initial_capacity)) {
    log_error_p(gc)("Failed to allocate initial Java heap (" SIZE_FORMAT "M)", _initial_capacity / M);
    return false;
  }

  return true;
}

size_t ZPageAllocator::min_capacity() const {
  return _min_capacity;
}

size_t ZPageAllocator::max_capacity() const {
  return _max_capacity;
}

size_t ZPageAllocator::soft_max_capacity() const {
  // Note that SoftMaxHeapSize is a manageable flag
  const size_t soft_max_capacity = Atomic::load(&SoftMaxHeapSize);
  const size_t current_max_capacity = Atomic::load(&_current_max_capacity);
  return MIN2(soft_max_capacity, current_max_capacity);
}

size_t ZPageAllocator::capacity() const {
  return Atomic::load(&_capacity);
}

size_t ZPageAllocator::used() const {
  return Atomic::load(&_used);
}

size_t ZPageAllocator::unused() const {
  const ssize_t capacity = (ssize_t)Atomic::load(&_capacity);
  const ssize_t used = (ssize_t)Atomic::load(&_used);
  const ssize_t claimed = (ssize_t)Atomic::load(&_claimed);
  const ssize_t unused = capacity - used - claimed;
  return unused > 0 ? (size_t)unused : 0;
}

ZPageAllocatorStats ZPageAllocator::stats(ZCycle* cycle) const {
  ZLocker<ZLock> locker(&_lock);
  return ZPageAllocatorStats(_min_capacity,
                             _max_capacity,
                             soft_max_capacity(),
                             _capacity,
                             _used,
                             cycle != NULL ? cycle->used_high() : 0,
                             cycle != NULL ? cycle->used_low() : 0,
                             cycle != NULL ? cycle->reclaimed() : 0);
}

size_t ZPageAllocator::increase_capacity(size_t size) {
  const size_t increased = MIN2(size, _current_max_capacity - _capacity);

  if (increased > 0) {
    // Update atomically since we have concurrent readers
    Atomic::add(&_capacity, increased);

    // Record time of last commit. When allocation, we prefer increasing
    // the capacity over flushing the cache. That means there could be
    // expired pages in the cache at this time. However, since we are
    // increasing the capacity we are obviously in need of committed
    // memory and should therefore not be uncommitting memory.
    _cache.set_last_commit();
  }

  return increased;
}

void ZPageAllocator::decrease_capacity(size_t size, bool set_max_capacity) {
  // Update atomically since we have concurrent readers
  Atomic::sub(&_capacity, size);

  if (set_max_capacity) {
    // Adjust current max capacity to avoid further attempts to increase capacity
    log_error_p(gc)("Forced to lower max Java heap size from "
                    SIZE_FORMAT "M(%.0f%%) to " SIZE_FORMAT "M(%.0f%%)",
                    _current_max_capacity / M, percent_of(_current_max_capacity, _max_capacity),
                    _capacity / M, percent_of(_capacity, _max_capacity));

    // Update atomically since we have concurrent readers
    Atomic::store(&_current_max_capacity, _capacity);
  }
}

void ZPageAllocator::increase_used(size_t size, ZCycle* cycle, ZGenerationId generation_id) {
  // Update atomically since we have concurrent readers
  const size_t used = Atomic::add(&_used, size);
  ZGeneration* generation = ZHeap::heap()->get_generation(generation_id);
  generation->increase_used(size);

  if (cycle != NULL) {
    // Allocating a page for the purpose of worker relocation has
    // a negative contribution to the number of reclaimed bytes.
    cycle->decrease_reclaimed(size);
  }

  ZHeap::heap()->minor_cycle()->update_used(used);
  ZHeap::heap()->major_cycle()->update_used(used);
}

void ZPageAllocator::decrease_used(size_t size, ZCycle* cycle, ZGenerationId generation_id) {
  // Update atomically since we have concurrent readers
  const size_t used = Atomic::sub(&_used, size);
  ZGeneration* generation = ZHeap::heap()->get_generation(generation_id);
  generation->decrease_used(size);

  // Only pages explicitly released after relocation count as
  // reclaimed bytes. This is denoted by a non-NULL "cycle"
  // for the cycle that performed the recycling. When undoing an
  // allocation, this parameter is NULL.
  if (cycle != NULL) {
    cycle->increase_reclaimed(size);
  }

  ZHeap::heap()->minor_cycle()->update_used(used);
  ZHeap::heap()->major_cycle()->update_used(used);
}

bool ZPageAllocator::commit_page(ZPage* page) {
  // Commit physical memory
  return _physical.commit(page->physical_memory());
}

void ZPageAllocator::uncommit_page(ZPage* page) {
  if (!ZUncommit) {
    return;
  }

  // Uncommit physical memory
  _physical.uncommit(page->physical_memory());
}

void ZPageAllocator::map_page(const ZPage* page) const {
  // Map physical memory
  _physical.map(page->start(), page->physical_memory());
}

void ZPageAllocator::unmap_page(const ZPage* page) const {
  // Unmap physical memory
  _physical.unmap(page->start(), page->size());
}

void ZPageAllocator::safe_destroy_page(ZPage* page) {
  // Destroy page safely
  _safe_destroy(page);
}

void ZPageAllocator::destroy_page(ZPage* page) {
  // Free virtual memory
  _virtual.free(page->virtual_memory());

  // Free physical memory
  _physical.free(page->physical_memory());

  // Destroy page safely
  safe_destroy_page(page);
}

bool ZPageAllocator::is_alloc_allowed(size_t size) const {
  const size_t available = _current_max_capacity - _used - _claimed;
  return available >= size;
}

bool ZPageAllocator::alloc_page_common_inner(uint8_t type, size_t size, ZList<ZPage>* pages) {
  if (!is_alloc_allowed(size)) {
    // Out of memory
    return false;
  }

  // Try allocate from the page cache
  ZPage* const page = _cache.alloc_page(type, size);
  if (page != NULL) {
    // Success
    pages->insert_last(page);
    return true;
  }

  // Try increase capacity
  const size_t increased = increase_capacity(size);
  if (increased < size) {
    // Could not increase capacity enough to satisfy the allocation
    // completely. Flush the page cache to satisfy the remainder.
    const size_t remaining = size - increased;
    _cache.flush_for_allocation(remaining, pages);
  }

  // Success
  return true;
}

bool ZPageAllocator::alloc_page_common(ZPageAllocation* allocation) {
  const uint8_t type = allocation->type();
  const size_t size = allocation->size();
  const ZAllocationFlags flags = allocation->flags();
  ZList<ZPage>* const pages = allocation->pages();

  if (!alloc_page_common_inner(type, size, pages)) {
    // Out of memory
    return false;
  }

  // Updated used statistics
  increase_used(size, allocation->cycle(), allocation->generation());

  // Success
  return true;
}

static void check_out_of_memory_during_initialization() {
  if (!is_init_completed()) {
    vm_exit_during_initialization("java.lang.OutOfMemoryError", "Java heap too small");
  }
}

bool ZPageAllocator::alloc_page_stall(ZPageAllocation* allocation) {
  ZStatTimerFIXME timer(ZCriticalPhaseAllocationStall);
  EventZAllocationStall event;
  ZPageAllocationStall result;

  // We can only block if the VM is fully initialized
  check_out_of_memory_during_initialization();

  do {
    // Start asynchronous GC
    ZCollectedHeap::heap()->collect(GCCause::_z_major_allocation_stall);

    // Wait for allocation to complete, fail or request a GC
    result = allocation->wait();
  } while (result == ZPageAllocationStallStartGC);

  {
    //
    // We grab the lock here for two different reasons:
    //
    // 1) Guard deletion of underlying semaphore. This is a workaround for
    // a bug in sem_post() in glibc < 2.21, where it's not safe to destroy
    // the semaphore immediately after returning from sem_wait(). The
    // reason is that sem_post() can touch the semaphore after a waiting
    // thread have returned from sem_wait(). To avoid this race we are
    // forcing the waiting thread to acquire/release the lock held by the
    // posting thread. https://sourceware.org/bugzilla/show_bug.cgi?id=12674
    //
    // 2) Guard the list of satisfied pages.
    //
    ZLocker<ZLock> locker(&_lock);
    _satisfied.remove(allocation);
  }

  // Send event
  event.commit(allocation->type(), allocation->size());

  return (result == ZPageAllocationStallSuccess);
}

bool ZPageAllocator::alloc_page_or_stall(ZPageAllocation* allocation) {
  {
    ZLocker<ZLock> locker(&_lock);

    if (alloc_page_common(allocation)) {
      // Success
      return true;
    }

    // Failed
    if (allocation->flags().non_blocking()) {
      // Don't stall
      return false;
    }

    // Enqueue allocation request
    _stalled.insert_last(allocation);
  }

  // Stall
  return alloc_page_stall(allocation);
}

ZPage* ZPageAllocator::alloc_page_create(ZPageAllocation* allocation) {
  const size_t size = allocation->size();

  // Allocate virtual memory. To make error handling a lot more straight
  // forward, we allocate virtual memory before destroying flushed pages.
  // Flushed pages are also unmapped and destroyed asynchronously, so we
  // can't immediately reuse that part of the address space anyway.
  const ZVirtualMemory vmem = _virtual.alloc(size, allocation->flags().low_address());
  if (vmem.is_null()) {
    log_error(gc)("Out of address space");
    return NULL;
  }

  ZPhysicalMemory pmem;
  size_t flushed = 0;

  // Harvest physical memory from flushed pages
  ZListRemoveIterator<ZPage> iter(allocation->pages());
  for (ZPage* page; iter.next(&page);) {
    flushed += page->size();

    // Harvest flushed physical memory
    ZPhysicalMemory& fmem = page->physical_memory();
    pmem.add_segments(fmem);
    fmem.remove_segments();

    // Unmap and destroy page
    _unmapper->unmap_and_destroy_page(page);
  }

  if (flushed > 0) {
    allocation->set_flushed(flushed);

    // Update statistics
    ZStatInc(ZCounterPageCacheFlush, flushed);
    log_debug(gc, heap)("Page Cache Flushed: " SIZE_FORMAT "M", flushed / M);
  }

  // Allocate any remaining physical memory. Capacity and used has
  // already been adjusted, we just need to fetch the memory, which
  // is guaranteed to succeed.
  if (flushed < size) {
    const size_t remaining = size - flushed;
    allocation->set_committed(remaining);
    _physical.alloc(pmem, remaining);
  }

  // Create new page
  return new ZPage(allocation->type(), vmem, pmem);
}

static bool is_alloc_satisfied(ZPageAllocation* allocation) {
  // The allocation is immediately satisfied if the list of pages contains
  // exactly one page, with the type and size that was requested.
  return allocation->pages()->size() == 1 &&
         allocation->pages()->first()->type() == allocation->type() &&
         allocation->pages()->first()->size() == allocation->size();
}

ZPage* ZPageAllocator::alloc_page_finalize(ZPageAllocation* allocation) {
  // Fast path
  if (is_alloc_satisfied(allocation)) {
    return allocation->pages()->remove_first();
  }

  // Slow path
  ZPage* const page = alloc_page_create(allocation);
  if (page == NULL) {
    // Out of address space
    return NULL;
  }

  // Commit page
  if (commit_page(page)) {
    // Success
    map_page(page);
    return page;
  }

  // Failed or partially failed. Split of any successfully committed
  // part of the page into a new page and insert it into list of pages,
  // so that it will be re-inserted into the page cache.
  ZPage* const committed_page = page->split_committed();
  destroy_page(page);

  if (committed_page != NULL) {
    map_page(committed_page);
    allocation->pages()->insert_last(committed_page);
  }

  return NULL;
}

void ZPageAllocator::alloc_page_failed(ZPageAllocation* allocation) {
  ZLocker<ZLock> locker(&_lock);

  size_t freed = 0;

  // Free any allocated/flushed pages
  ZListRemoveIterator<ZPage> iter(allocation->pages());
  for (ZPage* page; iter.next(&page);) {
    freed += page->size();
    free_page_inner(page, NULL /* cycle */);
  }

  // Adjust capacity and used to reflect the failed capacity increase
  const size_t remaining = allocation->size() - freed;
  decrease_used(remaining, NULL /* cycle */, allocation->generation());
  decrease_capacity(remaining, true /* set_max_capacity */);

  // Try satisfy stalled allocations
  satisfy_stalled();
}

ZPage* ZPageAllocator::alloc_page(uint8_t type, size_t size, ZAllocationFlags flags, ZCycle* cycle, ZGenerationId generation_id, ZPageAge age) {
  EventZPageAllocation event;

retry:
  ZPageAllocation allocation(type, size, flags, cycle, generation_id);

  // Allocate one or more pages from the page cache. If the allocation
  // succeeds but the returned pages don't cover the complete allocation,
  // then finalize phase is allowed to allocate the remaining memory
  // directly from the physical memory manager. Note that this call might
  // block in a safepoint if the non-blocking flag is not set.
  if (!alloc_page_or_stall(&allocation)) {
    // Out of memory
    return NULL;
  }

  ZPage* const page = alloc_page_finalize(&allocation);
  if (page == NULL) {
    // Failed to commit or map. Clean up and retry, in the hope that
    // we can still allocate by flushing the page cache (more aggressively).
    alloc_page_failed(&allocation);
    goto retry;
  }

  // Reset page. This updates the page's sequence number and must
  // be done after we potentially blocked in a safepoint (stalled)
  // where the global sequence number was updated.
  page->reset(generation_id, age, false /* flip */, false /* in-place */);

  // Update allocation statistics. Exclude worker relocations to avoid
  // artificial inflation of the allocation rate during relocation.
  if (!flags.worker_relocation()) {
    // Note that there are two allocation rate counters, which have
    // different purposes and are sampled at different frequencies.
    const size_t bytes = page->size();
    ZStatInc(ZCounterMutatorAllocationRate, bytes);
    ZStatInc(ZStatMutatorAllocRate::counter(), bytes);
  }

  // Send event
  event.commit(type, size, allocation.flushed(), allocation.committed(),
               page->physical_memory().nsegments(), flags.non_blocking());

  return page;
}

void ZPageAllocator::satisfy_stalled() {
  for (;;) {
    ZPageAllocation* const allocation = _stalled.first();
    if (allocation == NULL) {
      // Allocation queue is empty
      return;
    }

    if (!alloc_page_common(allocation)) {
      // Allocation could not be satisfied, give up
      return;
    }

    // Allocation succeeded, dequeue and satisfy allocation request.
    // Note that we must dequeue the allocation request first, since
    // it will immediately be deallocated once it has been satisfied.
    _stalled.remove(allocation);
    _satisfied.insert_last(allocation);
    allocation->satisfy(ZPageAllocationStallSuccess);
  }
}

void ZPageAllocator::recycle_page(ZPage* page) {
  // Cache page
  _cache.free_page(page);
}

void ZPageAllocator::free_page_inner(ZPage* page, ZCycle* cycle) {
  // Update used statistics
  decrease_used(page->size(), cycle, page->generation_id());

  // Set time when last used
  page->set_last_used();

  // Recycle page
  _safe_recycle(page);
}

void ZPageAllocator::free_page(ZPage* page, ZCycle* cycle) {
  ZLocker<ZLock> locker(&_lock);

  // Free page
  free_page_inner(page, cycle);

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void ZPageAllocator::free_pages(const ZArray<ZPage*>* pages, ZCycle* cycle) {
  ZLocker<ZLock> locker(&_lock);

  // Free pages
  ZArrayIterator<ZPage*> iter(pages);
  for (ZPage* page; iter.next(&page);) {
    free_page_inner(page, cycle);
  }

  // Try satisfy stalled allocations
  satisfy_stalled();
}

size_t ZPageAllocator::uncommit(uint64_t* timeout) {
  // We need to join the suspendible thread set while manipulating capacity and
  // used, to make sure GC safepoints will have a consistent view.
  ZList<ZPage> pages;
  size_t flushed;

  {
    SuspendibleThreadSetJoiner joiner;
    ZLocker<ZLock> locker(&_lock);

    // Never uncommit below min capacity. We flush out and uncommit chunks at
    // a time (~0.8% of the max capacity, but at least one granule and at most
    // 256M), in case demand for memory increases while we are uncommitting.
    const size_t retain = MAX2(_used, _min_capacity);
    const size_t release = _capacity - retain;
    const size_t limit = MIN2(align_up(_current_max_capacity >> 7, ZGranuleSize), 256 * M);
    const size_t flush = MIN2(release, limit);

    // Flush pages to uncommit
    flushed = _cache.flush_for_uncommit(flush, &pages, timeout);
    if (flushed == 0) {
      // Nothing flushed
      return 0;
    }

    // Record flushed pages as claimed
    Atomic::add(&_claimed, flushed);
  }

  // Unmap, uncommit, and destroy flushed pages
  ZListRemoveIterator<ZPage> iter(&pages);
  for (ZPage* page; iter.next(&page);) {
    unmap_page(page);
    uncommit_page(page);
    destroy_page(page);
  }

  {
    SuspendibleThreadSetJoiner joiner;
    ZLocker<ZLock> locker(&_lock);

    // Adjust claimed and capacity to reflect the uncommit
    Atomic::sub(&_claimed, flushed);
    decrease_capacity(flushed, false /* set_max_capacity */);
  }

  return flushed;
}

void ZPageAllocator::enable_deferred_destroy() const {
  _safe_destroy.enable_deferred_delete();
}

void ZPageAllocator::disable_deferred_destroy() const {
  _safe_destroy.disable_deferred_delete();
}

void ZPageAllocator::enable_deferred_recycle() const {
  _safe_recycle.enable_deferred_delete();
}

void ZPageAllocator::disable_deferred_recycle() const {
  _safe_recycle.disable_deferred_delete();
}

bool ZPageAllocator::is_alloc_stalled() const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  return !_stalled.is_empty();
}

void ZPageAllocator::check_out_of_memory() {
  ZLocker<ZLock> locker(&_lock);

  // Fail allocation requests that were enqueued before the
  // last GC cycle started, otherwise start a new GC cycle.
  for (ZPageAllocation* allocation = _stalled.first(); allocation != NULL; allocation = _stalled.first()) {
    if (allocation->seqnum() == ZHeap::heap()->major_cycle()->seqnum()) {
      // Start a new GC cycle, keep allocation requests enqueued
      allocation->satisfy(ZPageAllocationStallStartGC);
      return;
    }

    // Out of memory, fail allocation request
    _stalled.remove(allocation);
    _satisfied.insert_last(allocation);
    allocation->satisfy(ZPageAllocationStallFailed);
  }
}

void ZPageAllocator::threads_do(ThreadClosure* tc) const {
  tc->do_thread(_unmapper);
  tc->do_thread(_uncommitter);
}
