package cn.someget.cache.service;

import java.util.List;
import java.util.Map;

/**
 * 自动缓存相关皆苦
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
public interface CacheService {

    /**
     * 从缓存获取对象
     * @param key 对应的key
     * @param clazz class
     * @param <V> 对应类型
     * @return 返回对象
     */
    <V> V getObjectFromCache(String key, Class<V> clazz);

    /**
     * 从缓存获取对象集合
     * @param key  对应的key
     * @param clazz class
     * @param <V> 对应类型
     * @return 返回对象
     */
    <V> List<V> getObjectListFromCache(String key, Class<V> clazz);

    /**
     * 批量缓存获取对象
     * @param ids 特征值,例如prefix:uid 那么uid就是特征值
     * @param clazz class
     * @param prefix 对应key的前缀
     * @param <K> 特征值类型
     * @param <V> 返回类型
     * @return 返回map,对应<特征值, 对应对象>
     */
    <K, V> Map<K, V> getObjectFromCache(List<K> ids, Class<V> clazz, String prefix);


    /**
     * 批量缓存获取对象集合
     * @param ids 特征值,例如prefix:uid 那么uid就是特征值
     * @param clazz class
     * @param prefix 对应key的前缀
     * @param <K> 特征值类型
     * @param <V> 返回类型
     * @return 返回map,对应<特征值, 对应对象集合>
     */
    <K, V> Map<K, List<V>> getObjectListFromCache(List<K> ids, Class<V> clazz, String prefix);
}
