/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include <math.h>
#include <errno.h>
#include "cgroupV1Subsystem_linux.hpp"
#include "cgroupUtil_linux.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "os_linux.hpp"

static inline
void do_trace_log(julong read_mem_limit, julong host_mem) {
  if (log_is_enabled(Debug, os, container)) {
    jlong mem_limit = (jlong)read_mem_limit; // account for negative values
    if (mem_limit < 0 || read_mem_limit >= host_mem) {
      const char *reason;
      if (mem_limit == OSCONTAINER_ERROR) {
        reason = "failed";
      } else if (mem_limit == -1) {
        reason = "unlimited";
      } else {
        assert(read_mem_limit >= host_mem, "Expected read value exceeding host_mem");
        // Exceeding physical memory is treated as unlimited. This implementation
        // caps it at host_mem since Cg v1 has no value to represent 'max'.
        reason = "ignored";
      }
      log_debug(os, container)("container memory limit %s: " JLONG_FORMAT ", using host value " JLONG_FORMAT,
                               reason, mem_limit, host_mem);
    }
  }
}


jlong CgroupV1MemoryController::read_memory_limit_in_bytes(julong phys_mem) {
  julong memlimit;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.limit_in_bytes", JULONG_FORMAT, &memlimit);
  if (err != 0) {
    log_trace(os, container)("Memory Limit is: %d", OSCONTAINER_ERROR);
    do_trace_log(OSCONTAINER_ERROR, phys_mem);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Memory Limit is: " JULONG_FORMAT, memlimit);

  if (memlimit < phys_mem) {
    do_trace_log(memlimit, phys_mem);
    return (jlong)memlimit;
  }
  log_trace(os, container)("Non-Hierarchical Memory Limit is: Unlimited");
  if (is_hierarchical()) {
    julong hier_memlimit;
    err = cg_file_multi_line_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.stat",
                                  "hierarchical_memory_limit", JULONG_FORMAT, &hier_memlimit);
    if (err != 0) {
      do_trace_log(OSCONTAINER_ERROR, phys_mem);
      return OSCONTAINER_ERROR;
    }
    log_trace(os, container)("Hierarchical Memory Limit is: " JULONG_FORMAT, hier_memlimit);
    if (hier_memlimit < phys_mem) {
      do_trace_log(hier_memlimit, phys_mem);
      return (jlong)hier_memlimit;
    }
    log_trace(os, container)("Hierarchical Memory Limit is: Unlimited");
  }
  do_trace_log(memlimit, phys_mem);
  return (jlong)-1;
}

/* read_mem_swap
 *
 * Determine the memory and swap limit metric. Returns a positive limit value strictly
 * lower than the physical memory and swap limit iff there is a limit. Otherwise a
 * negative value is returned indicating the determined status.
 *
 * returns:
 *    * A number > 0 if the limit is available and lower than a physical upper bound.
 *    * OSCONTAINER_ERROR if the limit cannot be retrieved (i.e. not supported) or
 *    * -1 if there isn't any limit in place (note: includes values which exceed a physical
 *      upper bound)
 */
