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
public class RedisCache {
    private final RedisTemplate redisTemplate;
    private final RedisKeyPrefixManager keyPrefixManager;

    @Autowired
    public RedisCache(RedisTemplate redisTemplate, RedisKeyPrefixManager keyPrefixManager) {
        this.redisTemplate = redisTemplate;
        this.keyPrefixManager = keyPrefixManager;
    }

    public <T> void setCacheObject(final String key, final T value){
        redisTemplate.opsForValue().set(redisKey(key), value);
    }

    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit){
        redisTemplate.opsForValue().set(redisKey(key), value, timeout, timeUnit);
    }

    public boolean expire(final String key, final long timeout){
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    public boolean expire(final String key, final long timeout, final TimeUnit unit){
        return redisTemplate.expire(redisKey(key), timeout, unit);
    }

    public <T> T getCacheObject(final String key){
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(redisKey(key));
    }

    /**
     * Claims a short-lived value once. The claim marker prevents concurrent exchange requests
     * from consuming the same authorization code, even when the application has multiple nodes.
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

    public boolean deleteObject(final String key){
        return redisTemplate.delete(redisKey(key));
    }

    public long deleteObject(final Collection<String> collection){
        return redisTemplate.delete(collection.stream().map(this::redisKey).toList());
    }

    public <T> long setCacheList(final String key, final List<T> dataList){
        Long count = redisTemplate.opsForList().rightPushAll(redisKey(key), dataList);
        return count == null ? 0 : count;
    }

    public <T> List<T> getCacheList(final String key){
        return redisTemplate.opsForList().range(redisKey(key), 0, -1);
    }

    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet){
        BoundSetOperations<String, T> setOperations = redisTemplate.boundSetOps(redisKey(key));
        Iterator<T> iterator = dataSet.iterator();
        while (iterator.hasNext()){
            setOperations.add(iterator.next());
        }
        return setOperations;
    }

    public <T> Set<T> getCacheSet(final String key){
        return redisTemplate.opsForSet().members(redisKey(key));
    }

    public <T> void setCacheMap(final String key, final Map<String, T> dataMap){
        if(dataMap != null){
            redisTemplate.opsForHash().putAll(redisKey(key), dataMap);
        }
    }

    public <T> Map<String, T> getCacheMap(final String key){
        return redisTemplate.opsForHash().entries(redisKey(key));
    }

    public <T> void setCacheMapValue(final String key, final String hkey, final T value){
        redisTemplate.opsForHash().put(redisKey(key), hkey, value);
    }

    public <T> T getCacheMapValue(final String key, final String hkey){
        HashOperations<String, String, T> ops = redisTemplate.opsForHash();
        return ops.get(redisKey(key), hkey);
    }

    public void delCacheMapValue(final String key, final String hkeys){
        redisTemplate.opsForHash().delete(redisKey(key), hkeys);
    }

    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hkeys){
        return redisTemplate.opsForHash().multiGet(redisKey(key), hkeys);
    }

    public Collection<String> keys(final String pattern){
        return redisTemplate.keys(keyPrefixManager.pattern(pattern));
    }

    private String redisKey(String logicalKey) {
        return keyPrefixManager.key(logicalKey);
    }

}
