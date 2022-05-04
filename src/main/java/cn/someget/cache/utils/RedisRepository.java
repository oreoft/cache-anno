package cn.someget.cache.utils;

import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 对redis的一层封装
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@Slf4j
@Repository
public class RedisRepository {

    @Resource
    private LocalCache localCache;

    protected RedisTemplate<String, String> redisTemplate;

    @Resource(name = "redisTemplate")
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        /*
            序列化全部都使用string
            留个小坑, 如果使用注解储存的数据, 使用非注解取出来也必须使用string序列化
         */
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(stringSerializer);
        this.redisTemplate = redisTemplate;
    }


    public Map<String, String> multiGet(List<String> keys) {
        return null;
    }

    public void multiExpire(Map<String, Long> expireMap, TimeUnit seconds) {

    }

    public ReactiveRedisOperations<Object, Object> getRedisTemplate() {
        return null;
    }

    public String get(String key) {
        return null;
    }


    public void batchWriteRedis(Map<String, Object> keyValues, Long expire) {
        if (MapUtil.isEmpty(keyValues)) {
            return;
        }
        try {
            Map<String, Long> expireMap = new HashMap<>(keyValues.size());
            Map<String, String> valueMap = new HashMap<>(keyValues.size());
            keyValues.forEach((k, v) -> {
                expireMap.put(k, expire);
                if (v instanceof String) {
                    valueMap.put(k, (String) v);
                } else {
                    valueMap.put(k, JSON.toJSONString(v));
                }
            });
            getRedisTemplate().opsForValue().multiSet(valueMap);
            log.info("redis multiSet, valueMap:{}", JSON.toJSONString(valueMap));
            multiExpire(expireMap, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("redis setIfAbsent error, keyValues:{}, expire:{}", JSON.toJSONString(keyValues), expire, e);
            Map<String, Object> caches = new HashMap<>(keyValues.size());
            keyValues.forEach(caches::put);
            localCache.putAll(caches);
        }
    }

    public void writeRedis(String key, Long expire, Object value) {
        Map<String, Object> keyValues = new HashMap<>(1);
        keyValues.put(key, value);
        batchWriteRedis(keyValues, expire);
    }
}
