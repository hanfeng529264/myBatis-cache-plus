package com.hf.mbcp.monitor;

import com.hf.mbcp.cache.local.CaffeineProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * JVM 堆内存压力监控，自动收缩 L1 缓存容量。
 * <p>
 * 每 30s 采样一次堆使用率：
 * <ul>
 *   <li>&ge; 80%：L1 容量缩减至当前的 50%</li>
 *   <li>&ge; 70%：L1 容量缩减至当前的 70%</li>
 *   <li>&lt; 60%：恢复至配置的 maxMemoryMb</li>
 * </ul>
 */
public class MemoryPressureWatcher {

    private static final Logger log = LoggerFactory.getLogger(MemoryPressureWatcher.class);

    private static final double THRESHOLD_CRITICAL = 0.80;
    private static final double THRESHOLD_HIGH     = 0.70;
    private static final double THRESHOLD_NORMAL   = 0.60;

    private final CaffeineProvider l1Provider;
    private final long maxMemoryBytes;

    private final MemoryMXBean memMxBean = ManagementFactory.getMemoryMXBean();

    public MemoryPressureWatcher(CaffeineProvider l1Provider, long maxMemoryMb) {
        this.l1Provider  = l1Provider;
        this.maxMemoryBytes = maxMemoryMb * 1024 * 1024;
    }

    @Scheduled(fixedDelay = 30_000)
    public void checkMemory() {
        var heap = memMxBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max  = heap.getMax();
        if (max <= 0) return;

        double ratio = (double) used / max;

        if (ratio >= THRESHOLD_CRITICAL) {
            long newMax = (long) (maxMemoryBytes * 0.50);
            log.warn("[MBCP] heap usage {:.1f}%, shrink L1 to 50% ({} MB)", ratio * 100, newMax / 1024 / 1024);
            l1Provider.resizeMaxWeight(newMax);
        } else if (ratio >= THRESHOLD_HIGH) {
            long newMax = (long) (maxMemoryBytes * 0.70);
            log.info("[MBCP] heap usage {:.1f}%, shrink L1 to 70% ({} MB)", ratio * 100, newMax / 1024 / 1024);
            l1Provider.resizeMaxWeight(newMax);
        } else if (ratio < THRESHOLD_NORMAL) {
            // 内存充裕，恢复原始配置
            l1Provider.resizeMaxWeight(maxMemoryBytes);
        }
    }
}
