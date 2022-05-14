package cn.someget.cache.utils;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对redis的一层封装
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@Slf4j
@Repository
public class RedisRepository {

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


    /**
     * 单个key写入redis
     * @param key key
     * @param expire 过期时间
     * @param value value
     */
    public void set(String key, Long expire, Object value) {
        if (value == null) {
            return;
        }
        // 把要写入的值变成str, 如果是string就不动, 如果是list则序列化成json
        String valueStr = value instanceof String ? value.toString() : JSON.toJSONString(value);
        try {
            redisTemplate.opsForValue().set(key, valueStr, expire, TimeUnit.SECONDS);
            log.info("redis set, value:{}", valueStr);
        } catch (Exception e) {
            log.warn("cache-anno redis set error, keyValues:{}, expire:{}, msg:{}",
                    JSON.toJSONString(valueStr), expire, e.getMessage());
        }
    }

    /**
     * 批量写入
     * 批量是管道一次性写入, 写入是ex所以ttl可以保证原子性
     * 但是批量数据不保证能一起成功
     *
     * @param keyValues 写入的kv
     * @param expire    要设置的过期时间
     */
    public void batchSet(Map<String, Object> keyValues, Long expire) {
        if (MapUtil.isEmpty(keyValues)) {
            return;
        }
        try {
            // 管道批量设置
            redisTemplate.executePipelined((RedisCallback<?>) connection -> {
                RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                keyValues.forEach((k, v) -> {
                    // 如果为空跳过
                    if (CharSequenceUtil.isBlank(k) || v == null) {
                        return;
                    }
                    byte[] key = stringSerializer.serialize(k);
                    byte[] value = stringSerializer.serialize(v instanceof String ? v.toString() :JSON.toJSONString(v));
                    connection.setEx(key, expire, value);
                });
                return null;
            });
            log.info("redis batchSet, valueMap:{}", JSON.toJSONString(keyValues));
        } catch (Exception e) {
            log.warn("cache-anno redis batchSet error, keyValues:{}, expire:{}, msg:{}",
                    JSON.toJSONString(keyValues), expire, e.getMessage());
        }
    }

    /**
     * 获取key的数据, 会自动处理异常
     *
     * @param key key
     * @return 结果
     */
    public String get(String key) {
        try {
            String result = redisTemplate.opsForValue().get(key);
            log.info("redis get, keys:{}, result:{}", key, result);
            return result;
        } catch (Exception e) {
            log.error("cache-anno redis get Error, key:{}", key, e);
        }
        return null;
    }


    /**
     * 批量获取key
     *
     * @param keys key集合
     * @return key-value结果
     */
    public Map<String, String> multiGet(List<String> keys) {
        keys = keys.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, String> result = new HashMap<>(keys.size());
        try {
            // 批量获取key
            List<String> redisData = redisTemplate.opsForValue().multiGet(keys);
            if (CollectionUtils.isEmpty(redisData)) {
                return result;
            }
            // 按照位置进行拼装
            for (int i = 0; i < keys.size(); i++) {
                String value = redisData.get(i);
                String key = keys.get(i);
                // 如果这个key是空的, 则跳过
                if (CharSequenceUtil.isBlank(key)) {
                    continue;
                }
                result.put(key, value);
            }
            log.info("redis multiGet, keys:{}, result:{}", JSON.toJSONString(keys), JSON.toJSONString(result));
        } catch (Exception e) {
            log.error("cache-anno redis multiGet Error, keys:{}, msg:{}", JSON.toJSONString(keys), e.getMessage());
        }
        return result;
    }
}
