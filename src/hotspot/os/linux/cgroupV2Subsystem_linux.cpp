/*
 * Copyright (c) 2020, 2022, Red Hat Inc.
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

#include "cgroupV2Subsystem_linux.hpp"
#include "cgroupUtil_linux.hpp"

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
int CgroupV2CpuController::cpu_shares() {
  int shares;
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(this), "/cpu.weight", "%d", &shares);
  if (err != 0) {
    log_trace(os, container)("Raw value for CPU Shares is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Raw value for CPU Shares is: %d", shares);
  // Convert default value of 100 to no shares setup
  if (shares == 100) {
    log_debug(os, container)("CPU Shares is: %d", -1);
    return -1;
  }

  // CPU shares (OCI) value needs to get translated into
  // a proper Cgroups v2 value. See:
  // https://github.com/containers/crun/blob/master/crun.1.md#cpu-controller
  //
  // Use the inverse of (x == OCI value, y == cgroupsv2 value):
  // ((262142 * y - 1)/9999) + 2 = x
  //
  int x = 262142 * shares - 1;
  double frac = x/9999.0;
  x = ((int)frac) + 2;
  log_trace(os, container)("Scaled CPU shares value is: %d", x);
  // Since the scaled value is not precise, return the closest
  // multiple of PER_CPU_SHARES for a more conservative mapping
  if ( x <= PER_CPU_SHARES ) {
     // will always map to 1 CPU
     log_debug(os, container)("CPU Shares is: %d", x);
     return x;
  }
  int f = x/PER_CPU_SHARES;
  int lower_multiple = f * PER_CPU_SHARES;
  int upper_multiple = (f + 1) * PER_CPU_SHARES;
  int distance_lower = MAX2(lower_multiple, x) - MIN2(lower_multiple, x);
  int distance_upper = MAX2(upper_multiple, x) - MIN2(upper_multiple, x);
  x = distance_lower <= distance_upper ? lower_multiple : upper_multiple;
  log_trace(os, container)("Closest multiple of %d of the CPU Shares value is: %d", PER_CPU_SHARES, x);
  log_debug(os, container)("CPU Shares is: %d", x);
  return x;
}

static
char* cpu_quota_val(CgroupV2Controller* ctrl) {
  char quota[1024];
  int err = cg_file_contents_ctrl(ctrl, "/cpu.max", "%1023s %*d", quota);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("Raw value for CPU quota is: %s", quota);
  return os::strdup(quota);
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
int CgroupV2CpuController::cpu_quota() {
  char * cpu_quota_str = cpu_quota_val(static_cast<CgroupV2Controller*>(this));
  int limit = (int)CgroupUtil::limit_from_str(cpu_quota_str);
  log_trace(os, container)("CPU Quota is: %d", limit);
  return limit;
}

char * CgroupV2Subsystem::cpu_cpuset_cpus() {
  char cpus[1024];
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(_unified), "/cpuset.cpus", "%1023s", cpus);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("cpuset.cpus is: %s", cpus);
  return os::strdup(cpus);
}

char * CgroupV2Subsystem::cpu_cpuset_memory_nodes() {
  char mems[1024];
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(_unified), "/cpuset.mems", "%1023s", mems);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("cpuset.mems is: %s", mems);
  return os::strdup(mems);
}

int CgroupV2CpuController::cpu_period() {
  int period;
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(this), "/cpu.max", "%*s %d", &period);
  if (err != 0) {
    log_trace(os, container)("CPU Period is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("CPU Period is: %d", period);
  return period;
}

/* memory_usage_in_bytes
 *
 * Return the amount of used memory used by this cgroup and descendents
 *
 * return:
 *    memory usage in bytes or
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV2MemoryController::memory_usage_in_bytes() {
  jlong memusage;
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(this), "/memory.current", JLONG_FORMAT, &memusage);
  if (err != 0) {
    log_trace(os, container)("Memory Usage is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Memory Usage is: " JLONG_FORMAT, memusage);
  return memusage;
}

static
char* mem_soft_limit_val(CgroupController* ctrl) {
  char mem_soft_limit_str[1024];
  int err = cg_file_contents_ctrl(ctrl, "/memory.low", "%1023s", mem_soft_limit_str);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("Memory Soft Limit is: %s", mem_soft_limit_str);
  return os::strdup(mem_soft_limit_str);
}

jlong CgroupV2MemoryController::memory_soft_limit_in_bytes(julong phys_mem) {
  char* mem_soft_limit_str = mem_soft_limit_val(static_cast<CgroupV2Controller*>(this));
  return CgroupUtil::limit_from_str(mem_soft_limit_str);
}

jlong CgroupV2MemoryController::memory_max_usage_in_bytes() {
  // Log this string at trace level so as to make tests happy.
  log_trace(os, container)("Maximum Memory Usage is not supported.");
  return OSCONTAINER_ERROR; // not supported
}

jlong CgroupV2MemoryController::rss_usage_in_bytes() {
  julong rss;
  int err = cg_file_multi_line_ctrl(static_cast<CgroupV2Controller*>(this), "/memory.stat",
                                    "anon", JULONG_FORMAT, &rss);
  if (err != 0) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("RSS usage is: " JULONG_FORMAT, rss);
  return (jlong)rss;
}

jlong CgroupV2MemoryController::cache_usage_in_bytes() {
  julong cache;
  int err = cg_file_multi_line_ctrl(static_cast<CgroupV2Controller*>(this), "/memory.stat",
                                    "file", JULONG_FORMAT, &cache);
  if (err != 0) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Cache usage is: " JULONG_FORMAT, cache);
  return (jlong)cache;
}

static
char* mem_swp_limit_val(CgroupController* ctrl) {
  char mem_swp_limit_str[1024];
  int err = cg_file_contents_ctrl(ctrl, "/memory.swap.max", "%1023s", mem_swp_limit_str);
  if (err != 0) {
    return nullptr;
  }
  // FIXME: This log-line is misleading, since it reads the swap limit only, not memory *and*
  // swap limit.
  log_trace(os, container)("Memory and Swap Limit is: %s", mem_swp_limit_str);
  return os::strdup(mem_swp_limit_str);
}

// Note that for cgroups v2 the actual limits set for swap and
// memory live in two different files, memory.swap.max and memory.max
// respectively. In order to properly report a cgroup v1 like
// compound value we need to sum the two values. Setting a swap limit
// without also setting a memory limit is not allowed.
jlong CgroupV2MemoryController::memory_and_swap_limit_in_bytes(julong phys_mem, julong host_swap) {
  char* mem_swp_limit_str = mem_swp_limit_val(static_cast<CgroupV2Controller*>(this));
  if (mem_swp_limit_str == nullptr) {
    // Some container tests rely on this trace logging to happen.
    log_trace(os, container)("Memory and Swap Limit is: %d", OSCONTAINER_ERROR);
    // swap disabled at kernel level, treat it as no swap
    return read_memory_limit_in_bytes(phys_mem);
  }
  jlong swap_limit = CgroupUtil::limit_from_str(mem_swp_limit_str);
  if (swap_limit >= 0) {
    jlong memory_limit = read_memory_limit_in_bytes(phys_mem);
    assert(memory_limit >= 0, "swap limit without memory limit?");
    return memory_limit + swap_limit;
  }
  log_trace(os, container)("Memory and Swap Limit is: " JLONG_FORMAT, swap_limit);
  return swap_limit;
}

// memory.swap.current : total amount of swap currently used by the cgroup and its descendants
static
char* mem_swp_current_val(CgroupV2Controller* ctrl) {
  char mem_swp_current_str[1024];
  int err = cg_file_contents_ctrl(ctrl, "/memory.swap.current", "%1023s", mem_swp_current_str);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("Swap currently used is: %s", mem_swp_current_str);
  return os::strdup(mem_swp_current_str);
}


jlong CgroupV2MemoryController::memory_and_swap_usage_in_bytes(julong host_mem, julong host_swap) {
  jlong memory_usage = memory_usage_in_bytes();
  if (memory_usage >= 0) {
      char* mem_swp_current_str = mem_swp_current_val(static_cast<CgroupV2Controller*>(this));
      jlong swap_current = CgroupUtil::limit_from_str(mem_swp_current_str);
      return memory_usage + (swap_current >= 0 ? swap_current : 0);
  }
  return memory_usage; // not supported or unlimited case
}

static
char* mem_limit_val(CgroupV2Controller* ctrl) {
  char mem_limit_str[1024];
  int err = cg_file_contents_ctrl(ctrl, "/memory.max", "%1023s", mem_limit_str);
  if (err != 0) {
    return nullptr;
  }
  log_trace(os, container)("Raw value for memory limit is: %s", mem_limit_str);
  return os::strdup(mem_limit_str);
}

/* read_memory_limit_in_bytes
 *
 * Return the limit of available memory for this process.
 *
 * return:
 *    memory limit in bytes or
 *    -1 for unlimited, OSCONTAINER_ERROR for an error
 */
