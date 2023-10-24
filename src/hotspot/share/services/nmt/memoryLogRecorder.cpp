/*
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
 *
 */

#include "precompiled.hpp"
#include "jvm.h"
#include "runtime/globals.hpp"
#include "services/nmtCommon.hpp"
#include "services/nmt/memoryLogRecorder.hpp"
#include "services/mallocHeader.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/nativeCallStack.hpp"

#if defined(LINUX)
#include <malloc.h>
#elif defined(__APPLE__)
#include <malloc/malloc.h>
#endif

#ifdef ASSERT

constexpr size_t _histogram_horizontal_space = 100;
constexpr double _histogram_cutoff = 0.25;
constexpr size_t _feedback_cutoff_count = 500000;

size_t* NMT_MemoryLogRecorder::malloc_buckets = nullptr;
size_t NMT_MemoryLogRecorder::malloc_buckets_count = 0;
size_t* NMT_MemoryLogRecorder::good_sizes_counts = nullptr;
size_t* NMT_MemoryLogRecorder::good_sizes_totals = nullptr;

int compare(const void* ptr_a, const void* ptr_b) {
  size_t a = * ( (size_t*) ptr_a );
  size_t b = * ( (size_t*) ptr_b );
  if ( a > b ) return 1;
  else if ( a < b ) return -1;
  else return 0;
}

static inline double percent_diff(double initial_value, double final_value) {
  return 100.0 * (final_value - initial_value) / initial_value;
}

static inline double ratio(double smaller, double bigger) {
  return 100.0 * smaller / bigger;
}

// on macOS malloc currently (macOS 13) returns the same value for same sizes
// on Linux malloc can return different values for the same sizes
static inline size_t _malloc_good_size_native(size_t size) {
  void *ptr = malloc(size);
  assert(ptr != nullptr, "must be, size=%zu", size);
  size_t actual = 0;
#if defined(LINUX)
  actual = malloc_usable_size(ptr);
#elif defined(WINDOWS)
  actual = _msize(ptr);
#elif defined(__APPLE__)
  actual = malloc_size(ptr);
#endif
  free(ptr);
  return actual;
}

size_t NMT_MemoryLogRecorder::_malloc_good_size_stats(size_t size) {
  assert(good_sizes_counts != nullptr, "good_sizes_counts != nullptr");
  assert(good_sizes_totals != nullptr, "good_sizes_counts != nullptr");
  for (size_t i=0; i<malloc_buckets_count; i++) {
    if (malloc_buckets[i] == size) {
      // return average actual size
      return good_sizes_totals[i]/good_sizes_counts[i];
    }
  }
  // don't have this size in our stats, so estimate it
  return _malloc_good_size_native(size);
}

size_t NMT_MemoryLogRecorder::_malloc_good_size(size_t size) {
  return _malloc_good_size_stats(size);
}

void NMT_MemoryLogRecorder::calculate_good_sizes(Entry* entries, size_t count) {
  find_malloc_buckets_sizes(entries, count);
  assert(good_sizes_counts == nullptr, "good_sizes_counts == nullptr");
  assert(good_sizes_totals == nullptr, "good_sizes_counts == nullptr");
  good_sizes_counts = (size_t*)calloc(malloc_buckets_count, sizeof(size_t));
  good_sizes_totals = (size_t*)calloc(malloc_buckets_count, sizeof(size_t));
  assert(good_sizes_counts != nullptr, "good_sizes_counts != nullptr");
  assert(good_sizes_totals != nullptr, "good_sizes_counts != nullptr");

  for (size_t c=0; c<count; c++) {
    Entry* e = access_non_empty(entries, c);
    if ((e != nullptr) && is_alloc(e)) {
      for (size_t i=0; i<malloc_buckets_count; i++) {
        if (malloc_buckets[i] == e->requested) {
          good_sizes_counts[i] += 1;
          good_sizes_totals[i] += e->actual;
          break;
        }
      }
    }
  }

#if 0
  fprintf(stderr, "\n");
  size_t b = 0;
  for (size_t i=0; i<malloc_buckets_count; i++) {
    if (malloc_buckets[i] > 0) {
      fprintf(stderr, "%3ld %8ld %8ld %12ld",
              b++, malloc_buckets[i], good_sizes_counts[i], good_sizes_totals[i]);
      if (good_sizes_counts[i] > 0) {
        fprintf(stderr, " [%12ld][%.3f]\n",
                good_sizes_totals[i]/good_sizes_counts[i], (double)good_sizes_totals[i]/(double)good_sizes_counts[i]);
      } else {
        fprintf(stderr, " [%12ld][%.3f]\n", 0L, 0.0);
      }
    }
  }
  fprintf(stderr, "\n");
#endif
}

