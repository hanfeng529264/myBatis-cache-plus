package com.hf.mbcp.cache.multi;

import com.hf.mbcp.api.InvalidationMessage;

import java.util.List;
import java.util.function.Consumer;

/** 无 Redis 时的 No-Op 广播（单节点部署，无需节点间通知）。 */
public class NoOpBroadcaster implements InvalidationBroadcaster {

    @Override
    public void broadcast(List<String> cacheKeys, List<String> tableNames) {
        // 单节点：无需广播，L1 由调用方直接清除
    }

    @Override
    public void subscribe(Consumer<InvalidationMessage> handler) {
        // 无订阅
    }
}
