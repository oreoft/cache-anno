package cn.someget.cache.utils;


import cn.hutool.core.map.MapUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存的封装
 *
 * @author zyf
 * @date 2022-05-03 12:12
 */
@Slf4j
@Component
public class LocalCache {

    /**
     * 本地缓存容器
     */
    private Cache<String, Object> cache;


    @PostConstruct
    public void buildCache() {
        cache = Caffeine.newBuilder().recordStats()
                .removalListener(((key, value, cause) -> log.info("key:{}, was removed, cause:{}", key, cause)))
                .expireAfterWrite(3, TimeUnit.SECONDS).build();
    }

    /**
     * 查询缓存
     *
     * @param key key
     */
    public Object getIfPresent(String key) {
        return cache.getIfPresent(key);
    }

    /**
     * 查询多个缓存
     *
     * @param keys keys
     */
    public Map<String, Object> getAllPresent(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return new HashMap<>(0);
        }
        return this.cache.getAllPresent(keys);
    }

    /**
     * 删除缓存
     *
     * @param key key
     */
    public void delete(String key) {
        this.cache.invalidate(key);
    }

    /**
     * 写缓存
     *
     * @param key    key
     * @param object 值
     */
    public void put(String key, Object object) {
        this.cache.put(key, object);
    }

    /**
     * 写缓存
     *
     * @param caches 缓存map
     */
    public void putAll(Map<String, Object> caches) {
        if (MapUtil.isEmpty(caches)) {
            return;
        }
        this.cache.putAll(caches);
    }


    @Scheduled(fixedDelay = 60000)
    public void stats() {
        CacheStats stats = cache.stats();
        log.info("local cache stats, missCount:{}, missRate:{}, hitCount:{}, hitRate:{}",
                stats.missCount(), stats.missRate(), stats.hitCount(), stats.hitRate());
    }
}
