package cn.someget.cache.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.someget.cache.service.CacheService;
import cn.someget.cache.utils.LocalCache;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 本地缓存相应的处理
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@Service("defaultLocalCacheService")
public class LocalCacheServiceImpl implements CacheService {

    @Resource(name = "defaultRedisCacheService")
    private CacheService defaultRedisCacheService;

    @Resource
    private LocalCache localCache;

    @Override
    public <V> V getObjectFromCache(String key, Class<V> clazz) {
        if (CharSequenceUtil.isBlank(key)) {
            return null;
        }
        // 尝试从本地缓存获取
        V value = (V) localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        // 尝试从redis
        V v = defaultRedisCacheService.getObjectFromCache(key, clazz);
        if (v == null) {
            // 如果为空,则直接返回,会走方法去获取值
            return null;
        }

        // 更新本地缓存
        localCache.put(key, v);
        return v;
    }

    @Override
    public <V> List<V> getObjectListFromCache(String key, Class<V> clazz) {
        if (CharSequenceUtil.isBlank(key)) {
            return Collections.emptyList();
        }
        List<V> value = (List<V> ) localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        List<V> v = defaultRedisCacheService.getObjectListFromCache(key, clazz);
        if (CollectionUtils.isEmpty(v)) {
            return Collections.emptyList();
        }
        localCache.put(key, v);
        return v;
    }

    @Override
    public <K, V> Map<K, V> getObjectFromCache(List<K> ids, Class<V> clazz, String prefix) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        ids.removeIf(Objects::isNull);
        List<String> keys = ids.stream().map(p -> String.format(prefix, p)).collect(Collectors.toList());
        Map<String, V> allPresent = (Map<String, V>) localCache.getAllPresent(keys);
        // 从本地缓存里面把结果都筛选出来, 如果有未命中的整理出来
        Map<K, V> results = new HashMap<>(ids.size());
        List<K> missIds = new ArrayList<>();
        for (K id : ids) {
            String key = String.format(prefix, id);
            V v = allPresent.get(key);
            if (v != null) {
                results.put(id, v);
            } else {
                missIds.add(id);
            }
        }
        // 没有未命中的 直接返回
        if (CollectionUtils.isEmpty(missIds)) {
            return results;
        }
        // 未命中的尝试去redis里面获取一下
        Map<K, V> objectFromCache = defaultRedisCacheService.getObjectFromCache(missIds, clazz, prefix);
        Map<String, Object> needCacheObjects = new HashMap<>(missIds.size());
        for (K k : missIds) {
            V v = objectFromCache.get(k);
            if (v == null) {
                continue;
            }
            needCacheObjects.put(String.format(prefix, k), v);
            results.put(k, v);
        }
        // 更新一下本地缓存
        if (MapUtil.isNotEmpty(needCacheObjects)) {
            localCache.putAll(needCacheObjects);
        }
        return results;
    }

    @Override
    public <K, V> Map<K, List<V>> getObjectListFromCache(List<K> ids, Class<V> clazz, String prefix) {
        // 这个方法和上面是一样的, 只不过获取的返回值都是List<V>
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        ids.removeIf(Objects::isNull);
        List<String> keys = ids.stream().map(p -> String.format(prefix, p)).collect(Collectors.toList());
        Map<String, Object> allPresent = localCache.getAllPresent(keys);
        Map<K, List<V>> results = new HashMap<>(ids.size());
        List<K> missIds = new ArrayList<>();
        for (K id : ids) {
            String key = String.format(prefix, id);
            List<V> v = (List<V>) allPresent.get(key);
            if (v != null) {
                results.put(id, v);
            } else {
                missIds.add(id);
            }
        }

        if (CollectionUtils.isEmpty(missIds)) {
            return results;
        }

        Map<K, List<V>> objectListFromCache = defaultRedisCacheService.getObjectListFromCache(missIds, clazz, prefix);
        Map<String, Object> needCacheObjects = new HashMap<>(missIds.size());
        for (K k : missIds) {
            List<V> v = objectListFromCache.get(k);
            if (v == null) {
                continue;
            }
            needCacheObjects.put(String.format(prefix, k), v);
            results.put(k, v);
        }
        if (MapUtil.isNotEmpty(needCacheObjects)) {
            localCache.putAll(needCacheObjects);
        }
        return results;
    }

}