void NMT_MemoryLogRecorder::find_malloc_buckets_sizes(Entry* entries, size_t count) {
  if (malloc_buckets == nullptr) {
    assert(malloc_buckets_count == 0, "malloc_buckets_count == 0");
    size_t buckets_max = 4;
    malloc_buckets = (size_t*)calloc(buckets_max, sizeof(size_t));
    assert(malloc_buckets != nullptr, "malloc_buckets != nullptr");
    for (size_t c=0; c<count; c++) {
      Entry* e = access_non_empty(entries, c);
      if (e != nullptr) {
        bool found = false;
        while (!found) {
          for (size_t i=0; i<buckets_max; i++) {
            if ((malloc_buckets[i] == 0) || (malloc_buckets[i] == e->requested)) {
              found = true;
              malloc_buckets[i] = e->requested;
              malloc_buckets_count = MAX(malloc_buckets_count, i);
              break;
            }
          }
          if (!found) {
            buckets_max *= 2;
            malloc_buckets = (size_t*)realloc(malloc_buckets, buckets_max*sizeof(size_t));
            assert(malloc_buckets != nullptr, "malloc_buckets != nullptr");
            memset(&malloc_buckets[buckets_max/2], 0, buckets_max*sizeof(size_t)/2);
          }
        }
      }
    }
    assert(malloc_buckets_count > 0, "malloc_buckets_count > 0");
    assert(malloc_buckets != nullptr, "malloc_buckets != nullptr");
    qsort(malloc_buckets, malloc_buckets_count, sizeof(size_t), compare);
  }

#if 0
  fprintf(stderr, "\n");
  fprintf(stderr, "malloc_buckets_count: %zu\n", malloc_buckets_count);
  size_t b = 0;
  for (size_t i=0; i<malloc_buckets_count; i++) {
    if (malloc_buckets[i] > 0) {
      fprintf(stderr, "malloc_buckets[%zu]: %zu\n", i, malloc_buckets[i]);
    }
  }
  fprintf(stderr, "\n");
#endif
}

