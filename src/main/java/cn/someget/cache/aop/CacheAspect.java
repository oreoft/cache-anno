package cn.someget.cache.aop;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.someget.cache.anno.Cache;
import cn.someget.cache.service.CacheService;
import cn.someget.cache.utils.RedisKey;
import cn.someget.cache.utils.RedisRepository;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cn.someget.cache.utils.RedisKey.DISABLE_MISS_VALUE;

/**
 * 切面处理
 *
 * @author zyf
 * @date 2022-05-03 16:05
 */
@Slf4j
@Component
@Aspect
public class CacheAspect {

    @Resource
    private RedisRepository redisRepository;

    @Resource(name = "defaultRedisCacheService")
    private CacheService redisCacheService;

    @Resource(name = "defaultLocalCacheService")
    private CacheService localCacheService;

    @Pointcut(value = "@annotation(cn.someget.cache.anno.Cache)")
    public void cache() {
        //point
    }

    @Around("cache()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 拿到当前方法信息
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Cache cache = method.getAnnotation(Cache.class);
        boolean usingLocalCache = cache.usingLocalCache();
        // 判断使用redis还是local进行自动缓存
        CacheService cacheService = usingLocalCache ? localCacheService : redisCacheService;
        // 获取前缀
        String prefix = cache.prefix();
        // 获取存储的类型
        Class<?> clazz = cache.clazz();
        // 是否多对多 仅在方法出参为Map<T, List<E>>的结构下才生效
        boolean hasMoreValue = cache.hasMoreValue();
        // 获取过期时间
        long expire = cache.expire();
        // 获取未命中的过期时间
        long missExpire = cache.missExpire();
        // 获取方法的入参
        Object[] args = joinPoint.getArgs();
        // 获取返回类型
        Class<?> returnType = method.getReturnType();
        // 如果没有入参的话, 暂不支持这种方式自动缓存
        Assert.notNull(args, "do not support non args");
        // 如果参数是null, 直接返回null
        Object arg = args[0];
        if (arg == null) {
            log.warn("query key is null, class:{}, method:{}, args:{}", clazz.getName(), method.getName(), JSON.toJSONString(args));
            return null;
        }

        // 如果第一个入参是list, 则说明是多对多的返回
        if (arg instanceof List) {
            // 如果参数大于2则不支持
            Assert.isTrue(args.length <= 2, "do not support args > 2");
            // 如果有两个参数,第二个参数是List或者Map也不支持
            boolean paramCheck = args.length == 2 && (args[1] instanceof List || arg instanceof Map);
            Assert.isFalse(paramCheck, "do not support the args");
            // 验证返回值是否是字典(那么返回参数一定要是Map, 不然无法映射多对多的关系)
            Assert.isTrue(returnType == Map.class, "param error");

            // 把参数变成恢复成List
            List<Object> inputList = JSON.parseArray(JSON.toJSONString(arg), Object.class);

            // 把第二个参数拼接到prefix上(若有)
            if (args.length == 2) {
                String param2 = Convert.toStr(args[1], "");
                if (CharSequenceUtil.isNotBlank(param2)) {
                    prefix = prefix + ":" + param2;
                }
            }

            // 去缓存容器里面取数据, list one to many(Map<K, List<V>)
            if (hasMoreValue) {
                Map<Object, ?> objectFromLocalCache = cacheService.getObjectListFromCache(inputList, clazz, prefix);
                // 执行自动缓存方法 list one to many(Map<K, List<V>)
                doHandleListCache(inputList, clazz, objectFromLocalCache, joinPoint, prefix, expire,
                        instance -> new ArrayList<>(), args, missExpire);
                return objectFromLocalCache;
            }

            // 去缓存容器里面取数据, list one to one(Map<K, V>)
            Map<Object, ?> objectFromLocalCache = cacheService.getObjectFromCache(inputList, clazz, prefix);
            // 执行自动缓存方法 list one to one(Map<K, V>)
            doHandleListCache(inputList, clazz, objectFromLocalCache, joinPoint, prefix, expire,
                    instance -> this.buildEmptyObject(clazz), args, missExpire);
            return objectFromLocalCache;
        } else {
            // 如果第一个入参不是list,说明是一对一或者一对多的返回
            String key = String.format(prefix, args);
            // one to list
            if (returnType == List.class) {
                return doHandleOne2ListCache(joinPoint, key, clazz, expire, cacheService, missExpire);
            }
            // one to one
            return doHandleOne2OneCache(joinPoint, key, expire, returnType, cacheService, missExpire);
        }
    }

