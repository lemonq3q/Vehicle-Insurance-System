package com.example.insurancesystem.coordinator.backup;

import com.example.insurancesystem.backup.BackupInventory;
import com.example.insurancesystem.backup.BackupInventory.*;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * 唯一备份写入方：每日全量与五分钟扫描共享互斥、本地两份状态及OSS两份索引。
 * A2/A4/A6落盘后才推进，远程幂等upsert覆盖；默认禁用，不自动恢复或PURGE MySQL原文件。
 */
@Service
public class DatabaseBackupService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final Pattern POSITION = Pattern.compile("(?:MASTER|SOURCE)_LOG_FILE='([^']+)'[^\\n]*?(?:MASTER|SOURCE)_LOG_POS=(\\d+)");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final BackupProperties config;
    private final MySqlBackupTools tools;
    private final BackupObjectStore store;
    private final ObjectMapper mapper;
    private final RestTemplate rest;
    private final String secret;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ExecutorService uploads = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mysql-binlog-backup"); thread.setDaemon(true); return thread;
    });

    /**
     * 注入原有工具、OSS与维护密钥；构造时不连接数据库，不启动备份。
     */
    public DatabaseBackupService(BackupProperties config, MySqlBackupTools tools, BackupObjectStore store,
            ObjectMapper mapper, RestTemplateBuilder builder, CoordinatorProperties coordinator) {
        this.config=config; this.tools=tools; this.store=store; this.mapper=mapper;
        secret=coordinator.getInternalSecret();
        rest=builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(40)).build();
    }

    /**
     * 五分钟仅提交一批后台作业，不占维护心跳线程，上一批未结束时不堆积任务。
     */
    @Scheduled(fixedDelayString="${maintenance.backup.binlog-interval-ms:300000}")
    public void scheduleBinlogs() {
        if(config.isEnabled() && queued.compareAndSet(false,true)) uploads.submit(() -> {
            try { runBatch(null); } finally { queued.set(false); }
        });
    }

    /**
     * 维护末尾创建全量并清理；备份失败只报警，不能阻止参与服务恢复流量。
     */
    public void afterMaintenance(String runId) { if(config.isEnabled()) runBatch(runId); }

    /**
     * 同一批次先读完整本地状态，损坏时禁止采集与清理。文件锁防止同目录多进程并发写索引。
     * runId非空表示每日维护；仅该路径产生全量和执行每日过期清理。
     */
    void runBatch(String runId) {
        if(!config.isEnabled())return;
        boolean acquired=false, completed=false;
        long started=System.nanoTime();
        try {
            acquired=lock.tryLock(runId==null?0:60,TimeUnit.SECONDS);
            if(!acquired){if(runId!=null)log.error("等待备份互斥超时，本次全量未执行");return;}
            log.info("数据库备份批次开始 mode={}, runId={}",runId==null?"BINLOG_SCAN":"DAILY_FULL",runId);
            Path root=tools.root();
            try(FileChannel channel=FileChannel.open(root.resolve("state/writer.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                    FileLock fileLock=channel.tryLock()) {
                if(fileLock==null)throw new IOException("另一个协调器正在使用该备份目录");
                BackupStateStore states=new BackupStateStore(root,mapper,config.getBucket()+"|"+store.base());
                BackupStateStore.State full=states.load("full"), binlog=states.load("binlog");
                log.info("本地备份状态加载完成 fullRecords={}, binlogRecords={}, cycles={}",full.files.size(),binlog.files.size(),binlog.cycles.size());
                if(runId!=null) createFull(runId,root,states,full,binlog);
                /* A2/A4/A6状态保存失败向外抛出，中止整批，不能继续生成覆盖未落盘的新版本。 */
                process("binlog",states,binlog);
                scanBinlogs(root,states,binlog);
                process("binlog",states,binlog);
                process("full",states,full);
                if(runId!=null) {
                    try { cleanupRemote(full); } catch(Exception e){log.error("远程备份清理未完成，下次维护重试",e);}
                    try { cleanupLocal(root,states,full,binlog); } catch(Exception e){log.error("本地备份清理未完成，禁止忽略错误继续删除",e);}
                }
                log.info("备份状态汇总 fullPending={}, binlogPending={}",full.files.values().stream().filter(e->e.phase!=Phase.COMPLETED).count(),binlog.files.values().stream().filter(e->e.phase!=Phase.COMPLETED).count());
                completed=true;
            }
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            log.error("备份批次失败，下一周期从持久状态恢复",e);
        } finally {
            if(acquired) {
                log.info("数据库备份批次结束 mode={}, runId={}, processingFinished={}, elapsedMs={}",runId==null?"BINLOG_SCAN":"DAILY_FULL",runId,completed,elapsedMs(started));
                lock.unlock();
            }
        }
    }

    /**
     * 全量A1/A2：原有一致性快照及恢复坐标不变，只有成功导出才记录轮转边界。
     * 日志暂停仅覆盖导出与轮转，finally恢复；B1产生的未登记文件在每日清理时回收。
     */
    private void createFull(String runId,Path root,BackupStateStore states,BackupStateStore.State full,
            BackupStateStore.State binlog) throws Exception {
        long started=System.nanoTime();
        log.info("全量备份预检开始 runId={}, database={}",runId,config.getDatabase());
        if(!"1".equals(tools.query("SELECT @@log_bin")))throw new IOException("MySQL未开启binlog");
        if(!"0".equals(tools.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+config.getDatabase()+"' AND table_type='BASE TABLE' AND engine<>'InnoDB'")))
            throw new IOException("存在非InnoDB表，不能保证一致性快照");
        String uuid=serverUuid(), day=today().toString(), id=Instant.now().toEpochMilli()+"-"+UUID.randomUUID();
        Path directory=root.resolve("full").resolve(day).resolve(id); Files.createDirectories(directory);
        Entry entry=new Entry(); entry.type="FULL"; entry.id=id; entry.serverUuid=uuid; entry.backupDate=day;
        entry.retainThroughDate=day; entry.fileName="database.sql.gz"; entry.database=config.getDatabase();
        entry.runId=runId; entry.startedAt=Instant.now().toString(); entry.closed=true; entry.revision=1;
        entry.mysqlVersion=tools.query("SELECT VERSION()");
        log.info("全量备份导出开始 runId={}, backupId={}, backupDate={}, serverUuid={}",runId,id,day,uuid);
        Path raw=directory.resolve("database.sql.writing");
        try {
            control("pause",runId);
            log.info("日志消费已暂停，开始执行数据库快照导出 runId={}, backupId={}",runId,id);
            tools.dump(raw);
            String[] position=position(raw); entry.binlogStartFile=position[0]; entry.binlogStartPosition=Long.parseLong(position[1]);
            tools.query("FLUSH BINARY LOGS"); entry.afterDumpBinlog=lastBinlog();
            entry.dumpCompletedAt=Instant.now().toString();
            log.info("快照导出与binlog轮转完成 backupId={}, binlogStartFile={}, binlogStartPosition={}, afterDumpBinlog={}, elapsedMs={}",id,entry.binlogStartFile,entry.binlogStartPosition,entry.afterDumpBinlog,elapsedMs(started));
            recordCycle(binlog,uuid,entry.binlogStartFile,day); states.save("binlog",binlog);
        } finally {
            try {control("resume",runId);}catch(Exception e){log.error("恢复日志消费失败，等待租约自动到期",e);}
        }
        Path compressed=directory.resolve("database.sql.gz");
        try(InputStream input=Files.newInputStream(raw);OutputStream output=new GZIPOutputStream(Files.newOutputStream(compressed))) {input.transferTo(output);}
        Files.delete(raw); entry.localPath=compressed.toAbsolutePath().toString(); entry.sizeBytes=Files.size(compressed);
        entry.sha256=store.sha256(compressed); entry.phase=Phase.PENDING_UPLOAD;
        entry.objectKey=store.base()+"full/"+day+"/"+id+"/database.sql.gz";
        full.files.put(entry.objectKey,entry); states.save("full",full);
        log.info("全量备份本地文件已登记 backupId={}, backupDate={}, objectKey={}, sizeBytes={}, phase={}, elapsedMs={}",id,day,entry.objectKey,entry.sizeBytes,entry.phase,elapsedMs(started));
    }

    /**
     * 解析工具写入的真实恢复坐标，不能把导出后轮转位置当作全量起点。
     */
    static String[] position(Path raw)throws IOException {
        try(BufferedReader reader=Files.newBufferedReader(raw)){String line;while((line=reader.readLine())!=null){Matcher match=POSITION.matcher(line);if(match.find())return new String[]{match.group(1),match.group(2)};}}
        throw new IOException("导出文件缺少binlog快照坐标");
    }

    /**
     * 只在每日轮转完成后切周期，漏维护时前一周期保持开放。成功确认下一边界后延后旧周期清理参考。
     * 不更改已有文件backupDate/path，旧周期的COMPLETED记录仅需幂等更新索引信息。
     */
    static void recordCycle(BackupStateStore.State state,String uuid,String startFile,String day) {
        for(Cycle cycle:state.cycles) if(uuid.equals(cycle.serverUuid)&&startFile.equals(cycle.startFile))return;
        for(Cycle cycle:state.cycles) if(uuid.equals(cycle.serverUuid)&&cycle.endDate==null)cycle.endDate=day;
        Cycle cycle=new Cycle();cycle.serverUuid=uuid;cycle.startFile=startFile;cycle.backupDate=day;state.cycles.add(cycle);
        for(Entry entry:state.files.values()) {
            Cycle belonging=cycleFor(state,entry.serverUuid,entry.fileName);
            if(belonging!=null&&entry.backupDate!=null&&belonging.endDate!=null
                    &&!belonging.endDate.equals(entry.retainThroughDate)) {
                entry.retainThroughDate=belonging.endDate;
                if(entry.phase==Phase.COMPLETED)entry.phase=Phase.PENDING_INDEX;
            }
        }
    }

    /**
     * 从同实例数字序号边界归属周期，首次启用以前没有边界的文件保持未知。
     */
    static Cycle cycleFor(BackupStateStore.State state,String uuid,String file) {
        Cycle found=null;
        for(Cycle cycle:state.cycles) {
            Integer comparison=BackupInventory.compareFiles(file,cycle.startFile);
            if(!Objects.equals(uuid,cycle.serverUuid)||comparison==null||comparison<0)continue;
            if(found==null||BackupInventory.compareFiles(cycle.startFile,found.startFile)>0)found=cycle;
        }
        return found;
    }

    /**
     * 扫描源文件与本地状态：不变跳过，增长生成新稳定副本，关闭只改变最终属性。
     * 未完成版本不被新采集覆盖；归属过期且周期已闭合的新发现文件不再次上传。
     */
    private void scanBinlogs(Path root,BackupStateStore states,BackupStateStore.State state)throws Exception {
        long started=System.nanoTime();
        log.info("binlog扫描开始");
        int captured=0, unchanged=0, deferred=0, closedUpdates=0, skippedUnknown=0;
        String uuid=serverUuid(), output=tools.query("SHOW BINARY LOGS");
        if(output.isBlank())throw new IOException("binlog未开启或无文件");
        String[] rows=output.split("\\R");Set<String> observed=new HashSet<>();
        for(int i=0;i<rows.length;i++) {
            String[] row=rows[i].split("\\t");String name=row[0];
            if(!name.matches("[a-zA-Z0-9_.-]+")||name.contains(".."))throw new IOException("源binlog名称不合法");
            long sourceSize=Long.parseLong(row[1]); boolean closed=i<rows.length-1; observed.add(name);
            Entry previous=state.files.values().stream().filter(e->uuid.equals(e.serverUuid)&&name.equals(e.fileName)).findFirst().orElse(null);
            Cycle cycle=cycleFor(state,uuid,name);
            /*
             * 只保存已确定所属日期的binlog。首次边界之前的文件直接跳过，不采集副本或登记记录；
             * 旧版未知记录也停止处理，不再上传unassigned对象，已存在的远程文件不在此处删除。
             */
            if(previous!=null&&previous.backupDate==null||previous==null&&cycle==null) {
                skippedUnknown++;
                log.debug("跳过无日期归属的binlog file={}, serverUuid={}",name,uuid);
                continue;
            }
            if(previous!=null&&previous.phase!=Phase.COMPLETED){deferred++;continue;}
            if(previous!=null&&previous.sourceSize==sourceSize) {
                if(previous.closed!=closed){previous.closed=closed;previous.revision++;previous.phase=Phase.PENDING_INDEX;states.save("binlog",state);closedUpdates++;}
                unchanged++;continue;
            }
            Entry entry=previous==null?new Entry():previous.remoteCopy();
            if(previous==null){entry.type="BINLOG";entry.id=uuid+"/"+name;entry.serverUuid=uuid;entry.fileName=name;
                entry.backupDate=cycle.backupDate;entry.startedAt=Instant.now().toString();
                entry.objectKey=store.base()+"binlog/"+uuid+"/"+entry.backupDate+"/"+name;}
            entry.retainThroughDate=cycle==null?previous.retainThroughDate:cycle.endDate;entry.closed=closed;
            if(BackupRetention.expired(entry,cutoff())){unchanged++;continue;}
            log.info("binlog采集开始 file={}, backupDate={}, sourceSize={}, change={}",name,entry.backupDate,sourceSize,previous==null?"NEW":"GROWN");
            Path directory=root.resolve("binlog").resolve(uuid).resolve(entry.backupDate);
            Files.createDirectories(directory);Path capture=Files.createTempDirectory(directory,"capture-");
            Path source=tools.binlog(name,capture);entry.revision=previous==null?1:previous.revision+1;
            Path stable=directory.resolve(name+"-"+UUID.randomUUID()+".snapshot");Files.move(source,stable,StandardCopyOption.ATOMIC_MOVE);
            entry.localPath=stable.toAbsolutePath().toString();entry.sizeBytes=Files.size(stable);entry.sourceSize=sourceSize;
            entry.sha256=store.sha256(stable);entry.phase=Phase.PENDING_UPLOAD;entry.lastError=null;
            state.files.put(entry.objectKey,entry);states.save("binlog",state);captured++;
            log.info("binlog稳定副本已登记 file={}, backupDate={}, sizeBytes={}, revision={}, phase={}",name,entry.backupDate,entry.sizeBytes,entry.revision,entry.phase);
        }
        for(Entry entry:state.files.values())if(uuid.equals(entry.serverUuid)&&entry.backupDate!=null&&entry.phase!=Phase.COMPLETED&&!observed.contains(entry.fileName)) {
            entry.lastError="源binlog已消失，本地尚未完成上传，请检查恢复链";log.error("{}：{}",entry.lastError,entry.fileName);
        }
        states.save("binlog",state);
        log.info("binlog扫描完成 serverUuid={}, sourceFiles={}, captured={}, unchanged={}, deferred={}, closedUpdates={}, skippedUnknown={}, elapsedMs={}",uuid,rows.length,captured,unchanged,deferred,closedUpdates,skippedUnknown,elapsedMs(started));
    }

    /**
     * A3至A6统一状态机：仅外部操作失败可继续其他文件；状态落盘失败必须中止批次。
     * B3重复同名上传，B5直接重复upsert，不另查某个版本是否已经更新成功。
     */
    void process(String type,BackupStateStore states,BackupStateStore.State state)throws Exception {
        BackupInventory index=null;
        for(Entry entry:state.files.values()) {
            /* 兼容旧版未知日期记录：保留现有文件及状态，不再推进上传或写入云端索引。 */
            if("BINLOG".equals(entry.type)&&entry.backupDate==null)continue;
            if(entry.phase==Phase.COMPLETED){removeCompletedCopy(states,entry);continue;}
            if(BackupRetention.expired(entry,cutoff()))continue;
            if(entry.phase==Phase.PENDING_UPLOAD) {
                long uploadStarted=System.nanoTime();
                log.info("备份文件上传开始 type={}, id={}, backupDate={}, objectKey={}, sizeBytes={}, revision={}",type,entry.id,entry.backupDate,entry.objectKey,entry.sizeBytes,entry.revision);
                /* 旧状态缺少摘要时先补齐并落盘；状态写失败不能被当作网络重试继续执行。 */
                if(entry.sha256==null&&entry.localPath!=null){entry.sha256=store.sha256(states.ownedPath(entry.localPath));states.save(type,state);}
                try {
                    if(entry.localPath==null)throw new IOException("待上传副本缺失");Path file=states.ownedPath(entry.localPath);
                    if(Files.size(file)!=entry.sizeBytes)throw new IOException("待上传副本大小已改变");
                    if(!entry.sha256.equals(store.sha256(file)))throw new IOException("待上传副本校验值已改变");
                    String sha=store.upload(entry.objectKey,file,Map.of("backup-date",entry.backupDate,"backup-type",entry.type));
                    if(!entry.sha256.equals(sha))throw new IOException("上传文件校验不一致");
                } catch(Exception e){entry.lastError="文件上传失败："+e.getClass().getSimpleName();states.save(type,state);log.error("备份文件待重试：{}",entry.objectKey,e);continue;}
                entry.phase=Phase.PENDING_INDEX;entry.uploadedAt=Instant.now().toString();entry.lastError=null;states.save(type,state);
                log.info("备份文件上传完成 type={}, id={}, objectKey={}, phase={}, elapsedMs={}",type,entry.id,entry.objectKey,entry.phase,elapsedMs(uploadStarted));
            }
            long indexStarted=System.nanoTime();
            log.info("备份索引更新开始 type={}, objectKey={}, revision={}",type,entry.objectKey,entry.revision);
            try {
                if("full".equals(type)) ensureChain(entry);
                if(index==null)index=store.inventory(type);
                index.files.put(entry.objectKey,entry.remoteCopy());store.inventory(type,index);
            } catch(Exception e){entry.lastError="远程索引更新失败："+e.getClass().getSimpleName();states.save(type,state);log.error("备份索引待重试：{}",entry.objectKey,e);continue;}
            entry.phase=Phase.COMPLETED;entry.lastError=null;states.save(type,state);
            log.info("备份索引更新完成 type={}, id={}, objectKey={}, revision={}, phase={}, elapsedMs={}",type,entry.id,entry.objectKey,entry.revision,entry.phase,elapsedMs(indexStarted));
            removeCompletedCopy(states,entry);
        }
    }

    /**
     * 保留原发布约束：全量只有在快照起点至已采集末端的binlog连续、且覆盖导出后文件时才能发布。
     * 使用统一索引代替OSS逐文件列举；缺口保留PENDING_INDEX，下周期按同一状态重新检查即可。
     */
    private void ensureChain(Entry full)throws Exception {
        if(full.binlogStartFile==null)throw new IOException("全量缺少恢复起点");
        SortedSet<String> names=new TreeSet<>((a,b)->{Integer order=BackupInventory.compareFiles(a,b);return order==null?a.compareTo(b):order;});
        for(Entry entry:store.inventory("binlog").files.values()) {
            Integer order=BackupInventory.compareFiles(entry.fileName,full.binlogStartFile);
            if(Objects.equals(full.serverUuid,entry.serverUuid)&&order!=null&&order>=0) {
                if(entry.fileName.equals(full.binlogStartFile)&&entry.sizeBytes<full.binlogStartPosition)
                    throw new IOException("快照起点binlog尚未覆盖恢复位置");
                names.add(entry.fileName);
            }
        }
        if(names.isEmpty()||!names.first().equals(full.binlogStartFile)
                ||full.afterDumpBinlog!=null&&!names.contains(full.afterDumpBinlog))throw new IOException("快照所需binlog未上传，暂不发布全量索引");
        java.math.BigInteger expected=new java.math.BigInteger(full.binlogStartFile.substring(full.binlogStartFile.lastIndexOf('.')+1));
        for(String name:names) {
            java.math.BigInteger actual=new java.math.BigInteger(name.substring(name.lastIndexOf('.')+1));
            if(!actual.equals(expected))throw new IOException("binlog恢复链存在缺口");
            expected=expected.add(java.math.BigInteger.ONE);
        }
    }

    /**
     * 完成后仅删除本地大副本，状态记录仍保留，失败在下一批或每日清理重试。
     */
    private void removeCompletedCopy(BackupStateStore states,Entry entry) {
        if(entry.localPath==null)return;
        try{if(Files.deleteIfExists(states.ownedPath(entry.localPath)))log.debug("已完成备份的本地副本已删除 id={}",entry.id);}catch(IOException e){log.warn("本地已完成副本清理失败：{}",entry.id,e);}
    }

    /**
     * 远程清理先读两份有效索引，按保守日期及恢复依赖逐个删除；成功或不存在才移除记录。
     * 删除后索引保存失败保持旧索引，下一维护重复删除即可；不引入删除状态文件。
     */
    void cleanupRemote(BackupStateStore.State localFull)throws Exception {
        long started=System.nanoTime();
        log.info("远程备份过期清理开始 cutoff={}",cutoff());
        BackupInventory full=store.inventory("full"), binlog=store.inventory("binlog");LocalDate cutoff=cutoff();
        List<Entry> dependencies=new ArrayList<>(full.files.values());dependencies.addAll(localFull.files.values());
        Set<String> anchors=BackupRetention.fullAnchors(dependencies,cutoff);
        for(String type:List.of("full","binlog")) {
            BackupInventory index=type.equals("full")?full:binlog;
            Iterator<Entry> iterator=index.files.values().iterator();boolean changed=false;int deleted=0,failed=0,original=index.files.size();
            while(iterator.hasNext()) {
                Entry entry=iterator.next();
                if(!BackupRetention.expired(entry,cutoff)||type.equals("full")&&anchors.contains(entry.objectKey)
                        ||type.equals("binlog")&&BackupRetention.neededByFull(entry,dependencies,cutoff))continue;
                try {store.delete(entry.objectKey);iterator.remove();changed=true;deleted++;log.info("远程过期备份文件已删除 type={}, objectKey={}, backupDate={}, retainThroughDate={}",type,entry.objectKey,entry.backupDate,entry.retainThroughDate);}
                catch(Exception e){failed++;log.warn("过期备份删除失败，保留索引：{}",entry.objectKey,e);}
            }
            if(changed)store.inventory(type,index);
            log.info("远程备份清理索引已确认 type={}, original={}, deleted={}, failed={}, retained={}",type,original,deleted,failed,index.files.size());
        }
        log.info("远程备份过期清理完成 elapsedMs={}",elapsedMs(started));
    }

    /**
     * 每日清理本地记录和孤立文件；先加载的两份状态可信才执行，远程读取失败不猜测恢复依赖。
     * 数据删除后才移除过期状态；Files.walk不跟随链接，state及其bak永远不参与数据扫描。
     */
    void cleanupLocal(Path root,BackupStateStore states,BackupStateStore.State full,BackupStateStore.State binlog)throws Exception {
        long started=System.nanoTime();int originalRecords=full.files.size()+binlog.files.size();
        log.info("本地备份清理开始 cutoff={}, records={}",cutoff(),originalRecords);
        BackupInventory remoteFull=store.inventory("full");List<Entry> dependencies=new ArrayList<>(remoteFull.files.values());dependencies.addAll(full.files.values());
        Set<String> anchors=BackupRetention.fullAnchors(dependencies,cutoff());
        for(String type:List.of("full","binlog")) {
            BackupStateStore.State state=type.equals("full")?full:binlog;Iterator<Entry> iterator=state.files.values().iterator();
            while(iterator.hasNext()) {
                Entry entry=iterator.next();boolean expired=BackupRetention.expired(entry,cutoff())
                        &&!(type.equals("full")?anchors.contains(entry.objectKey):BackupRetention.neededByFull(entry,dependencies,cutoff()));
                if(!expired&&entry.phase!=Phase.COMPLETED)continue;
                try {
                    if(entry.localPath!=null)Files.deleteIfExists(states.ownedPath(entry.localPath));
                    if(expired){if(entry.phase!=Phase.COMPLETED)log.error("超过保留窗口仍未完成备份：{}",entry.objectKey);iterator.remove();log.debug("本地过期备份记录已移除 type={}, id={}",type,entry.id);}
                }catch(IOException e){log.warn("本地备份删除失败，保留状态：{}",entry.id,e);}
            }
            states.save(type,state);
        }
        Set<Path> referenced=new HashSet<>();
        for(BackupStateStore.State state:List.of(full,binlog))for(Entry entry:state.files.values())if(entry.localPath!=null)referenced.add(states.ownedPath(entry.localPath));
        for(String type:List.of("full","binlog")) {
            Path directory=root.resolve(type);if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))continue;
            try(java.util.stream.Stream<Path> paths=Files.walk(directory)) {
                List<Path> ordered=paths.sorted(Comparator.reverseOrder()).toList();
                for(Path path:ordered) {
                    if(path.equals(directory))continue;
                    if(Files.isSymbolicLink(path)){Files.deleteIfExists(path);continue;}
                    if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) {
                        try(java.util.stream.Stream<Path> children=Files.list(path)){if(children.findAny().isEmpty())Files.deleteIfExists(path);}
                    } else if(!referenced.contains(path.toAbsolutePath().normalize()))Files.deleteIfExists(states.ownedPath(path.toString()));
                }
            }
        }
        pruneCycles(binlog);states.save("binlog",binlog);
        /*
         * 有效主状态已读写完成后，旧单文件状态与中断工具/原子写遗留临时文件均已失去引用。
         * 只保留两份主状态、上一版备份和当前持有的锁，不触及MySQL原始日志目录。
         */
        try(java.util.stream.Stream<Path> paths=Files.list(root.resolve("state"))) {
            Set<String> keep=Set.of("full.json","binlog.json","full.json.bak","binlog.json.bak","writer.lock");
            for(Path path:(Iterable<Path>)paths::iterator)if(!keep.contains(path.getFileName().toString())
                    &&!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(path);
        }
        log.info("本地备份清理完成 recordsBefore={}, recordsAfter={}, elapsedMs={}",originalRecords,full.files.size()+binlog.files.size(),elapsedMs(started));
    }

    /**
     * 保留仍有记录依赖的周期及窗口前一个边界，清理无用历史信息而不丢失保守判断依据。
     */
    private void pruneCycles(BackupStateStore.State state) {
        LocalDate floor=cutoff();for(Entry entry:state.files.values())if(entry.backupDate!=null&&LocalDate.parse(entry.backupDate).isBefore(floor))floor=LocalDate.parse(entry.backupDate);
        Map<String,Cycle> first=new HashMap<>(),before=new HashMap<>();for(Cycle cycle:state.cycles){
            Cycle earliest=first.get(cycle.serverUuid);if(earliest==null||cycle.backupDate.compareTo(earliest.backupDate)<0)first.put(cycle.serverUuid,cycle);
            if(LocalDate.parse(cycle.backupDate).isBefore(floor)){
            Cycle old=before.get(cycle.serverUuid);if(old==null||cycle.backupDate.compareTo(old.backupDate)>0)before.put(cycle.serverUuid,cycle);
        }}
        /* 保留最早边界，源库尚未清除的过期旧文件仍可判为已过期，不会重新变成未知并补传。 */
        LocalDate boundary=floor;state.cycles.removeIf(c->LocalDate.parse(c.backupDate).isBefore(boundary)&&before.get(c.serverUuid)!=c&&first.get(c.serverUuid)!=c);
    }

    /**
     * 暂停/恢复使用既有内部密钥，重复恢复幂等，暂停失败或响应丢失也执行finally恢复。
     */
    private void control(String action,String runId) {
        HttpHeaders headers=new HttpHeaders();headers.set("X-Maintenance-Secret",secret);
        java.net.URI uri=UriComponentsBuilder.fromHttpUrl(config.getMonitorUrl()).path("/internal/maintenance/log-consumer/"+action)
                .queryParam("runId",runId).queryParam("seconds",config.getTimeoutSeconds()+120).build().encode().toUri();
        Map<?,?> response=rest.exchange(uri,HttpMethod.POST,new HttpEntity<>(headers),Map.class).getBody();
        if(response==null||!Integer.valueOf(200).equals(response.get("code")))throw new IllegalStateException("日志消费控制失败");
        log.info("备份日志消费控制完成 action={}, runId={}",action,runId);
    }

    /**
     * 用单调时钟统计备份阶段处理耗时，避免服务器校时导致负数；仅用于日志，不参与日期归属。
     */
    private static long elapsedMs(long started){return (System.nanoTime()-started)/1_000_000;}

    /**
     * 实例UUID参与固定对象路径，避免不同MySQL服务器的相同文件名碰撞。
     */
    private String serverUuid()throws Exception {String uuid=tools.query("SELECT @@server_uuid");if(!uuid.matches("[a-fA-F0-9-]{36}"))throw new IOException("服务器UUID不合法");return uuid;}
    /**
     * 导出后活动文件只作记录，恢复起点仍由dump提供。
     */
    private String lastBinlog()throws Exception {String[] lines=tools.query("SHOW BINARY LOGS").split("\\R");return lines[lines.length-1].split("\\t")[0];}
    /**
     * 日期归属和窗口判断统一使用北京时间，不依赖系统默认时区。
     */
    private LocalDate today(){return LocalDate.now(ZONE);}
    /**
     * 三十天包含今天，删除严格早于窗口首日，未知边界另行保守保护。
     */
    private LocalDate cutoff(){return today().minusDays(config.getRetentionDays()-1L);}
    /**
     * 关闭后台线程；已落盘阶段由下次启动恢复，不清空未完成记录。
     */
    @PreDestroy public void close(){uploads.shutdownNow();}
}
