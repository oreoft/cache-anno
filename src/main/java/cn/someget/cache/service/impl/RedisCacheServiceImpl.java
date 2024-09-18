package cn.someget.cache.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.someget.cache.service.CacheService;
import cn.someget.cache.utils.RedisRepository;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * redis相应的自动缓存处理
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@Service("defaultRedisCacheService")
public class RedisCacheServiceImpl implements CacheService {

    @Resource
    private RedisRepository redisRepository;

    @Override
    public <V> V getObjectFromCache(String key, Class<V> clazz) {
        if (CharSequenceUtil.isBlank(key)) {
            return null;
        }
        // 从redis里面查出来
        String result = redisRepository.get(key);
        return result == null ? null : JSON.parseObject(result, clazz);
    }

    @Override
    public <V> List<V> getObjectListFromCache(String key, Class<V> clazz) {
        if (CharSequenceUtil.isBlank(key)) {
            return new ArrayList<>();
        }
        // 从redis里面查出来
        String result = redisRepository.get(key);
        return result == null ? Collections.emptyList() : JSON.parseArray(result, clazz);
    }

    @Override
    public <K, V> Map<K, V> getObjectFromCache(List<K> ids, Class<V> clazz, String prefix) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }

        // 去构建key, 此时map的value是key
        Map<K, String> cacheData = getRedisData(ids, prefix);

        // 把数据重新转成对应类型然后返回
        return cacheData.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> JSON.parseObject(entry.getValue(), clazz)));
    }

    @Override
    public <K, V> Map<K, List<V>> getObjectListFromCache(List<K> ids, Class<V> clazz, String prefix) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }

        // 去构建key, 此时map的value是key
        Map<K, String> cacheData = getRedisData(ids, prefix);

        // 把数据重新转成List然后返回
        return cacheData.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> JSON.parseArray(entry.getValue(), clazz)));
    }


    /**
     * 获取数据
     * @param ids 占位符对应的元素
     * @param prefix 前缀
     * @param <K> 元素类型
     * @return 返回map, 其中key是占位符元素, value是key对应的value
     */
    private <K> Map<K, String> getRedisData(List<K> ids, String prefix) {
        // 移除空元素
        ids = ids.stream().filter(ObjectUtil::isNotNull).collect(Collectors.toList());
        List<String> keys = ids.stream().map(id -> String.format(prefix, id)).collect(Collectors.toList());
        // 从redis中取数据
        Map<String, String> cacheData = redisRepository.multiGet(keys);
        // 按照ids把数据都拼好返回
        Map<K, String> result = new HashMap<>(cacheData.size());
        for (K id : ids) {
            String key = String.format(prefix, id);
            String value = cacheData.get(key);
            if (StringUtils.isEmpty(value)) {
                continue;
            }
            result.put(id, value);
        }
        return result;
    }
}