    /**
     * 处理one to list的自动缓存
     */
    private Object doHandleOne2ListCache(ProceedingJoinPoint joinPoint,
                                         String key,
                                         Class<?> returnType,
                                         long expire,
                                         CacheService cacheService,
                                         long missExpire) throws Throwable {
        // 从缓存容器里面拿数据
        List<?> objectListFromCache = cacheService.getObjectListFromCache(key, returnType);
        // 不为空的话就把这个直接返回
        if (CollUtil.isNotEmpty(objectListFromCache)) {
            return objectListFromCache;
        }
        // 为空的话执行方法, 拿到从db查询的结果
        Object proceed = joinPoint.proceed();
        if (proceed instanceof List) {
            List<?> real = (List<?>) proceed;
            // db查询也为空的话, 设置空缓存
            if (CollectionUtils.isEmpty(real)) {
                proceed = new ArrayList<>();
                expire = missExpire;
            }
            // 如果空缓存过期时间不为0, 则表示需要进行空缓存
            if (!DISABLE_MISS_VALUE.equals(expire)) {
                redisRepository.set(key, expire, proceed);
            }
        }
        return proceed;
    }

    /**
     * 处理one to one的自动缓存
     */
    private Object doHandleOne2OneCache(ProceedingJoinPoint joinPoint,
                                        String key, long expire,
                                        Class<?> returnType,
                                        CacheService cacheService,
                                        long missExpire) throws Throwable {
        // 从缓存容器获取数据
        Object objectFromLocalCache = cacheService.getObjectFromCache(key, returnType);
        // 如果有数据, 则直接返回
        if (objectFromLocalCache != null) {
            return objectFromLocalCache;
        }
        // 如果没有命中则走方法拿数据
        Object proceed = joinPoint.proceed();
        // 如果方法也返回null, 则设置空缓存
        if (proceed == null) {
            proceed = buildEmptyObject(returnType);
            expire = missExpire;
        }
        // 如果空缓存过期时间不为0, 则表示需要进行空缓存
        if (!DISABLE_MISS_VALUE.equals(expire)) {
            redisRepository.set(key, expire, proceed);
        }
        return proceed;
    }

    /**
     * list to map的自动缓存
     * 这里又分为两种,map的value是对象或者List<对象>
     */
    @SuppressWarnings("unchecked")
    private void doHandleListCache(List<Object> inputList, Class<?> clazz,
                                   Map<Object, ?> objectFromLocalCache,
                                   ProceedingJoinPoint joinPoint,
                                   String prefix, long expire,
                                   UnaryOperator<Object> emptyCallback,
                                   Object[] args, long missExpire) throws Throwable {
        // objectFromLocalCache这个已经是从缓存容器里面取出来的值, 看一下inputList中少了没有, 如果少了放miss部分走方法给它补上
        List<Object> cacheMissList = inputList.stream()
                .filter(key -> !objectFromLocalCache.containsKey(key))
                .collect(Collectors.toList());
        // 如果没有miss的说明, 全在缓存中取了, 直接返回
        if (CollectionUtils.isEmpty(cacheMissList)) {
            return;
        }
        // 参数1是未命中的key集合, 其他位是多余的入参数信息
        List<Object> params = new ArrayList<>();
        params.add(cacheMissList);
        /*
            把其他位置的参数都添加到参数列表中(虽然目前只支持2个参数)
            这里其实就是想把args的第一个元素从inputList换成missList, 但是修改args是上游传过来的, 动它怕留坑
         */
        IntStream.range(1, args.length)
                .forEach(index -> params.add(args[index]));

        // 送去执行方法, 然后拿到结果
        Map result = (Map) joinPoint.proceed(params.toArray());

        List<Object> dbMissingList;
        if (MapUtil.isEmpty(result)) {
            // 如果redis为空,说明走方法查到也都是空
            dbMissingList = cacheMissList;
        } else {
            // 否则说明方法还是查到了部分或者全部数据, 取出这部分数据
            dbMissingList = cacheMissList.stream()
                    .filter(key -> !result.containsKey(key))
                    .collect(Collectors.toList());
            Map<String, Object> keyValues = new HashMap<>(result.size());
            result.forEach((k, v) -> keyValues.put(String.format(prefix, k), v));
            // 然后写入缓存容器
            redisRepository.batchSet(keyValues, expire);
            // 并且放入到要返回的结果中
            objectFromLocalCache.putAll(result);
        }

        // 如果dbMiss为空表示方法查询到了所有miss数据, 则直接返回
        if (CollectionUtils.isEmpty(dbMissingList)) {
            return;
        }

        // 如果空缓存过期时间不为0, 则把没命中的数据都空缓存一下
        if (!DISABLE_MISS_VALUE.equals(missExpire)) {
            // 把剩下missList转换成key-Empty写入redis(这里没有回写结果, 因为没区别)
            Map<String, Object> emptyMissData = dbMissingList.stream()
                    .collect(Collectors.toMap(key -> String.format(prefix, key), key -> emptyCallback.apply(clazz)));
            redisRepository.batchSet(emptyMissData, missExpire);
        }
    }

    /**
     * 制造空缓存
     */
    private Object buildEmptyObject(Class<?> clazz) {
        Object empty = null;
        try {
            Method emptyMethod = clazz.getMethod("emptyObject");
            empty = emptyMethod.invoke(null);
        } catch (Exception e) {
            log.warn("call static emptyObject error, className:{}", clazz.getName());
        }
        return empty == null ? RedisKey.EMPTY_OBJECT : JSON.toJSONString(empty);
    }

}
