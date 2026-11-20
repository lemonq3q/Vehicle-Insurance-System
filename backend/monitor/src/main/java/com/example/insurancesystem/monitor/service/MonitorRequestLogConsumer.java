package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.requestlog.RequestLogTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import javax.annotation.*;
import org.slf4j.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 监控专用消费者：先事务入库后确认，失败保留pending；唯一日志ID使重投幂等。 */
@Service
public class MonitorRequestLogConsumer {
    private static final Logger log=LoggerFactory.getLogger(MonitorRequestLogConsumer.class);
    private static final String GROUP="monitor-request-log";
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private volatile boolean running=true;
    private Thread thread;
    /** 使用专用字符串Redis客户端，不改变现有业务缓存序列化。 */
    public MonitorRequestLogConsumer(RedisConnectionFactory factory,JdbcTemplate jdbc,ObjectMapper mapper,org.springframework.transaction.PlatformTransactionManager manager) {
        this.redis=new StringRedisTemplate(factory); this.jdbc=jdbc; this.mapper=mapper; transaction=new TransactionTemplate(manager);
    }
    /** 独立后台线程，不在调度线程或HTTP请求中等待消息。 */
    @PostConstruct public void start() { thread=new Thread(this::consume,"monitor-request-log-consumer"); thread.setDaemon(true); thread.start(); }
    /** 固定消费者名恢复pending，多实例重复处理由数据库唯一键兜底。 */
    private void consume() {
        while(running) {
            try {
                try { redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> c.execute("XGROUP","CREATE".getBytes(),RequestLogTransport.STREAM.getBytes(),GROUP.getBytes(),"0".getBytes(),"MKSTREAM".getBytes())); }
                catch(Exception e) { if(!Objects.toString(e.getMessage(),"").contains("BUSYGROUP")) throw e; }
                List<MapRecord<String,Object,Object>> records=redis.opsForStream().read(Consumer.from(GROUP,"writer"),StreamReadOptions.empty().count(50),StreamOffset.create(RequestLogTransport.STREAM,ReadOffset.from("0")));
                /*
                 * 空队列的服务端阻塞时间必须小于客户端命令超时（当前2秒），
                 * 留出网络与调度余量，避免把正常空闲等待误判为Redis故障。
                 */
                if(records==null || records.isEmpty()) records=redis.opsForStream().read(Consumer.from(GROUP,"writer"),StreamReadOptions.empty().count(50).block(Duration.ofMillis(500)),StreamOffset.create(RequestLogTransport.STREAM,ReadOffset.lastConsumed()));
                if(records==null) continue;
                for(MapRecord<String,Object,Object> record:records) {
                    String payload=String.valueOf(record.getValue().get("payload"));
                    Map<String,Object> d=mapper.readValue(payload,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                    transaction.execute(status -> {
                        jdbc.update("INSERT INTO monitor_request_log(log_id,source_system,started_at,duration_ms,method,uri,http_status,business_code,content_type,has_exception,enterprise_id,user_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE log_id=VALUES(log_id)",
                            d.get("logId"),d.get("sourceSystem"),new Timestamp(((Number)d.get("startedAt")).longValue()),d.get("durationMs"),d.get("method"),d.get("uri"),d.get("httpStatus"),d.get("businessCode"),d.get("contentType"),Boolean.TRUE.equals(d.get("hasException"))?1:0,d.get("enterpriseId"),d.get("userId"));
                        jdbc.update("INSERT INTO monitor_request_log_detail(log_id,payload) VALUES(?,?) ON DUPLICATE KEY UPDATE log_id=VALUES(log_id)",d.get("logId"),payload); return null;
                    });
                    redis.opsForStream().acknowledge(RequestLogTransport.STREAM,GROUP,record.getId());
                    redis.opsForStream().delete(RequestLogTransport.STREAM,record.getId());
                }
            } catch(Exception e) { log.warn("请求日志消费失败，保留消息重试",e); try { Thread.sleep(3000); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return; } }
        }
    }
    /** 关闭消费线程，不确认未提交的消息。 */
    @PreDestroy public void stop() { running=false; if(thread!=null) thread.interrupt(); }
}