void NMT_MemoryLogRecorder::print_histogram(Entry* entries, size_t count, double cutoff) {
  find_malloc_buckets_sizes(entries, count);

  size_t* histogram_counts = (size_t*)calloc(malloc_buckets_count, sizeof(size_t));
  size_t* histogram_actual_sizes = (size_t*)calloc(malloc_buckets_count, sizeof(size_t));
  assert(histogram_counts != nullptr, "histogram_counts != nullptr");
  assert(histogram_actual_sizes != nullptr, "histogram_actual_sizes != nullptr");

  size_t total_requested = 0;
  size_t total_actual = 0;
  for (size_t c=0; c<count; c++) {
    Entry* e = access_non_empty(entries, c);
    if ((e != nullptr) && (is_alloc(e))) {
      total_requested += e->requested;
      total_actual += e->actual;
    }
  }
  size_t alloc_overhead = (total_actual - total_requested);

  // find total_actual sizes for alloc requests and count how many of them there are
  for (size_t i=0; i<malloc_buckets_count; i++) {
    for (size_t c=0; c<count; c++) {
      Entry* e = access_non_empty(entries, c);
      if ((e != nullptr) && (malloc_buckets[i] == e->requested)) {
        if (histogram_actual_sizes[i] > 0) {
          // just double checking
          assert(histogram_actual_sizes[i] = e->actual, "histogram_actual_sizes[] = e->total_actual");
        }
        histogram_actual_sizes[i] = e->actual;
        histogram_counts[i]++;
      }
    }
  }

  size_t r_total = 0;
  size_t a_total = 0;
  size_t o_total = 0;
  size_t buckets_count = 0;
  fprintf(stderr, "Histogram of memory overhead (quadratic scale)\n");
  fprintf(stderr, "----------------------------------------------\n");
  fprintf(stderr, "requested:    actual: overhead:  count: ratio:\n");
  for (size_t i=0; i<malloc_buckets_count; i++) {
    if (malloc_buckets[i] > 0) {
      buckets_count++;

      char flag = (malloc_buckets[i] == histogram_actual_sizes[i]) ? '=' : ' ';
      size_t overhead = histogram_counts[i] * (histogram_actual_sizes[i] - malloc_buckets[i]);
      double overhead_ratio = ratio(overhead, alloc_overhead);
      // quadratic function which goes through 3 points: (0,0) (25,50) (100,100)
      // https://www.mathepower.com/en/quadraticfunctions.php
      size_t mark = MIN((size_t)round(-(1.0/(double)_histogram_horizontal_space)*overhead_ratio*overhead_ratio + 2.0*overhead_ratio), 100);

      r_total += histogram_counts[i] * malloc_buckets[i];
      a_total += histogram_counts[i] * histogram_actual_sizes[i];
      o_total += overhead;

      if (overhead_ratio > cutoff) {
        if (overhead_ratio < 10.0) {
          fprintf(stderr, "%9zu%c %9zu %9zu   %6zu  %02.3f ",
                  malloc_buckets[i], flag, histogram_actual_sizes[i], overhead, histogram_counts[i], overhead_ratio);
        } else {
          fprintf(stderr, "%9zu%c %9zu %9zu   %6zu  %02.2f ",
                  malloc_buckets[i], flag, histogram_actual_sizes[i], overhead, histogram_counts[i], overhead_ratio);
        }
        for (size_t j=0; j<mark; j++) {
          fprintf(stderr, "*");
        }
        for (size_t j=mark; j<=_histogram_horizontal_space; j++) {
          fprintf(stderr, ".");
        }
        fprintf(stderr, "\n");
      }
    }
  }
  fprintf(stderr, "\nnative malloc used %zu distinct allocation sizes\n\n", buckets_count);

//  assert(r_total == total_requested, "r_total:%zu == total_requested:%zu", r_total, total_requested);
//  assert(a_total == total_actual, "a_total:%zu == total_actual:%zu", a_total, total_actual);
//  assert(a_total-r_total == o_total, "a_total-r_total:%zu == o_total:%zu", a_total-r_total, o_total);
//  assert(alloc_overhead == o_total, "alloc_overhead:%zu == total_requested:%zu", alloc_overhead, o_total);

  free(histogram_actual_sizes);
  free(histogram_counts);
}

void NMT_MemoryLogRecorder::print_entry(Entry* e) {
  if (e != nullptr) {
    fprintf(stderr, "{ %18p, %18p", e->ptr, e->old);
    for (int i=0; i<NMT_TrackingStackDepth; i++) {
      fprintf(stderr, ", %18p", e->stack[i]);
    }
    fprintf(stderr, ", %7u, %7u, %7u", (unsigned)e->requested, (unsigned)e->actual,
            (unsigned)e->flags);
    if (!is_empty(e)) {
      fprintf(stderr, ", \"%s\"},\n", NMTUtil::flag_to_name(e->flags));
    } else {
      fprintf(stderr, "},\n");
    }
  } else {
    fprintf(stderr, "nullptr\n");
  }
}

void NMT_MemoryLogRecorder::print_records(Entry* entries, size_t count) {
  for (size_t c=0; c<count; c++) {
    Entry* e = &entries[c];
    print_entry(e);
  }
}

