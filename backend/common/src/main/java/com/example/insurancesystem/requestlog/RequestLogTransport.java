package com.example.insurancesystem.requestlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/** 有界队列隔离请求与Redis；发送失败或溢出落盘，每日维护窗口补投，允许异常退出损失内存日志。 */
@Component
public class RequestLogTransport {
    public static final String STREAM="monitor:request-logs:v1";
    private static final Logger log=LoggerFactory.getLogger(RequestLogTransport.class);
    private final BlockingQueue<Map<String,Object>> queue;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Path directory;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"request-log-sender"); t.setDaemon(true); return t; });
    private final java.util.concurrent.atomic.AtomicBoolean replaying=new java.util.concurrent.atomic.AtomicBoolean();
    private final ExecutorService replayWorker=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"request-log-replay"); t.setDaemon(true); return t; });
    private volatile boolean running=true;
    /** 创建后端专属目录及发送线程；目录失败仅记录基础设施错误，不阻断业务。 */
    public RequestLogTransport(RedisConnectionFactory factory,ObjectMapper mapper,
            @Value("${spring.application.name:backend}") String name,
            @Value("${request-log.queue-capacity:2000}") int capacity,
            @Value("${request-log.spool-root:/var/data}") String spoolRoot) {
        this.mapper=mapper; redis=new StringRedisTemplate(factory);
        queue=new ArrayBlockingQueue<>(Math.max(1,capacity));
        directory=Paths.get(spoolRoot,name.replaceAll("[^a-zA-Z0-9_-]","_"));
        try { Files.createDirectories(directory); } catch(Exception e) { log.error("创建请求日志溢出目录失败",e); }
        worker.submit(this::sendLoop);
    }
    /** 非阻塞入队，队列满时落本地目录，不等待Redis。 */
    public void submit(Map<String,Object> value) { if(!queue.offer(value)) spool(value); }
    /** 后台发送失败落盘并退避，防止Redis故障时高频重试。 */
    private void sendLoop() {
        while(running || !queue.isEmpty()) {
            try { Map<String,Object> value=queue.poll(1,TimeUnit.SECONDS); if(value!=null) {
                try { publish(value); } catch(Exception e) { spool(value); Thread.sleep(1000); }
            }} catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }
    /** 固定Stream协议键跨服务共享，不裁剪未消费消息。 */
    private void publish(Map<String,Object> value) throws Exception {
        redis.opsForStream().add(STREAM,Collections.singletonMap("payload",mapper.writeValueAsString(value)));
    }
    /** 独立临时文件关闭后原子改名，维护只读取完整文件。 */
    private void spool(Map<String,Object> value) {
        try { Files.createDirectories(directory); Path temp=Files.createTempFile(directory,"log-",".writing");
            Files.writeString(temp,mapper.writeValueAsString(value));
            Files.move(temp,temp.resolveSibling(temp.getFileName().toString().replace(".writing",".ready")),StandardCopyOption.ATOMIC_MOVE);
        } catch(Exception e) { log.error("请求日志本地落盘失败",e); }
    }
    /** 每日维护补投完整文件；Redis接收成功后删除，失败保留到下次，重复由唯一ID去重。 */
    @Scheduled(cron="${request-log.replay-cron:0 30 4 * * *}",zone="Asia/Shanghai")
    public void scheduledReplay() {
        if(replaying.compareAndSet(false,true)) replayWorker.submit(() -> { try { replay(); } finally { replaying.set(false); } });
    }
    /** 在专用线程补投，避免长时间占用现有维护/租约调度线程；供测试同步验证。 */
    public void replay() {
        try(java.util.stream.Stream<Path> paths=Files.list(directory)) {
            Iterator<Path> it=paths.filter(p->p.toString().endsWith(".ready")).iterator();
            while(it.hasNext()) { Path p=it.next(); try {
                Map<String,Object> value=mapper.readValue(Files.readString(p),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                publish(value); Files.delete(p);
            } catch(Exception e) { log.warn("请求日志补投失败，保留文件 {}",p,e); break; } }
        } catch(Exception e) { log.warn("扫描请求日志目录失败",e); }
    }
    /** 正常退出有限排空，不无限延长服务关闭。 */
    @PreDestroy public void close() { running=false; replayWorker.shutdownNow(); worker.shutdown(); try { if(!worker.awaitTermination(5,TimeUnit.SECONDS)) worker.shutdownNow(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
}
