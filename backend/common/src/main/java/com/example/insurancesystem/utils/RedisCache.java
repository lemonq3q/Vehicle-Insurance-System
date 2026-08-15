package com.example.insurancesystem.utils;

import com.example.insurancesystem.config.RedisKeyPrefixManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings(value = {"unchecked", "rawtypes"})
@Component
/**
 * 对 Spring RedisTemplate 的字符串、列表、集合和 Hash 操作进行统一封装，并在所有逻辑键前自动添加应用命名空间。
 */
public class RedisCache {
    private final RedisTemplate redisTemplate;
    private final RedisKeyPrefixManager keyPrefixManager;

    @Autowired
    /**
     * 注入统一序列化模板和键前缀管理器，保证所有缓存操作采用相同类型恢复及应用隔离规则。
     */
    public RedisCache(RedisTemplate redisTemplate, RedisKeyPrefixManager keyPrefixManager) {
        this.redisTemplate = redisTemplate;
        this.keyPrefixManager = keyPrefixManager;
    }

    /**
     * 写入一个不过期的普通对象缓存。
     */
    public <T> void setCacheObject(final String key, final T value){
        redisTemplate.opsForValue().set(redisKey(key), value);
    }

    /**
     * 按指定时长和单位写入普通对象缓存，登录会话、验证码和临时授权码使用该入口控制生命周期。
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit){
        redisTemplate.opsForValue().set(redisKey(key), value, timeout, timeUnit);
    }

    /**
     * 以秒为单位更新逻辑键过期时间。
     */
    public boolean expire(final String key, final long timeout){
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 按指定时间单位更新逻辑键过期时间，并返回 Redis 是否成功应用设置。
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit){
        return redisTemplate.expire(redisKey(key), timeout, unit);
    }

    /**
     * 读取普通对象缓存，由 Redis JSON 序列化器恢复原运行时类型。
     */
    public <T> T getCacheObject(final String key){
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(redisKey(key));
    }

    /**
     * 通过 setIfAbsent 原子创建短期 claimed 标记，实现临时授权值的一次性领取。多个节点并发兑换同一 SSO 码时，
     * 只有首个请求能获得原值并删除主键，后续请求返回空；领取标记自动过期，避免异常中断留下永久锁。
     */
    public <T> T getAndDeleteOnce(final String key, final long claimTimeout, final TimeUnit unit) {
        String redisKey = redisKey(key);
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(redisKey + ":claimed", "1", claimTimeout, unit);
        if (!Boolean.TRUE.equals(claimed)) {
            return null;
        }
        T value = getCacheObject(key);
        if (value != null) {
            deleteObject(key);
        }
        return value;
    }

    /**
     * 删除单个逻辑键并返回是否实际删除。
     */
    public boolean deleteObject(final String key){
        return redisTemplate.delete(redisKey(key));
    }

    /**
     * 为一组逻辑键添加命名空间后批量删除，返回删除数量。
     */
    public long deleteObject(final Collection<String> collection){
        return redisTemplate.delete(collection.stream().map(this::redisKey).toList());
    }

    /**
     * 将列表元素按原顺序追加到 Redis List 右侧并返回新增元素数。
     */
    public <T> long setCacheList(final String key, final List<T> dataList){
        Long count = redisTemplate.opsForList().rightPushAll(redisKey(key), dataList);
        return count == null ? 0 : count;
    }

    /**
     * 读取 Redis List 的全部元素并保持存储顺序。
     */
    public <T> List<T> getCacheList(final String key){
        return redisTemplate.opsForList().range(redisKey(key), 0, -1);
    }

    /**
     * 将集合逐项加入 Redis Set，并返回绑定操作对象供调用方继续执行集合命令。
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet){
        BoundSetOperations<String, T> setOperations = redisTemplate.boundSetOps(redisKey(key));
        Iterator<T> iterator = dataSet.iterator();
        while (iterator.hasNext()){
            setOperations.add(iterator.next());
        }
        return setOperations;
    }

    /**
     * 获取 Redis Set 的全部去重成员。
     */
    public <T> Set<T> getCacheSet(final String key){
        return redisTemplate.opsForSet().members(redisKey(key));
    }

    /**
     * 将非空 Map 的所有字段批量写入 Redis Hash；空 Map 不执行无意义命令。
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap){
        if(dataMap != null){
            redisTemplate.opsForHash().putAll(redisKey(key), dataMap);
        }
    }

    /**
     * 读取 Redis Hash 的全部字段和值。
     */
    public <T> Map<String, T> getCacheMap(final String key){
        return redisTemplate.opsForHash().entries(redisKey(key));
    }

    /**
     * 写入或覆盖 Redis Hash 中的单个字段值。
     */
    public <T> void setCacheMapValue(final String key, final String hkey, final T value){
        redisTemplate.opsForHash().put(redisKey(key), hkey, value);
    }

    /**
     * 读取 Redis Hash 中指定字段并恢复目标类型。
     */
    public <T> T getCacheMapValue(final String key, final String hkey){
        HashOperations<String, String, T> ops = redisTemplate.opsForHash();
        return ops.get(redisKey(key), hkey);
    }

    /**
     * 删除 Redis Hash 的指定字段。
     */
    public void delCacheMapValue(final String key, final String hkeys){
        redisTemplate.opsForHash().delete(redisKey(key), hkeys);
    }

    /**
     * 按字段集合批量读取 Redis Hash 值，结果顺序与输入字段顺序一致。
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hkeys){
        return redisTemplate.opsForHash().multiGet(redisKey(key), hkeys);
    }

    /**
     * 在应用命名空间内按模式扫描键，防止匹配共享 Redis 中其他部署的缓存。
     */
    public Collection<String> keys(final String pattern){
        return redisTemplate.keys(keyPrefixManager.pattern(pattern));
    }

    /**
     * 将业务逻辑键转换为带应用前缀的真实 Redis 键，所有公开操作统一经此入口隔离命名空间。
     */
    private String redisKey(String logicalKey) {
        return keyPrefixManager.key(logicalKey);
    }

}