void NMT_MemoryLogRecorder::report_by_thread(Entry* entries, size_t count) {
  size_t threads_max = 4;
  size_t thread_count = 0;
  address* threads = (address*)calloc(threads_max, sizeof(address));
  assert(threads != nullptr, "threads != nullptr");
  for (size_t c=0; c<count; c++) {
    Entry* e = access_non_empty(entries, c);
    if (e != nullptr) {
      bool found = false;
      while (!found) {
        for (size_t i=0; i<threads_max; i++) {
          if ((threads[i] == nullptr) || (threads[i] == e->thread)) {
            found = true;
            threads[i] = e->thread;
            thread_count = MAX(thread_count, i);
            break;
          }
        }
        if (!found) {
          threads_max *= 2;
          threads = (address*)realloc(threads, threads_max*sizeof(address));
          assert(threads != nullptr, "threads != nullptr");
          memset(&threads[threads_max/2], 0, threads_max*sizeof(size_t)/2);
        }
      }
    }
  }
#if 0
  for (size_t i=0; i<threads_max; i++) {
    if (threads[i] != nullptr) {
      fprintf(stderr, " threads[%zu]: %p\n", i, threads[i]);
    }
  }
#endif

  size_t* counter_malloc = (size_t*)calloc(thread_count, sizeof(size_t));
  size_t* counter_realloc = (size_t*)calloc(thread_count, sizeof(size_t));
  size_t* counter_free = (size_t*)calloc(thread_count, sizeof(size_t));
  size_t* sizes_requested = (size_t*)calloc(thread_count, sizeof(size_t));
  size_t* sizes_actual = (size_t*)calloc(thread_count, sizeof(size_t));
  size_t* sizes_freed = (size_t*)calloc(thread_count, sizeof(size_t));
  assert(counter_malloc != nullptr, "counter_malloc != nullptr");
  assert(counter_realloc != nullptr, "counter_realloc != nullptr");
  assert(counter_free != nullptr, "counter_free != nullptr");
  assert(sizes_requested != nullptr, "sizes_requested != nullptr");
  assert(sizes_actual != nullptr, "sizes_actual != nullptr");
  assert(sizes_freed != nullptr, "sizes_freed != nullptr");

  size_t total_count_mallocs = 0;
  size_t total_count_reallocs = 0;
  size_t total_count_frees = 0;
  size_t total_size_requested = 0;
  size_t total_size_actual = 0;
  size_t total_size_freed = 0;
  for (size_t c=0; c<count; c++) {
    Entry* e = access_non_empty(entries, c);
    if (e != nullptr) {
      // count the instances of malloc, realloc and free
      for (size_t i=0; i<thread_count; i++) {
        if (threads[i] == e->thread) {
          if (is_malloc(e)) {
            counter_malloc[i]++;
            total_count_mallocs++;
          } else if (is_realloc(e)) {
            counter_realloc[i]++;
            total_count_reallocs++;
          } else if (is_free(e)) {
            counter_free[i]++;
            total_count_frees++;
          }
          if (is_alloc(e)) {
            total_size_requested += e->requested;
            sizes_requested[i] += e->requested;
            total_size_actual += e->actual;
            sizes_actual[i] += e->actual;
          } else { // free
            total_size_freed += e->actual;
            sizes_freed[i] += e->actual;
          }
          break;
        }
      }
    }
  }


  fprintf(stderr, "\n");
  fprintf(stderr, "------------------------------------------------------------------------------------------------------\n");
  fprintf(stderr, "                      thread name:  mallocs: reallocs:     free:   requested:   allocated:      freed:\n");
  fprintf(stderr, "                                     (count)   (count)   (count)      (bytes)      (bytes)     (bytes)\n");
  fprintf(stderr, "------------------------------------------------------------------------------------------------------\n");
  for (size_t i=0; i<thread_count; i++) {
    if (threads[i] != nullptr) {
      char buf[32] = { 0 };
#if defined(LINUX) || defined(__APPLE__)
      pthread_getname_np((pthread_t)threads[i], &buf[0], sizeof(buf));
#elif defined(WINDOWS)
      // ???
#endif
      if (strlen(&buf[0]) == 0) {
        if (i==0) {
          strcpy(&buf[0], "Main");
        } else {
          strcpy(&buf[0], "???");
        }
      }
      fprintf(stderr, "%33s %9ld %9ld %9ld %12ld %12ld %12ld\n", buf,
              counter_malloc[i], counter_realloc[i], counter_free[i],
              sizes_requested[i], sizes_actual[i], sizes_freed[i]);
    } else {
      break;
    }
  }

  fprintf(stderr, "------------------------------------------------------------------------------------------------------\n");
  fprintf(stderr, "                          TOTALS: %9ld %9ld %9ld %12ld %12ld %12ld\n",
          total_count_mallocs, total_count_reallocs, total_count_frees,
          total_size_requested, total_size_actual, total_size_freed);

  fprintf(stderr, "\nfound %zu threads\n", thread_count);

  free(sizes_freed);
  free(sizes_actual);
  free(sizes_requested);
  free(counter_free);
  free(counter_realloc);
  free(counter_malloc);
  free(threads);
}

