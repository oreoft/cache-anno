# Cache-anno

[![standard-readme compliant](https://img.shields.io/badge/readme%20style-standard-brightgreen.svg?style=flat-square)](https://github.com/RichardLitt/standard-readme)

English | [简体中文](./README.md)

Data automatic caching annotations


Automatic cache annotation based on Spring AOP, support Redis and local cache, read and write cache data in the simplest way

## This repository contains the following:

1. @Cache annotation
2. Automatically cache the specified method
2. It can automatically cache non-existent data to prevent cache penetration under concurrency
2. Automatic mutex lock can be enabled when acquiring cache to prevent cache breakdown and protect DB

## content list

- [background](#background)
- [how to import](#how-to-import)
- [how to use](#how-to-use)
- [next steps](#next-steps)
- [maintainers](#maintainers)
- [how to contribute](#how-to-contribute)
    - [Contributor](#Contributor)
- [License](#License)

## background

`cache-anno` Our purpose is to solve redundant code for redis query and write

The goals of this repository are:
1. Redundant code is reduced, and reads and writes to cache are imperceptible.
2. Reduce manual coding issues, built-in optional anti-cache breakdown/penetration function.
3. Reduce workload, get home early for dinner

## how-to-import

This library has been put on the maven central warehouse, only need to import the pom file of the project. Please note ``` that version 1.x.x are testing version and cannot work properly```

**For all versions, please click [here](https://mvnrepository.com/artifact/cn.someget/cache-anno)**, [**here**](https://mvnrepository.com/artifact/cn.someget/cache-anno) or  [**here**](https://mvnrepository.com/artifact/cn.someget/cache-anno)

Maven

```xml
<!-- https://mvnrepository.com/artifact/cn.someget/cache-anno -->
<dependency>
    <groupId>cn.someget</groupId>
    <artifactId>cache-anno</artifactId>
    <version>2.0.0</version>
</dependency>
```

Gradle

```groovy
// https://mvnrepository.com/artifact/cn.someget/cache-anno
implementation group: 'cn.someget', name: 'cache-anno', version: '2.0.0'
```



There is no configure need to for this library, all beans are exposed through spring.factories and can be directly scanned by the startup class.
## how-to-use

#### 1. Notes

1. This library relies on spring's auto-assembled redis or manually assembled redisTemplate, and the configuration supports jedis and lettuce.

2. The data stored using this annotation is serialized as String, and of course, the automatic read data deserialization of annotations is also String. If you use annotations to store data, but do not use annotations to read data, please use String deserialization to read.

3.All redis io exceptions have been captured, and the exceptions will be printed to the log, which will not pollute the business code, will not affect your data reading, and will eventually read data from the db

#### 2.  Instructions

1. Getting Started and Principles

```java
	// It is recommended to define the prefix as a constant for easy reuse. One to one does not need to pass the clazz parameter
	@Cache(prefix = "user:info:%d")
    public UserInfoBO getIpUserInfo(Long uid) {
        UserInfo userInfo = userInfoMapper.selectByUid(uid);
        if (userInfo == null) {
            return null;
        }
        UserInfoBO bo = new UserInfoBO();
        BeanUtils.copyProperties(userInfo, bo);
        return bo;
    }
```

If the above method is not annotated with ```@Cache```, it is a simple method to query user information from the user table via uid, but it is still such a simple method after adding ```@Cache```.  
Before the query, the parameter uid will be spliced according to the prefix in the annotation, and then try to get data from Redis.   
**If the execution function does not hit the cache, it will be automatically written to the cache after the execution is completed.**  
The next time this function is executed, the prefix splicing parameter will be executed and the data will be tried to obtain from Redis. Because it was written automatically last time, the data will be returned directly, and the function will not be executed again.

<img src="https://mypicgogo.oss-cn-hangzhou.aliyuncs.com/tuchuang20220514204948.png" alt="image-20220514204948154" style="zoom:67%;" />

2. advanced comprehension

```java
	// The return value is a collection type, clazz must be passed
	@Cache(prefix = "mall:item:%d", clazz = MallItemsBO.class)
    public Map<Long, MallItemsBO> listItems(List<Long> itemsIds) {
        BaseResult<List<MallItemsDTO>> result = itemsRemoteClient.listItems(new ItemsReqDTO());
        if (result == null || CollectionUtils.isEmpty(result.getData())) {
            return Collections.emptyMap();
        }
        return result.getData().stream()
                .map(mallItemsDTO -> {
                    MallItemsBO mallItemsBO = new MallItemsBO();
                    BeanUtils.copyProperties(mallItemsDTO, mallItemsBO);
                    return mallItemsBO;
                }).collect(Collectors.toMap(MallItemsBO::getItemId, Function.identity()));
    }
```

The method is not annotated with @Cache. It is a function to remotely obtain item details from other services in batches through itemId.  
When @Cache is added, it is still the original method. **Before the query, all itemIds in the parameters and the prefix in the annotation will be spliced together, and then the results will be obtained from redis at one time. If all itemIds are obtained, they will be returned directly.
If there is an itemId that is not hit, the missed itemId will be unified and used for remote fetching. Finally, the summary in Redis (the remote fetching will be automatically written to the cache)**

<img src="https://mypicgogo.oss-cn-hangzhou.aliyuncs.com/tuchuang20220514204802.png" alt="image-20220514204802863" style="zoom:67%;" />

#### 3. Supported method types

| type            | prefix              | input                        | output                                                     | remark                                                                                                                                                                                                                                                                                                                                                           |
|-----------------|---------------------|------------------------------|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| one to one      | custom: placeholder | wrapper type or String       | ? extends Object                                           | The number of input parameters is equal to the number of placeholders                                                                                                                                                                                                                                                                                            |
| ont to list     | custom: placeholder | wrapper type or String       | List<? extends Object>                                     | The same as above, in theory, List and object are same thing for this library, because I use String serialization                                                                                                                                                                                                                                                |
| list to map_one | custom: placeholder | List<wrapper type or String> | Map<Input wrapper type or String,  ? extends Object>       | If it is a batch query, the first input parameter must be the corresponding query List. Each element in the list will be spliced with the prefix, so the placeholder of the prefix is the placeholder corresponding to the element in the list.                                                                                                                  |
| list to map_map | custom: placeholder | List<wrapper type or String> | Map<Input wrapper type or String,  List<? extends Object>> | This type is actually the same as above. Each element in the type List corresponds to an object. Each element of this type List corresponds to a list. I deserialize the same.<br />**Due to the limitation of java's generic erasure, it is impossible to determine what the value generic of Map is. Please set the parameter hasMoreValue in @Cache to true** |

Placeholders should be noted that the string type requires the placeholder is %s, the integer placeholder is %d, and the floating-point placeholder is %f [Please refer to here for details](https://www.cnblogs.com/happyday56/p/3996498.html)

The summary is divided into two categories. The input parameter is an object or a List, that is, a single acquisition and a batch acquisition. If it is a batch acquisition, remember that the List must be No. 1, and the method input parameters cannot exceed two, otherwise the unsupported method will be thrown. abnormal.
#### 三. The meaning of the parameters in the annotation

| name            | meaning                                           | remarks                                                                                                                                                                                                                                                                                                               |
| --------------- |---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| prefix          | The prefix of the key in Redis                    | To use a placeholder, if the input parameter is long, the placeholder is prefixKey:%d                                                                                                                                                                                                                                 |
| expire          | Expiration time (in seconds)                      | If you do not set the expiration time when using annotations, the default is 10 minutes. Note that the expiration time is enabled by default in the write cache of this library.                                                                                                                                      |
| missExpire      | Empty cache expiration time (in seconds)          | If it is 0, it means that the empty cache is not enabled (the default is 0). The expiration time of the empty cache means that if the result is not found from the db, an empty cache will be generated to Redis. The expiration time of this empty cache (the normal cache must be short, recommended 3- 10 seconds) |
| hasMoreValue    | Whether list to map_map type                      | Due to the limitation of generic erasure in java, it is impossible to determine what the value generic of Map is. Please set the parameter hasMoreValue in @Cache to true                                                                                                                                             |
| clazz           | Collection class return value corresponding type  | **If the return value is List or Map**, **this must be passed**, because java generic erasure leads to inability to perceive the generic type of the collection, and deserialization needs to be used. If it is a one to one type, this can be omitted.                                                               |
| usingLocalCache | Whether to use local cache                        | After setting true, the local cache (using caffeine) will be queried before reading from Redis. Similarly, the data will be written back to caffeine after taking it.                                                                                                                                                 |

#### 4.  Detailed description of other functions

> Enable empty cache writes

@Cache contains the attribute missExpire. The meaning of the attribute is the expiration time of the value that does not exist in the DB (in seconds).
The default value is 0. If it is 0, it means that if the value of the query does not exist in the DB, no empty cache processing will be performed.
If it is not 0, then the query value from Redis will not be hit, and the method query will be performed. If the method query returns no result,
a null value will be stored (if it is an object, it is `"{\"id\":-1}"` , if it is a collection, it is `[]`), the expiration time is the value of missExpire (recommended 3-10 seconds),
and all types in the table are supported.
![image-20220514213607659](https://mypicgogo.oss-cn-hangzhou.aliyuncs.com/tuchuang20220514213607.png)

Note：

- After the empty cache is enabled, the cache processing needs to be deleted after inserting records, because the corresponding value may already exist in the DB, but there is still an empty value in Redis that is in the TTL.
- ~~If the empty cache is an object, it will cache an object with an id of -1. If it is a collection, an empty collection will be cached. An object with an id of -1 will not be returned to the method caller and will be filtered out directly, which is in line with your coding habits.
**It should be noted that the cache object must have an id field (both Integer and Long), otherwise it cannot be filtered, which will return an empty object with all properties null.**~~
Version 2.0.1 or later supports setting the object empty cache to `"{}"`, so it does not have to contain an id. The caller of the method that hits the empty cache will get null, which is in line with everyone's coding habits.
  All empty cache objects must be constructed without parameters, otherwise deserialization cannot generate empty objects.
> enable local cache

```@Cache``` contains the attribute usingLocalCache, which means enable local cache or not,
In e-commerce marketing, commodity data is frequently obtained through RPC, because the QPS of the marketing scenario is very high. Even if there is Redis before RPC, the frequent acquisition of commodities leads to a very high QPS of Redis.
The product data does not change very much, so it is necessary to add a local cache before redis, which will reduce the qps of redis

There are many scenarios where using local cache will reduce qps.
This library supports local caching. Just use the annotation to set the attribute of ```usingLocalCache``` to true (default is false). 
The local cache used by this library is caffeine, which has recently overwhelmed Guava. , so that the local cache is queried before getting the data, and if the local cache does not hit, then Redis is queried.

Note: Multi-layer caching will increase the possibility of Cache-DB inconsistency. Here, the default TTL of the local cache is 3 seconds, and modification is not supported for the time being.
##### 

## next-steps
1. Improve unit testing, welcome everyone to pr
2. The caller that hits the empty cache method of the object currently gets ```null``` instead of the empty cache. The business code does not need to judge the empty cache, and the collection empty cache will still return an empty List.
3. There are already good solutions to prevent cache breakdown, and the next version will merge
3. Evicting the cache using annotations

## maintainers

[@Oreoft](https://github.com/oreoft)

## how-to-contribute

Your joining is very welcome! [New Issue](https://github.com/oreoft/cache-anno/issues/new) or Pull Request.

Standard Readme follows [Contributor Covenant](http://contributor-covenant.org/version/1/3/0/) specification.

### Contributor

Wait until there are 100 starts to display


## License

[MIT](../cache-anno/LICENSE) © Oreoft
