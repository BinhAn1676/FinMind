package com.finance.chatservice.common.cache.redisson;



public interface RedisDistributedService {
    RedisDistributedLocker getDistributedLock(String lockKey);
}