jlong CgroupV1MemoryController::read_mem_swap(julong host_total_memsw) {
  julong hier_memswlimit;
  julong memswlimit;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.memsw.limit_in_bytes", JULONG_FORMAT, &memswlimit);
  if (err != 0) {
    log_trace(os, container)("Memory and Swap Limit is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Memory and Swap Limit is: " JULONG_FORMAT, memswlimit);
  if (memswlimit >= host_total_memsw) {
    log_trace(os, container)("Non-Hierarchical Memory and Swap Limit is: Unlimited");
    if (is_hierarchical()) {
      const char* matchline = "hierarchical_memsw_limit";
      err = cg_file_multi_line_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.stat", matchline, JULONG_FORMAT, &hier_memswlimit);
      if (err != 0) {
        return OSCONTAINER_ERROR;
      }
      log_trace(os, container)("Hierarchical Memory and Swap Limit is : " JULONG_FORMAT, hier_memswlimit);
      if (hier_memswlimit >= host_total_memsw) {
        log_trace(os, container)("Hierarchical Memory and Swap Limit is: Unlimited");
      } else {
        return (jlong)hier_memswlimit;
      }
    }
    return (jlong)-1;
  } else {
    // Backward compatibility:
    log_trace(os, container)("Hierarchical Memory and Swap Limit is : " JULONG_FORMAT, memswlimit);
    return (jlong)memswlimit;
  }
}

jlong CgroupV1MemoryController::memory_and_swap_limit_in_bytes(julong host_mem, julong host_swap) {
  jlong memory_swap = read_mem_swap(host_mem + host_swap);
  if (memory_swap == -1) {
    return memory_swap;
  }
  // If there is a swap limit, but swappiness == 0, reset the limit
  // to the memory limit. Do the same for cases where swap isn't
  // supported.
  jlong swappiness = read_mem_swappiness();
  if (swappiness == 0 || memory_swap == OSCONTAINER_ERROR) {
    jlong memlimit = read_memory_limit_in_bytes(host_mem);
    if (memory_swap == OSCONTAINER_ERROR) {
      log_trace(os, container)("Memory and Swap Limit has been reset to " JLONG_FORMAT " because swap is not supported", memlimit);
    } else {
      log_trace(os, container)("Memory and Swap Limit has been reset to " JLONG_FORMAT " because swappiness is 0", memlimit);
    }
    return memlimit;
  }
  return memory_swap;
}

static inline
jlong memory_swap_usage_impl(CgroupController* ctrl) {
  julong memory_swap_usage;
  int err = cg_file_contents_ctrl(ctrl, "/memory.memsw.usage_in_bytes", JULONG_FORMAT, &memory_swap_usage);
  if (err != 0) {
    log_trace(os, container)("mem swap usage is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("mem swap usage is: " JULONG_FORMAT, memory_swap_usage);
  return (jlong)memory_swap_usage;
}

jlong CgroupV1MemoryController::memory_and_swap_usage_in_bytes(julong phys_mem, julong host_swap) {
  jlong memory_sw_limit = memory_and_swap_limit_in_bytes(phys_mem, host_swap);
  jlong memory_limit = read_memory_limit_in_bytes(phys_mem);
  if (memory_sw_limit > 0 && memory_limit > 0) {
    jlong delta_swap = memory_sw_limit - memory_limit;
    if (delta_swap > 0) {
      return memory_swap_usage_impl(static_cast<CgroupV1Controller*>(this));
    }
  }
  return memory_usage_in_bytes();
}

jlong CgroupV1MemoryController::read_mem_swappiness() {
  julong swappiness;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.swappiness",
                                  JULONG_FORMAT, &swappiness);
  if (err != 0) {
    log_trace(os, container)("Swappiness is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Swappiness is: " JULONG_FORMAT, swappiness);
  return (jlong)swappiness;
}

jlong CgroupV1MemoryController::memory_soft_limit_in_bytes(julong upper_bound) {
  julong memsoftlimit;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.soft_limit_in_bytes",
                                  JULONG_FORMAT, &memsoftlimit);
  if (err != 0) {
    log_trace(os, container)("Memory Soft Limit is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Memory Soft Limit is: " JULONG_FORMAT, memsoftlimit);
  if (memsoftlimit >= upper_bound) {
    log_trace(os, container)("Memory Soft Limit is: Unlimited");
    return (jlong)-1;
  } else {
    return (jlong)memsoftlimit;
  }
}

/* memory_usage_in_bytes
 *
 * Return the amount of used memory for this process.
 *
 * return:
 *    memory usage in bytes or
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1MemoryController::memory_usage_in_bytes() {
  jlong memusage;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.usage_in_bytes",
                                  JLONG_FORMAT, &memusage);
  if (err != 0) {
    log_trace(os, container)("Memory Usage is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Memory Usage is: " JLONG_FORMAT, memusage);
  return memusage;
}

/* memory_max_usage_in_bytes
 *
 * Return the maximum amount of used memory for this process.
 *
 * return:
 *    max memory usage in bytes or
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1MemoryController::memory_max_usage_in_bytes() {
  jlong memmaxusage;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.max_usage_in_bytes",
                                  JLONG_FORMAT, &memmaxusage);
  if (err != 0) {
    log_trace(os, container)("Maximum Memory Usage is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Maximum Memory Usage is: " JLONG_FORMAT, memmaxusage);
  return memmaxusage;
}

jlong CgroupV1MemoryController::rss_usage_in_bytes() {
  julong rss;
  int err = cg_file_multi_line_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.stat", "rss", JULONG_FORMAT, &rss);
  if (err != 0) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("RSS usage is: " JULONG_FORMAT, rss);
  return rss;
}

jlong CgroupV1MemoryController::cache_usage_in_bytes() {
  julong cache;
  int err = cg_file_multi_line_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.stat", "cache", JULONG_FORMAT, &cache);
  if (err != 0) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Cache usage is: " JULONG_FORMAT, cache);
  return cache;
}

jlong CgroupV1MemoryController::kernel_memory_usage_in_bytes() {
  jlong kmem_usage;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.kmem.usage_in_bytes",
                                  JLONG_FORMAT, &kmem_usage);
  if (err != 0) {
    log_trace(os, container)("Kernel Memory Usage is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Kernel Memory Usage is: " JLONG_FORMAT, kmem_usage);
  return kmem_usage;
}

jlong CgroupV1MemoryController::kernel_memory_limit_in_bytes(julong phys_mem) {
  julong kmem_limit;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.kmem.limit_in_bytes",
                                  JULONG_FORMAT, &kmem_limit);
  if (err != 0) {
    log_trace(os, container)("Kernel Memory Limit is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Kernel Memory Limit is: " JULONG_FORMAT, kmem_limit);
  if (kmem_limit >= phys_mem) {
    return (jlong)-1;
  }
  return (jlong)kmem_limit;
}

jlong CgroupV1MemoryController::kernel_memory_max_usage_in_bytes() {
  jlong kmem_max_usage;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/memory.kmem.max_usage_in_bytes",
                                  JLONG_FORMAT, &kmem_max_usage);
  if (err != 0) {
    log_trace(os, container)("Maximum Kernel Memory Usage is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Maximum Kernel Memory Usage is: " JLONG_FORMAT, kmem_max_usage);
  return kmem_max_usage;
}

void CgroupV1Subsystem::print_version_specific_info(outputStream* st) {
  julong phys_mem = os::Linux::physical_memory();
  CgroupV1MemoryController* ctrl = reinterpret_cast<CgroupV1MemoryController*>(memory_controller()->controller());
  jlong kmem_usage = ctrl->kernel_memory_usage_in_bytes();
  jlong kmem_limit = ctrl->kernel_memory_limit_in_bytes(phys_mem);
  jlong kmem_max_usage = ctrl->kernel_memory_max_usage_in_bytes();

  OSContainer::print_container_helper(st, kmem_usage, "kernel_memory_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_limit, "kernel_memory_max_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_max_usage, "kernel_memory_limit_in_bytes");
}

char * CgroupV1Subsystem::cpu_cpuset_cpus() {
  char cpus[1024];
  int err = cg_file_contents_ctrl(_cpuset, "/cpuset.cpus", "%1023s", cpus);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("cpuset.cpus is: %s", cpus);
  return os::strdup(cpus);
}

char * CgroupV1Subsystem::cpu_cpuset_memory_nodes() {
  char mems[1024];
  int err = cg_file_contents_ctrl(_cpuset, "/cpuset.mems", "%1023s", mems);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("cpuset.mems is: %s", mems);
  return os::strdup(mems);
}

/* cpu_quota
 *
 * Return the number of microseconds per period
 * process is guaranteed to run.
 *
 * return:
 *    quota time in microseconds
 *    -1 for no quota
 *    OSCONTAINER_ERROR for not supported
 */
int CgroupV1CpuController::cpu_quota() {
  int quota;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/cpu.cfs_quota_us", "%d", &quota);
  if (err != 0) {
    log_trace(os, container)("CPU Quota is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("CPU Quota is: %d", quota);
  return quota;
}

int CgroupV1CpuController::cpu_period() {
  int period;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/cpu.cfs_period_us", "%d", &period);
  if (err != 0) {
    log_trace(os, container)("CPU Period is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("CPU Period is: %d", period);
  return period;
}

/* cpu_shares
 *
 * Return the amount of cpu shares available to the process
 *
 * return:
 *    Share number (typically a number relative to 1024)
 *                 (2048 typically expresses 2 CPUs worth of processing)
 *    -1 for no share setup
 *    OSCONTAINER_ERROR for not supported
 */
int CgroupV1CpuController::cpu_shares() {
  int shares;
  int err = cg_file_contents_ctrl(static_cast<CgroupV1Controller*>(this), "/cpu.shares", "%d", &shares);
  if (err != 0) {
    log_trace(os, container)("CPU Shares is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("CPU Shares is: %d", shares);
  // Convert 1024 to no shares setup
  if (shares == 1024) return -1;

  return shares;
}


char* CgroupV1Subsystem::pids_max_val() {
  char pidsmax[1024];
  int err = cg_file_contents_ctrl(_pids, "/pids.max", "%1023s", pidsmax);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("Maximum number of tasks is: %s", pidsmax);
  return os::strdup(pidsmax);
}

/* pids_max
 *
 * Return the maximum number of tasks available to the process
 *
 * return:
 *    maximum number of tasks
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1Subsystem::pids_max() {
  if (_pids == nullptr) return OSCONTAINER_ERROR;
  char * pidsmax_str = pids_max_val();
  return CgroupUtil::limit_from_str(pidsmax_str);
}

/* pids_current
 *
 * The number of tasks currently in the cgroup (and its descendants) of the process
 *
 * return:
 *    current number of tasks
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1Subsystem::pids_current() {
  if (_pids == nullptr) return OSCONTAINER_ERROR;
  jlong pids_current;
  int err = cg_file_contents_ctrl(_pids, "/pids.current", JLONG_FORMAT, &pids_current);
  if (err != 0) {
    log_trace(os, container)("Current number of tasks is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Current number of tasks is: " JLONG_FORMAT, pids_current);
  return pids_current;
}
