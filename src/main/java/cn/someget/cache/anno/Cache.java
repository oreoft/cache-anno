package cn.someget.cache.anno;

import cn.someget.cache.utils.RedisKey;

import java.lang.annotation.*;

/**
 * 对外暴露的注解
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Cache {

    /**
     * key的前缀, 注意要使用占位符
     * 如入参是long, 占位符就是prefixKey:%d
     */
    String prefix() default "";

    /**
     * 单位秒
     */
    long expire() default 0L;

    /**
     * 单位秒
     */
    long missExpire() default RedisKey.COMMON_MISS_EXPIRE;

    /**
     * 是否多对多(如果是Map<T, List<E>>的结构才需要设置成true)
     * java的泛型擦除, 没得办法, 后期会想办法
     */
    boolean hasMoreValue() default false;

    /**
     * 对应类型, 反序列化需要使用, 记得传
     */
    Class<?> clazz() default Object.class;

    /**
     * 是否使用本地缓存(caffeine)
     * 注意使用了本地缓存就不会使用redis了
     */
    boolean usingLocalCache() default false;

}
