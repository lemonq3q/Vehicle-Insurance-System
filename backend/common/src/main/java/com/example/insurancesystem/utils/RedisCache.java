package com.example.insurancesystem.utils;

import com.example.insurancesystem.config.RedisKeyPrefixManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
     * 原子占用有时限的业务键，用于验证码发送冷却；多实例同时申请时只有一个请求可发送邮件。
     * @return 当前调用首次创建该键时为 true，已被其他请求占用时为 false
     */
    public boolean putIfAbsent(String key, Object value, long seconds) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(redisKey(key), value, seconds, TimeUnit.SECONDS));
    }

    /**
     * 仅在缓存仍等于调用方读到的值时删除，使用 Lua 保证验证码不能被并发消费两次，
     * 并避免旧请求删除新验证码或其他请求持有的冷却标记。参数沿用模板的 JSON 序列化器。
     */
    public boolean deleteIfEquals(String key, Object expected) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
        return Long.valueOf(1).equals(redisTemplate.execute(script, Collections.singletonList(redisKey(key)), expected));
    }

    /**
     * 比较 Redis 原始会话中的 sessionId 后原子删除，兼容旧版本缺少新增字段的序列化记录。
     * 不比较整个对象，避免应用升级后新增字段使旧会话无法撤销；新登录的不同 jti 受保护。
     */
    public boolean deleteSessionIfId(String key, String sessionId) {
        return changeSessionIfId(key, sessionId, "return redis.call('del', KEYS[1])");
    }

    /**
     * 只有原始会话仍属于相同 sessionId 才续期，不重新创建已撤回的会话。
     * 时间为服务端数值直接编入 Lua，避免 JSON 值序列化影响 Redis expire 的整数参数。
     */
    public boolean expireSessionIfId(String key, String sessionId, long seconds) {
        return changeSessionIfId(key, sessionId, "return redis.call('expire', KEYS[1], " + seconds + ")");
    }

    /**
     * 在 Lua 中读取原始 JSON 并比较 jti，再执行固定会话操作；无记录或非法历史数据均安全失败。
     * 同时兼容 JSON 对象和历史类型包装数组，外部输入不参与 Lua 操作语句拼接。
     */
    private boolean changeSessionIfId(String key, String sessionId, String operation) {
        if (sessionId == null) return false;
        String source = "local raw=redis.call('get',KEYS[1]); if not raw then return 0 end; "
                + "local ok,doc=pcall(cjson.decode,raw); if not ok or type(doc)~='table' then return 0 end; "
                + "if type(doc[2])=='table' then doc=doc[2] end; "
                + "local expected=cjson.decode(ARGV[1]); if doc['sessionId']==expected then " + operation + " else return 0 end";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(source, Long.class);
        return Long.valueOf(1).equals(redisTemplate.execute(script, Collections.singletonList(redisKey(key)), sessionId));
    }

    /**
     * 原子增加短期错误尝试次数，首次计数时设置过期时间，阻止并发绕过验证码尝试额度。
     */
    public long incrementCounter(String key, long seconds) {
        Long count = redisTemplate.opsForValue().increment(redisKey(key));
        if (Long.valueOf(1).equals(count)) expire(key, seconds);
        return count == null ? 0 : count;
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