NMT_MemoryLogRecorder::Entry* NMT_MemoryLogRecorder::find_free_entry(Entry* entries, size_t count) {
  Entry* e = access_non_empty(entries, count);
  if (e != nullptr) {
    for (size_t b=count-1; b>0; b--) {
      Entry* found = access_non_empty(entries, b);
      if ((found != nullptr) && (found->ptr == e->ptr)) {
        return found;
      }
    }
  }
  return nullptr;
}

NMT_MemoryLogRecorder::Entry* NMT_MemoryLogRecorder::find_realloc_entry(Entry* entries, size_t count) {
  Entry* e = access_non_empty(entries, count);
  if (e != nullptr) {
    for (size_t b=count-1; b>0; b--) {
      Entry* found = access_non_empty(entries, b);
      if ((found != nullptr) && (found->ptr == e->old)) {
        return found;
      }
    }
  }
  return nullptr;
}

void NMT_MemoryLogRecorder::consolidate(Entry* entries, size_t count, size_t start) {
  assert(start < count, "start < count");
  constexpr size_t steps = 99;
  size_t gap = count / steps;
  for (size_t c=start; c<count; c++) {
    if ((count > _feedback_cutoff_count) && (c%gap == 0)) {
      fprintf(stderr, "%3ld", (steps - (c/gap)));
    }
    Entry* e = &entries[c];
    if (is_alloc(e)) {
      if (is_realloc(e)) {
        Entry* found = find_realloc_entry(entries, c);
        if (found != nullptr) {
          assert(found->flags == e->flags, "found->flags == e->flags");
          assert(is_alloc(found), "is_alloc(found)");
          memset(found, 0, sizeof(Entry));
        } else {
          // realloc without initial malloc -> turn it into a malloc
          e->old = nullptr;
        }
      }
    } else if (is_free(e)) {
      Entry* found = find_free_entry(entries, c);
      if (found != nullptr) {
        assert(found->actual == e->actual, "found->actual == e->actual");
        assert(is_alloc(found), "is_alloc(found)");
        memset(e, 0, sizeof(Entry));
        memset(found, 0, sizeof(Entry));
      }
    } else {
      assert(false, "HUH?");
    }
  }
}