jlong CgroupV2MemoryController::read_memory_limit_in_bytes(julong phys_mem) {
  char * mem_limit_str = mem_limit_val(static_cast<CgroupV2Controller*>(this));
  jlong limit = CgroupUtil::limit_from_str(mem_limit_str);
  if (log_is_enabled(Trace, os, container)) {
    if (limit == -1) {
      log_trace(os, container)("Memory Limit is: Unlimited");
    } else {
      log_trace(os, container)("Memory Limit is: " JLONG_FORMAT, limit);
    }
  }
  if (log_is_enabled(Debug, os, container)) {
    julong read_limit = (julong)limit; // avoid signed/unsigned compare
    if (limit < 0 || read_limit >= phys_mem) {
      const char* reason;
      if (limit == -1) {
        reason = "unlimited";
      } else if (limit == OSCONTAINER_ERROR) {
        reason = "failed";
      } else {
        assert(read_limit >= phys_mem, "Expected mem limit to exceed host memory");
        reason = "ignored";
      }
      log_debug(os, container)("container memory limit %s: " JLONG_FORMAT ", using host value " JLONG_FORMAT,
                               reason, limit, phys_mem);
    }
  }
  return limit;
}


void CgroupV2Subsystem::print_version_specific_info(outputStream* st) {
  char* mem_swp_current_str = mem_swp_current_val(static_cast<CgroupV2Controller*>(_unified));
  jlong swap_current = CgroupUtil::limit_from_str(mem_swp_current_str);

  char* mem_swp_limit_str = mem_swp_limit_val(static_cast<CgroupV2Controller*>(_unified));
  jlong swap_limit = CgroupUtil::limit_from_str(mem_swp_limit_str);

  OSContainer::print_container_helper(st, swap_current, "memory_swap_current_in_bytes");
  OSContainer::print_container_helper(st, swap_limit, "memory_swap_max_limit_in_bytes");
}

char* CgroupV2Subsystem::pids_max_val() {
  char pidsmax[1024];
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(_unified), "/pids.max", "%1023s", pidsmax);
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
jlong CgroupV2Subsystem::pids_max() {
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
jlong CgroupV2Subsystem::pids_current() {
  jlong pids_current;
  int err = cg_file_contents_ctrl(static_cast<CgroupV2Controller*>(_unified), "/pids.current", JLONG_FORMAT, &pids_current);
  if (err != 0) {
    log_trace(os, container)("Current number of tasks is: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Current number of tasks is: " JLONG_FORMAT, pids_current);
  return pids_current;
}
