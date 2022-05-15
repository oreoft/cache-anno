package cn.someget.cache.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 会用到的常量
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisKey {

    /**
     * 通用过期时间 10分钟
     */
    public static final long COMMON_HIT_EXPIRE = 600L;

    /**
     * 未命中空缓存时间 10秒
     */
    public static final long COMMON_MISS_EXPIRE = 10L;

    /**
     * 取消空缓存过期时间
     */
    public static final Long DISABLE_MISS_VALUE = 0L;

    /**
     * 通用空对象缓存
     */
    public static final String EMPTY_OBJECT = "{}";

    /**
     * 空缓存 value
     */
    public static final String EMPTY_COLLECTION = "[]";

    /**
     * 分布式锁失效时间，单位秒
     */
    public static final int DEFAULT_RELEASE_TIME = 2;

}
