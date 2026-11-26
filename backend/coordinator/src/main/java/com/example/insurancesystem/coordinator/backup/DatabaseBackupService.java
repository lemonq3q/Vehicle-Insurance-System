package com.example.insurancesystem.coordinator.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.*;
import java.util.zip.GZIPOutputStream;
import javax.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** 协调器备份编排：全量在维护末尾运行，binlog五分钟补传；单锁避免同名旧副本覆盖新副本。
 * 本地仅暂存，OSS清单是恢复依据；默认禁用，不自动恢复数据库或PURGE服务器日志。
 */
@Service
public class DatabaseBackupService {
    private static final Logger log=LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final Pattern POSITION=Pattern.compile("(?:MASTER|SOURCE)_LOG_FILE='([^']+)'[^\\n]*?(?:MASTER|SOURCE)_LOG_POS=(\\d+)");
    private final BackupProperties config;private final MySqlBackupTools tools;private final BackupObjectStore store;private final ObjectMapper mapper;private final RestTemplate rest;private final String secret;
    private final ReentrantLock lock=new ReentrantLock();
    private final ExecutorService uploads=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"mysql-binlog-backup");t.setDaemon(true);return t;});
    private final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    /** HTTP暂停超时短于数据库工具超时；失败依赖恢复finally及监控租约双重兜底。 */
    public DatabaseBackupService(BackupProperties config,MySqlBackupTools tools,BackupObjectStore store,ObjectMapper mapper,RestTemplateBuilder builder,CoordinatorProperties coordinator){this.config=config;this.tools=tools;this.store=store;this.mapper=mapper;secret=coordinator.getInternalSecret();rest=builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(40)).build();}
    /** 独立执行器防止网络与本地工具阻塞维护心跳，上一批未结束时不积压新任务。 */
    @Scheduled(fixedDelayString="${maintenance.backup.binlog-interval-ms:300000}")
    public void scheduleBinlogs(){if(config.isEnabled()&&queued.compareAndSet(false,true))uploads.submit(()->{try{if(lock.tryLock()){try{retryFull();uploadBinlogs();}finally{lock.unlock();}}}catch(Exception e){log.error("binlog备份失败，将在下次重试",e);}finally{queued.set(false);}});}
    /** 所有业务维护任务完成后调用；仅暂停日志写入的导出阶段，上传阶段已恢复消费。 */
    public void afterMaintenance(String runId){
        if(!config.isEnabled())return;
        boolean acquired=false;
        try{
            acquired=lock.tryLock(60,TimeUnit.SECONDS);if(!acquired)throw new IOException("等待binlog上传结束超时，本次全量未执行");
            Path root=tools.root();
            if(!"1".equals(tools.query("SELECT @@log_bin")))throw new IOException("MySQL未开启binlog");
            if(!"0".equals(tools.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+config.getDatabase()+"' AND table_type='BASE TABLE' AND engine<>'InnoDB'")))throw new IOException("存在非InnoDB表，不能保证一致性快照");
            String uuid=serverUuid();String id=Instant.now().toEpochMilli()+"-"+UUID.randomUUID();Path dir=root.resolve("full").resolve(id);Files.createDirectories(dir);
            Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("backupId",id);manifest.put("runId",runId);manifest.put("serverUuid",uuid);manifest.put("database",config.getDatabase());manifest.put("startedAt",Instant.now().toString());manifest.put("mysqlVersion",tools.query("SELECT VERSION()"));
            Path raw=dir.resolve("database.sql");
            try{
                /* 暂停成功但HTTP响应丢失时，finally仍尝试恢复，避免仅靠调用成功标记遗漏恢复。 */
                control("pause",runId);
                tools.dump(raw);String[] position=position(raw);manifest.put("binlogStartFile",position[0]);manifest.put("binlogStartPosition",Long.parseLong(position[1]));
                tools.query("FLUSH BINARY LOGS");manifest.put("afterDumpBinlog",lastBinlog());manifest.put("dumpCompletedAt",Instant.now().toString());
            }finally{try{control("resume",runId);}catch(Exception e){log.error("恢复日志消费失败，等待暂停租约自动到期",e);}}
            Path compressed=dir.resolve("database.sql.gz");try(InputStream in=Files.newInputStream(raw);OutputStream out=new GZIPOutputStream(Files.newOutputStream(compressed))){in.transferTo(out);}Files.delete(raw);
            String key=store.base()+"full/"+id+"/database.sql.gz";manifest.put("objectKey",key);manifest.put("sizeBytes",Files.size(compressed));manifest.put("sha256",store.sha256(compressed));save(dir.resolve("manifest.json"),manifest);
            uploadBinlogs();publishFull(dir,manifest);retryFull();cleanup();
            log.info("数据库全量备份已完成 backupId={}",id);
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();log.error("数据库全量备份失败，保留本地有效文件，下次补投",e);}finally{if(acquired)lock.unlock();}
    }
    /** 快照坐标只能来自mysqldump，不能用导出结束的时间或轮转文件代替。 */
    static String[] position(Path raw)throws IOException{try(BufferedReader reader=Files.newBufferedReader(raw)){String line;while((line=reader.readLine())!=null){Matcher match=POSITION.matcher(line);if(match.find())return new String[]{match.group(1),match.group(2)};}}throw new IOException("导出文件缺少binlog快照坐标");}
    /** 暂停接口已确认在途事务排空才返回200，密钥通过请求头传递，不落入备份清单。 */
    private void control(String action,String runId){HttpHeaders headers=new HttpHeaders();headers.set("X-Maintenance-Secret",secret);java.net.URI uri=UriComponentsBuilder.fromHttpUrl(config.getMonitorUrl()).path("/internal/maintenance/log-consumer/"+action).queryParam("runId",runId).queryParam("seconds",config.getTimeoutSeconds()+120).build().encode().toUri();
        Map<?,?> body=rest.exchange(uri,HttpMethod.POST,new HttpEntity<>(headers),Map.class).getBody();if(body==null||!Integer.valueOf(200).equals(body.get("code")))throw new IllegalStateException("日志消费控制失败");}
    /** 校验服务端UUID防止数据库重装后的同名binlog覆盖历史实例。 */
    private String serverUuid()throws Exception{String uuid=tools.query("SELECT @@server_uuid");if(!uuid.matches("[a-fA-F0-9-]{36}"))throw new IOException("服务器UUID不合法");return uuid;}
    /** SHOW BINARY LOGS的最后一项作为轮转后的活动文件，仅作展示而非恢复起点。 */
    private String lastBinlog()throws Exception{String[] lines=tools.query("SHOW BINARY LOGS").split("\\R");return lines[lines.length-1].split("\\t")[0];}
    /** 已关闭且校验过的文件跳过，活动文件只要增长就同名覆盖；不删除旧OSS对象再上传。 */
    private void uploadBinlogs()throws Exception{
        Path root=tools.root();String uuid=serverUuid();String rows=tools.query("SHOW BINARY LOGS");if(rows.isBlank())throw new IOException("binlog未开启或无日志");String[] lines=rows.split("\\R");
        for(int i=0;i<lines.length;i++){
            String[] row=lines[i].split("\\t");String name=row[0];long size=Long.parseLong(row[1]);boolean closed=i<lines.length-1;
            String key=store.base()+"binlog/"+uuid+"/"+name;String stateName=uuid+"-"+name+".json";Path state=root.resolve("state").resolve(stateName);Map<String,Object> previous=Files.exists(state)?read(state):Map.of();
            if(Objects.equals(previous.get("closed"),closed)&&Objects.equals(String.valueOf(previous.get("sourceSize")),String.valueOf(size)))continue;
            Path temp=Files.createTempDirectory(root.resolve("binlog"),"capture-");
            try{Path file=tools.binlog(name,temp);Map<String,Object> meta=new LinkedHashMap<>();meta.put("serverUuid",uuid);meta.put("file",name);meta.put("objectKey",key);meta.put("sourceSize",size);meta.put("sizeBytes",Files.size(file));meta.put("capturedEndPosition",Files.size(file));meta.put("closed",closed);meta.put("uploadedAt",Instant.now().toString());meta.put("sha256",store.upload(key,file,Map.of("server-uuid",uuid,"binlog-file",name)));store.json(key+".json",meta);save(state,meta);}
            finally{try(java.util.stream.Stream<Path> files=Files.list(temp)){for(Path file:(Iterable<Path>)files::iterator)Files.deleteIfExists(file);}Files.deleteIfExists(temp);}
        }
    }
    /** 已成功压缩的全量上传失败后可重试；损坏或未完成导出不被误发布。 */
    private void retryFull()throws Exception{Path root=tools.root();try(java.util.stream.Stream<Path> dirs=Files.list(root.resolve("full"))){for(Path dir:(Iterable<Path>)dirs::iterator){Path manifest=dir.resolve("manifest.json");if(Files.isRegularFile(manifest)){Map<String,Object> data=read(manifest);if(!Boolean.TRUE.equals(data.get("uploaded"))) {try{uploadBinlogs();publishFull(dir,data);}catch(Exception e){log.error("待补投全量备份尚未恢复：{}，继续保护并采集当前binlog",dir.getFileName(),e);}}}}}}
    /** 先文件、后清单，最后更新latest；SHA及长度已验证后才清理本地大文件。 */
    private void publishFull(Path dir,Map<String,Object> data)throws Exception{
        ensureChain(data);
        Path file=dir.resolve("database.sql.gz");if(!Files.isRegularFile(file))throw new IOException("待补投全量文件不存在");
        store.upload((String)data.get("objectKey"),file,Map.of("backup-id",(String)data.get("backupId"),"database",config.getDatabase()));data.put("uploadCompletedAt",Instant.now().toString());
        String key=store.base()+"full/"+data.get("backupId")+"/manifest.json";store.json(key,data);
        List<String> entries=store.list(store.base()+"full/");String newest=entries.stream().filter(k->k.endsWith("/manifest.json")).max(String::compareTo).orElse(key);if(newest.equals(key))store.json(store.base()+"catalog/latest.json",Map.of("manifestKey",key,"backupId",data.get("backupId")));
        data.put("uploaded",true);save(dir.resolve("manifest.json"),data);Files.deleteIfExists(file);
    }
    /** 发布全量前必须存在从快照起点开始的连续binlog对象，源端过期造成缺口时拒绝标为成功。 */
    private void ensureChain(Map<String,Object> data)throws Exception{
        String start=(String)data.get("binlogStartFile");int dot=start.lastIndexOf('.');if(dot<0)throw new IOException("binlog起点缺少序号");String prefix=start.substring(0,dot+1);long expected=Long.parseLong(start.substring(dot+1));
        List<String> files=new ArrayList<>();for(String key:store.list(store.base()+"binlog/"+data.get("serverUuid")+"/")){String name=key.substring(key.lastIndexOf('/')+1);if(name.startsWith(prefix)&&name.substring(prefix.length()).matches("[0-9]+")&&name.compareTo(start)>=0)files.add(name);}
        Collections.sort(files);if(files.isEmpty()||!files.get(0).equals(start))throw new IOException("快照起点binlog未上传，不能发布完整恢复链");
        for(String file:files){if(Long.parseLong(file.substring(prefix.length()))!=expected++)throw new IOException("binlog恢复链存在缺口");}
    }
    /** 按恢复依赖保留30天和边界锚点；无有效全量时不删除任何binlog。 */
    private void cleanup()throws Exception{
        Instant cutoff=Instant.now().minus(Duration.ofDays(config.getRetentionDays()));List<Map<String,Object>> all=new ArrayList<>();
        for(String key:store.list(store.base()+"full/"))if(key.endsWith("/manifest.json")){Map<String,Object> m=store.read(key);m.put("manifestKey",key);all.add(m);}
        if(all.isEmpty())return;
        Set<Object> recentUuids=new HashSet<>();for(Map<String,Object> m:all)if(!Instant.parse((String)m.get("startedAt")).isBefore(cutoff))recentUuids.add(m.get("serverUuid"));
        Map<String,Map<String,Object>> anchors=new HashMap<>();for(Map<String,Object> m:all)if(recentUuids.contains(m.get("serverUuid"))&&Instant.parse((String)m.get("startedAt")).isBefore(cutoff)){String uuid=(String)m.get("serverUuid");Map<String,Object> old=anchors.get(uuid);if(old==null||((String)m.get("startedAt")).compareTo((String)old.get("startedAt"))>0)anchors.put(uuid,m);}
        Map<String,String> starts=new HashMap<>();for(Map<String,Object> m:all){boolean keep=!Instant.parse((String)m.get("startedAt")).isBefore(cutoff)||anchors.get(m.get("serverUuid"))==m;if(keep)starts.merge((String)m.get("serverUuid"),(String)m.get("binlogStartFile"),(a,b)->a.compareTo(b)<0?a:b);else{store.delete((String)m.get("objectKey"));store.delete((String)m.get("manifestKey"));}}
        for(String key:store.list(store.base()+"binlog/"))if(key.endsWith(".json")){Map<String,Object> m=store.read(key);String first=starts.get(m.get("serverUuid"));if(Boolean.TRUE.equals(m.get("closed"))&&Instant.parse((String)m.get("uploadedAt")).isBefore(cutoff)&&(first==null||((String)m.get("file")).compareTo(first)<0)){store.delete((String)m.get("objectKey"));store.delete(key);}}
    }
    /** 本地小清单原子替换，进程重启不会把半条JSON当成功状态。 */
    private void save(Path file,Map<String,Object> data)throws IOException{Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"state-",".tmp");mapper.writeValue(temp.toFile(),data);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    /** 仅从本地读取本模块生成的JSON状态。 */
    private Map<String,Object> read(Path file)throws IOException{return mapper.readValue(file.toFile(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
    /** 关闭后台上传线程，正在运行的工具由超时控制，不修改业务维护状态。 */
    @PreDestroy public void close(){uploads.shutdownNow();}
}