void NMT_MemoryLogRecorder::print_summary(Entry* entries, size_t count) {
  size_t overhead_per_malloc = MemTracker::overhead_per_malloc();
  long total_requested = 0;
  long total_requested_no_NMT = 0;
  long total_actual = 0;
  long total_actual_no_NMT = 0;
  long total_NMTObjects = 0;
  long count_mallocs = 0;
  long count_reallocs = 0;
  long count_frees = 0;
  long count_Objects = 0;
  long count_NMTObjects = 0;
  constexpr size_t steps = 99;
  size_t gap = count / steps;
  for (size_t c=0; c<count; c++) {
    if ((count > _feedback_cutoff_count) && (c%gap == 0)) {
      fprintf(stderr, "%3ld", (steps - (c/gap)));
    }
    Entry* e = access_non_empty(entries, c);
    if (e == nullptr) {
      //
    } else if (is_alloc(e)) {
      if (is_malloc(e)) {
        count_mallocs++;
        count_Objects++;
        total_requested += e->requested;
        total_actual += e->actual;
        if (is_nmt(e)) {
          count_NMTObjects++;
          total_NMTObjects += e->actual;
        } else {
          total_requested_no_NMT += (e->requested - overhead_per_malloc);
          total_actual_no_NMT += _malloc_good_size(e->requested - overhead_per_malloc); // this is an estimate
        }
      } else if (is_realloc(e)) {
        count_reallocs++;
        Entry* found = find_realloc_entry(entries, c);
        if (found != nullptr) {
          assert(found->flags == e->flags, "found->flags == e->flags");
          assert(is_alloc(found), "is_alloc(found)");
#if 0
          fprintf(stderr, "\nREALLOC:\n");
          print_entry(found);
          print_entry(e);
#endif
          if (is_nmt(e)) {
            total_NMTObjects += e->actual;
            total_requested += e->requested;
            total_actual += e->actual;

            total_NMTObjects -= found->actual;
            total_requested -= found->requested;
            total_actual -= found->actual;
          } else {
            total_requested += e->requested;
            total_actual += e->actual;
            total_requested_no_NMT += (e->requested - overhead_per_malloc);
            total_actual_no_NMT += _malloc_good_size(e->requested - overhead_per_malloc); // this is an estimate

            total_requested -= found->requested;
            total_actual -= found->actual;
            total_requested_no_NMT -= (found->requested - overhead_per_malloc);
            total_actual_no_NMT -= _malloc_good_size(found->requested - overhead_per_malloc); // this is an estimate
          }
        }
      }
    } else if (is_free(e)) {
      Entry* found = find_free_entry(entries, c);
      if (found != nullptr) {
        assert(found->actual == e->actual, "found->actual == e->actual");
        assert(is_alloc(found), "is_alloc(found)");
#if 0
        fprintf(stderr, "FREE:\n");
        print_entry(found);
        print_entry(e);
#endif
        count_Objects--;
        count_frees++;
        total_requested -= found->requested;
        total_actual -= found->actual;
        if (is_nmt(e)) {
          count_NMTObjects--;
          total_NMTObjects -= found->actual;
        } else {
          total_requested_no_NMT -= (found->requested - overhead_per_malloc);
          total_actual_no_NMT -= _malloc_good_size(found->requested - overhead_per_malloc); // this is an estimate
        }
      }
    } else {
      print_entry(e);
      assert(false, "HUH?");
    }
  }
  fprintf(stderr, "\n");
  
  long alloc_overhead = (total_actual - total_requested);

  fprintf(stderr, "\n\n");
  fprintf(stderr, "                             Current requested: %12ld bytes, %4ld Mb\n",
          total_requested, total_requested/1024/1024);
  fprintf(stderr, "                                Current actual: %12ld bytes, %4ld Mb\n",
          total_actual, total_actual/1024/1024);
  double overhead_ratio_requested = ratio(alloc_overhead, total_requested);
  double overhead_ratio_actual    = ratio(alloc_overhead, total_actual);
  fprintf(stderr, "            Overhead due to malloc rounding up: %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
          alloc_overhead, alloc_overhead/1024/1024,
          overhead_ratio_requested,
          overhead_ratio_actual,
          (count_Objects));

  if (overhead_per_malloc > 0) {
    size_t total_NMTHeaders = count_Objects * overhead_per_malloc;
    double total_NMTHeaders_ratio_requested = ratio(total_NMTHeaders, total_requested);
    double total_NMTHeaders_ratio_actual    = ratio(total_NMTHeaders, total_actual);
    double total_NMTObjects_ratio_requested = ratio(total_NMTObjects, total_requested);
    double total_NMTObjects_ratio_actual    = ratio(total_NMTObjects, total_actual);

    fprintf(stderr, "                   Overhead due to NMT headers: %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
            total_NMTHeaders, total_NMTHeaders/1024/1024,
            total_NMTHeaders_ratio_requested,
            total_NMTHeaders_ratio_actual,
            count_Objects);
    fprintf(stderr, "                   Overhead due to NMT objects: %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
            total_NMTObjects, total_NMTObjects/1024/1024,
            total_NMTObjects_ratio_requested,
            total_NMTObjects_ratio_actual,
            count_NMTObjects);

    fprintf(stderr, "\n\n");
    long alloc_overhead_no_NMT = (total_actual_no_NMT - total_requested_no_NMT);
    fprintf(stderr, "                    Current requested (no NMT): %12ld bytes, %4ld Mb\n",
            total_requested_no_NMT, total_requested_no_NMT/1024/1024);
    fprintf(stderr, "                       Current actual (no NMT): %12ld bytes, %4ld Mb\n",
            total_actual_no_NMT, total_actual_no_NMT/1024/1024);
    double overhead_ratio_requested_no_NMT = ratio(alloc_overhead_no_NMT, total_requested_no_NMT);
    double overhead_ratio_actual_no_NMT    = ratio(alloc_overhead_no_NMT, total_actual_no_NMT);
    fprintf(stderr, "   Overhead due to malloc rounding up (no NMT): %12ld bytes, %4ld Mb : %.3f%%, %.3f%% [#%zu]\n",
            alloc_overhead_no_NMT, alloc_overhead_no_NMT/1024/1024,
            overhead_ratio_requested_no_NMT,
            overhead_ratio_actual_no_NMT,
            (count_Objects - count_NMTObjects));

    double diff = percent_diff(total_actual_no_NMT, total_actual);
    fprintf(stderr, "\n");
    fprintf(stderr, "NMT overhead (current actual memory allocated): %12.3f%%\n", diff);
  }

#if 0
  fprintf(stderr, "\n\n");
  fprintf(stderr, "overhead_per_malloc:     %12ld\n", overhead_per_malloc);
  fprintf(stderr, "total_requested:         %12ld\n", total_requested);
  fprintf(stderr, "total_requested_no_NMT:  %12ld\n", total_requested_no_NMT);
  fprintf(stderr, "total_actual:            %12ld\n", total_actual);
  fprintf(stderr, "total_actual_no_NMT:     %12ld\n", total_actual_no_NMT);
  fprintf(stderr, "total_NMTObjects:        %12ld\n", total_NMTObjects);
  fprintf(stderr, "count_mallocs:           %12ld\n", count_mallocs);
  fprintf(stderr, "count_reallocs:          %12ld\n", count_reallocs);
  fprintf(stderr, "count_frees:             %12ld\n", count_frees);
  fprintf(stderr, "count_Objects:           %12ld\n", count_Objects);
  fprintf(stderr, "count_NMTObjects:        %12ld\n", count_NMTObjects);
  fprintf(stderr, "#mallocs + #reallocs + #frees: %ld counts\n",
          (count_mallocs + count_reallocs + count_frees));
#endif
}

void NMT_MemoryLogRecorder::dump(Entry* entries, size_t count) {
  fprintf(stderr, "Processing recorded NMT entries ...\n");
  fprintf(stderr, "\n\n");

  calculate_good_sizes(entries, count);

  if (PrintRecordedNMTEntries) {
    print_records(entries, count);
  }

#if 0
  fprintf(stderr, "#########################\n");
  fprintf(stderr, "Processing histograms ...\n\n");
  print_histogram(entries, count);
#endif
  fprintf(stderr, "#####################################\n");
  fprintf(stderr, "Processing memory usage by thread ...\n");
  report_by_thread(entries, count);

  fprintf(stderr, "\n\n");
  fprintf(stderr, "##########################################################\n");
  fprintf(stderr, "Consolidating memory by accouting for free and realloc ...\n");
  fprintf(stderr, "\n");
  consolidate(entries, count);
  fprintf(stderr, "\n");

  fprintf(stderr, "\n\n");
  fprintf(stderr, "#########################\n");
  fprintf(stderr, "Processing histograms ...\n\n");
  print_histogram(entries, count, _histogram_cutoff);
#if 0
  fprintf(stderr, "Processing memory usage by thread ...\n\n");
  report_by_thread(entries, count);
#endif

  fprintf(stderr, "\n\n");
  fprintf(stderr, "#############################\n");
  fprintf(stderr, "Processing memory summary ...\n\n");
  print_summary(entries, count);

  fprintf(stderr, "\nDONE!\n\n");

  assert(good_sizes_totals != nullptr, "good_sizes_totals != nullptr");
  assert(good_sizes_counts != nullptr, "good_sizes_counts != nullptr");
  assert(malloc_buckets != nullptr, "malloc_buckets != nullptr");
  free(good_sizes_totals);
  free(good_sizes_counts);
  free(malloc_buckets);
}

void NMT_MemoryLogRecorder::log(MEMFLAGS flags, size_t requested, address ptr, address old, const NativeCallStack *stack) {
  static pthread_mutex_t _mutex = PTHREAD_MUTEX_INITIALIZER;
  static Entry* _entries = nullptr;
  volatile static size_t count = 0;
  volatile static bool done = (RecordNMTEntries==0);

  Entry* _entry = nullptr;
  if (!done) {
    pthread_mutex_lock(&_mutex);
    {
      if (_entries == nullptr) {
        _entries = (Entry*)calloc(RecordNMTEntries+1, sizeof(Entry));
        assert(_entries != nullptr, "_entries != nullptr");
      }
      if (!done) {
        bool triggered_by_limit = (count >= (size_t)RecordNMTEntries);
        bool triggered_by_request = ((requested == 0) && (ptr == nullptr));
        if (triggered_by_limit) {
          fprintf(stderr, "\n\n");
          fprintf(stderr, "REASON: reached RecordNMTEntries limit: %ld/%ld\n\n", count, RecordNMTEntries);
        } else if (triggered_by_request) {
          fprintf(stderr, "\n\n");
          fprintf(stderr, "REASON: triggered by exit\n\n");
        }
        done = (triggered_by_limit || triggered_by_request);
        // if we reach max or hit "special" marker, then we are done
        if (!done) {
          _entry = &_entries[count++];
        } else {
          dump(_entries, count);
          free((void*)_entries);
          _entries = nullptr;
          exit(0);
        }
      }
    }
    pthread_mutex_unlock(&_mutex);
  }

  if (_entry != nullptr) {
#if defined(LINUX) || defined(__APPLE__)
    _entry->thread = (address)pthread_self();
#elif defined(WINDOWS)
    _entry->thread = nullptr; // ???
#endif
    _entry->ptr = ptr;
    _entry->old = old;
    _entry->requested = requested;
    if (_entry->requested > 0) {
      _entry->requested += MemTracker::overhead_per_malloc();
    }
#if defined(LINUX)
    _entry->actual = malloc_usable_size(ptr);
#elif defined(WINDOWS)
    _entry->actual = _msize(ptr);
#elif defined(__APPLE__)
    _entry->actual = malloc_size(ptr);
#endif
    _entry->flags = flags;
    if (stack != nullptr) {
      for (int i=0; i<NMT_TrackingStackDepth; i++) {
        _entry->stack[i] = stack->get_frame(i);
      }
    }
    if (_entry->requested > 0) {
//#if defined(LINUX)
//     size_t good_size = _malloc_good_size_native(_entry->requested);
//     if (_entry->actual != good_size) {
//       fprintf(stderr, ">>> %zu != %zu:%zu\n", _entry->actual, good_size, _entry->requested);
//     }
//     // assert(_entry->actual == good_size, "%zu != _malloc_good_size_native(%zu):%zu",
//     //        _entry->actual, _entry->requested, good_size);
//#endif
#if defined(WINDOWS)
      //???
#endif
#if defined(__APPLE__)
      size_t good_size = _malloc_good_size_native(_entry->requested);
      assert(_entry->actual == good_size, "%zu != malloc_good_size(%zu):%zu",
             _entry->actual, _entry->requested, good_size);
#endif
    } else {
//#if 0
//      for (size_t i=0; i<_entry->actual; i++)
//      {
//        u_char *b = (u_char*)&_entry->ptr[i];
//        *b = 0x00;
//      }
//#endif
    }
  }
}

#endif // ASSERT
