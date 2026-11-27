package com.example.insurancesystem.coordinator.backup;

import com.example.insurancesystem.backup.BackupInventory;
import com.example.insurancesystem.backup.BackupInventory.*;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用本地临时目录及模拟MySQL/OSS验证状态推进、扫描和清理，测试不会连接生产或执行真实SQL。
 */
class DatabaseBackupTest {
    @TempDir Path dir;
    final ObjectMapper mapper=new ObjectMapper();
    final String base="database-backup/prod/",uuid="12345678-1234-1234-1234-123456789abc";
    BackupProperties config;MySqlBackupTools tools;BackupObjectStore store;RestTemplate rest;
    DatabaseBackupService service;BackupStateStore states;
    Map<String,BackupInventory> remote;

    /**
     * 模拟对象存储按值读写，避免测试错误地把内存引用变化当成已经成功覆盖云端索引。
     */
    @BeforeEach void setup()throws Exception {
        Files.createDirectories(dir.resolve("state"));Files.createDirectories(dir.resolve("full"));Files.createDirectories(dir.resolve("binlog"));
        config=new BackupProperties();config.setEnabled(true);config.setRoot(dir.toString());
        tools=mock(MySqlBackupTools.class);when(tools.root()).thenReturn(dir);when(tools.query("SELECT @@server_uuid")).thenReturn(uuid);
        store=mock(BackupObjectStore.class);when(store.base()).thenReturn(base);
        when(store.sha256(any())).thenAnswer(c->HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(c.getArgument(0)))));
        when(store.upload(anyString(),any(),anyMap())).thenAnswer(c->store.sha256(c.getArgument(1)));
        remote=new HashMap<>();remote.put("full",new BackupInventory());remote.put("binlog",new BackupInventory());
        when(store.inventory(anyString())).thenAnswer(c->mapper.readValue(mapper.writeValueAsBytes(remote.get(c.getArgument(0))),BackupInventory.class));
        doAnswer(c->{remote.put(c.getArgument(0),mapper.readValue(mapper.writeValueAsBytes(c.getArgument(1)),BackupInventory.class));return null;}).when(store).inventory(anyString(),any(BackupInventory.class));
        rest=mock(RestTemplate.class);when(rest.exchange(any(java.net.URI.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of("code",200)));
        RestTemplateBuilder builder=mock(RestTemplateBuilder.class,RETURNS_SELF);when(builder.build()).thenReturn(rest);
        service=new DatabaseBackupService(config,tools,store,mapper,builder,new CoordinatorProperties());
        states=new BackupStateStore(dir,mapper,config.getBucket()+"|"+base);
    }

    /**
     * 每个测试释放后台执行器，不遗留后台任务影响其他测试。
     */
    @AfterEach void close(){service.close();}

    /**
     * 构造已生成稳定副本的待上传日志，所属日期与对象路径在登记时固定。
     */
    Entry pending(String date,String name)throws Exception {
        Entry e=new Entry();e.id=uuid+"/"+name;e.type="BINLOG";e.serverUuid=uuid;e.fileName=name;e.backupDate=date;
        e.objectKey=base+"binlog/"+uuid+"/"+date+"/"+name;e.phase=Phase.PENDING_UPLOAD;e.revision=1;
        Path path=dir.resolve("binlog").resolve(name+".snapshot");Files.writeString(path,name);e.localPath=path.toString();e.sizeBytes=Files.size(path);e.sha256=store.sha256(path);return e;
    }

    /**
     * 路径不存在时工具自动建立全部暂存目录，不要求运维预先逐级创建。
     */
    @Test void createsDirectoriesOnDemand()throws Exception {
        Path client=dir.resolve("client.cnf");Files.writeString(client,"[client]");config.setDefaultsFile(client.toString());config.setRoot(dir.resolve("missing/nested").toString());
        Path root=new MySqlBackupTools(config).root();for(String type:List.of("full","binlog","state"))assertTrue(Files.isDirectory(root.resolve(type)));
    }

    /**
     * 兼容MySQL两种坐标语法，缺失恢复坐标拒绝发布。
     */
    @Test void extractsSnapshotPosition()throws Exception {
        Path path=dir.resolve("dump.sql");Files.writeString(path,"-- CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE='mysql-bin.000123', SOURCE_LOG_POS=157;\n");
        assertArrayEquals(new String[]{"mysql-bin.000123","157"},DatabaseBackupService.position(path));
        Files.writeString(path,"-- CHANGE MASTER TO MASTER_LOG_FILE='bin.000001', MASTER_LOG_POS=4;\n");assertEquals("4",DatabaseBackupService.position(path)[1]);
        Files.writeString(path,"SELECT 1;");assertThrows(IOException.class,()->DatabaseBackupService.position(path));
    }

    /**
     * 上传失败保留待上传；云端索引失败保留待更新；下一次只做当前状态需要的操作。
     */
    @Test void continuesByPersistedPhaseWithoutRecoveryBranches()throws Exception {
        BackupStateStore.State state=states.load("binlog");Entry e=pending(day(0),"bin.000001");state.files.put(e.objectKey,e);states.save("binlog",state);
        doThrow(new IOException("模拟OSS上传失败")).when(store).upload(anyString(),any(),anyMap());
        service.process("binlog",states,state);assertEquals(Phase.PENDING_UPLOAD,states.load("binlog").files.get(e.objectKey).phase);assertTrue(Files.exists(Path.of(e.localPath)));
        doAnswer(c->store.sha256(c.getArgument(1))).when(store).upload(anyString(),any(),anyMap());
        doThrow(new IOException("模拟索引失败")).when(store).inventory(eq("binlog"),any(BackupInventory.class));
        service.process("binlog",states,states.load("binlog"));assertEquals(Phase.PENDING_INDEX,states.load("binlog").files.get(e.objectKey).phase);
        clearInvocations(store);doAnswer(c->{remote.put("binlog",mapper.readValue(mapper.writeValueAsBytes(c.getArgument(1)),BackupInventory.class));return null;}).when(store).inventory(eq("binlog"),any(BackupInventory.class));
        service.process("binlog",states,states.load("binlog"));verify(store,never()).upload(anyString(),any(),anyMap());
        assertEquals(Phase.COMPLETED,states.load("binlog").files.get(e.objectKey).phase);assertFalse(Files.exists(Path.of(e.localPath)));
        assertNull(remote.get("binlog").files.get(e.objectKey).phase);assertNull(remote.get("binlog").files.get(e.objectKey).localPath);
    }

    /**
     * 已上传且索引已存在仍可重复幂等覆盖，跨日补传不会改变固定所属目录或产生重复条目。
     */
    @Test void lateUploadsKeepDatesAndIdempotentIndex()throws Exception {
        BackupStateStore.State state=states.load("binlog");Entry a=pending(day(-1),"bin.000001"),b=pending(day(0),"bin.000002");
        state.files.put(a.objectKey,a);state.files.put(b.objectKey,b);states.save("binlog",state);service.process("binlog",states,state);
        state=states.load("binlog");state.files.get(a.objectKey).phase=Phase.PENDING_INDEX;states.save("binlog",state);clearInvocations(store);
        service.process("binlog",states,state);verify(store,never()).upload(anyString(),any(),anyMap());assertEquals(2,remote.get("binlog").files.size());
        assertEquals(day(-1),remote.get("binlog").files.get(a.objectKey).backupDate);assertTrue(a.objectKey.contains("/"+day(-1)+"/"));
    }

    /**
     * 状态写入失败直接停止上传批次，不能继续处理下一个文件或删除待处理副本。
     */
    @Test void stateWriteFailureStopsBatch()throws Exception {
        BackupStateStore.State state=states.load("binlog");Entry e=pending(day(0),"bin.000001");state.files.put(e.objectKey,e);states.save("binlog",state);
        BackupStateStore failing=spy(states);doThrow(new IOException("模拟磁盘失败")).when(failing).save(anyString(),any());
        assertThrows(IOException.class,()->service.process("binlog",failing,state));assertTrue(Files.exists(Path.of(e.localPath)));verify(store,never()).inventory(eq("binlog"),any(BackupInventory.class));
    }

    /**
     * 五分钟扫描检测增长并覆盖同一对象；内容未增长时跳过，关闭只更新索引而不重新下载。
     */
    @Test void scansGrowthAndClosure()throws Exception {
        BackupStateStore.State state=states.load("binlog");DatabaseBackupService.recordCycle(state,uuid,"bin.000001",day(0));states.save("binlog",state);
        when(tools.query("SHOW BINARY LOGS")).thenReturn("bin.000001\t10");
        when(tools.binlog(anyString(),any())).thenAnswer(c->{Path path=((Path)c.getArgument(1)).resolve((String)c.getArgument(0));Files.writeString(path,UUID.randomUUID().toString());return path;});
        service.runBatch(null);service.runBatch(null);verify(tools,times(1)).binlog(anyString(),any());
        when(tools.query("SHOW BINARY LOGS")).thenReturn("bin.000001\t20");service.runBatch(null);verify(tools,times(2)).binlog(anyString(),any());
        String key=base+"binlog/"+uuid+"/"+day(0)+"/bin.000001";assertEquals(2,remote.get("binlog").files.get(key).revision);assertEquals(20,remote.get("binlog").files.get(key).sourceSize);
        when(tools.query("SHOW BINARY LOGS")).thenReturn("bin.000001\t20\nbin.000002\t4");service.runBatch(null);
        verify(tools,times(2)).binlog(eq("bin.000001"),any());assertTrue(remote.get("binlog").files.get(key).closed);assertEquals(2,remote.get("binlog").files.size());
    }

    /**
     * 漏维护不猜边界，直到下一次成功维护才闭合旧周期，删除参考延后到新边界日。
     */
    @Test void missingMaintenanceDelaysExpiration()throws Exception {
        BackupStateStore.State state=states.load("binlog");DatabaseBackupService.recordCycle(state,uuid,"bin.000001",day(-32));
        Entry e=pending(day(-32),"bin.000002");e.closed=true;e.phase=Phase.COMPLETED;state.files.put(e.objectKey,e);
        assertFalse(BackupRetention.expired(e,LocalDate.parse(day(-29))));DatabaseBackupService.recordCycle(state,uuid,"bin.000010",day(-28));
        assertEquals(day(-28),e.retainThroughDate);assertFalse(BackupRetention.expired(e,LocalDate.parse(day(-29))));
        assertEquals(day(-32),DatabaseBackupService.cycleFor(state,uuid,"bin.000009").backupDate);assertEquals(day(-28),DatabaseBackupService.cycleFor(state,uuid,"bin.000010").backupDate);
        assertEquals(Phase.PENDING_INDEX,e.phase);assertNull(DatabaseBackupService.cycleFor(state,uuid,"bin.000000"));
    }

    /**
     * 删除云端对象成功但索引写失败时，下一次重复删除并更新索引即可，保护锚点和其依赖日志。
     */
    @Test void retentionAndDeleteAreIdempotent()throws Exception {
        Entry anchor=full("anchor",day(-31),"bin.000010"),old=full("old",day(-40),"bin.000009"),recent=full("recent",day(0),"bin.000012");
        for(Entry e:List.of(anchor,old,recent))remote.get("full").files.put(e.objectKey,e);
        Entry expired=pending(day(-35),"bin.000009"),kept=pending(day(-35),"bin.000010");for(Entry e:List.of(expired,kept)){e.closed=true;e.retainThroughDate=day(-34);remote.get("binlog").files.put(e.objectKey,e.remoteCopy());}
        doThrow(new IOException("模拟删除后索引写失败")).doAnswer(c->{remote.put("full",mapper.readValue(mapper.writeValueAsBytes(c.getArgument(1)),BackupInventory.class));return null;}).when(store).inventory(eq("full"),any(BackupInventory.class));
        BackupStateStore.State local=states.load("full");assertThrows(IOException.class,()->service.cleanupRemote(local));service.cleanupRemote(local);
        verify(store,times(2)).delete(old.objectKey);verify(store).delete(expired.objectKey);verify(store,never()).delete(anchor.objectKey);verify(store,never()).delete(kept.objectKey);
        assertEquals(2,remote.get("full").files.size());assertEquals(1,remote.get("binlog").files.size());
    }

    /**
     * 主状态损坏中止整批，保留所有本地文件；有效状态下每日清除未登记文件并保留待上传副本。
     */
    @Test void cleansOrphansOnlyWithValidState()throws Exception {
        BackupStateStore.State full=states.load("full"),bin=states.load("binlog");Entry pending=pending(day(0),"bin.000001");bin.files.put(pending.objectKey,pending);states.save("binlog",bin);
        Path orphan=dir.resolve("full/orphan.sql");Files.writeString(orphan,"partial");service.cleanupLocal(dir,states,full,bin);assertFalse(Files.exists(orphan));assertTrue(Files.exists(Path.of(pending.localPath)));
        Files.writeString(orphan,"partial");Files.writeString(dir.resolve("state/full.json"),"broken");service.runBatch("maintenance");assertTrue(Files.exists(orphan));verify(tools,never()).query(anyString());
    }

    /**
     * 导出过程失败仍恢复日志消费；不会上传不完整文件或执行远程删除。
     */
    @Test void resumesConsumerAfterDumpFailure()throws Exception {
        when(tools.query("SELECT @@log_bin")).thenReturn("1");when(tools.query(contains("COUNT(*)"))).thenReturn("0");doThrow(new IOException("模拟导出失败")).when(tools).dump(any());
        service.afterMaintenance("run-1");verify(rest).exchange(argThat((java.net.URI uri)->uri.getPath().endsWith("/pause")),eq(HttpMethod.POST),any(HttpEntity.class),eq(Map.class));
        verify(rest).exchange(argThat((java.net.URI uri)->uri.getPath().endsWith("/resume")),eq(HttpMethod.POST),any(HttpEntity.class),eq(Map.class));verify(store,never()).upload(anyString(),any(),anyMap());verify(store,never()).delete(anyString());
    }

    /**
     * 全量云端发布必须等恢复链连续，缺口修复后按PENDING_INDEX继续，无需重新上传全量大文件。
     */
    @Test void publishesFullOnlyWhenChainReady()throws Exception {
        BackupStateStore.State state=states.load("full");Entry e=full("today",day(0),"bin.000001");e.phase=Phase.PENDING_INDEX;e.binlogStartPosition=4;e.afterDumpBinlog="bin.000002";state.files.put(e.objectKey,e);states.save("full",state);
        service.process("full",states,state);assertEquals(Phase.PENDING_INDEX,states.load("full").files.get(e.objectKey).phase);assertTrue(remote.get("full").files.isEmpty());
        for(String name:List.of("bin.000001","bin.000002")){Entry b=pending(day(0),name);remote.get("binlog").files.put(b.objectKey,b.remoteCopy());}
        service.process("full",states,states.load("full"));assertEquals(Phase.COMPLETED,states.load("full").files.get(e.objectKey).phase);verify(store,never()).upload(anyString(),any(),anyMap());
    }

    /**
     * 完整维护创建日期目录、真实快照坐标及每日轮转边界，随后发布两份索引并清掉本地大副本。
     */
    @Test void createsAndPublishesDailyFull()throws Exception {
        when(tools.query("SELECT @@log_bin")).thenReturn("1");when(tools.query(contains("COUNT(*)"))).thenReturn("0");
        when(tools.query("SELECT VERSION()")).thenReturn("8.0.40");when(tools.query("SHOW BINARY LOGS")).thenReturn("bin.000001\t100\nbin.000002\t4");
        doAnswer(c->{Files.writeString(c.getArgument(0),"-- CHANGE MASTER TO MASTER_LOG_FILE='bin.000001', MASTER_LOG_POS=4;\nCREATE TABLE example(id int);");return null;}).when(tools).dump(any());
        when(tools.binlog(anyString(),any())).thenAnswer(c->{Path path=((Path)c.getArgument(1)).resolve((String)c.getArgument(0));Files.writeString(path,"test-binlog");return path;});
        service.afterMaintenance("daily-run");assertEquals(1,remote.get("full").files.size());Entry full=remote.get("full").files.values().iterator().next();
        assertTrue(full.objectKey.startsWith(base+"full/"+day(0)+"/"));assertEquals("bin.000001",full.binlogStartFile);assertEquals(4,full.binlogStartPosition);assertEquals("bin.000002",full.afterDumpBinlog);
        assertEquals(2,remote.get("binlog").files.size());assertEquals(day(0),states.load("binlog").cycles.get(0).backupDate);
        Entry local=states.load("full").files.get(full.objectKey);assertEquals(Phase.COMPLETED,local.phase);assertFalse(Files.exists(Path.of(local.localPath)));
        verify(tools).query("FLUSH BINARY LOGS");verify(store,never()).json(anyString(),anyMap());
    }

    /**
     * 全量恢复锚点样例，路径始终含所属日期而非上传日期。
     */
    Entry full(String id,String date,String start) {
        Entry e=new Entry();e.type="FULL";e.id=id;e.serverUuid=uuid;e.backupDate=date;e.retainThroughDate=date;e.closed=true;e.binlogStartFile=start;e.objectKey=base+"full/"+date+"/"+id+"/database.sql.gz";return e;
    }

    /**
     * 所有测试与业务一致使用北京时间自然日。
     */
    String day(int offset){return LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(offset).toString();}
    /**
     * 首次成功维护前无日期边界时，不采集、不上传、不新增文件记录，避免产生永久未知归属备份。
     */
    @Test void skipsSourceBinlogsWithoutDateBoundary()throws Exception {
        when(tools.query("SHOW BINARY LOGS")).thenReturn("bin.000001\t10");
        service.runBatch(null);
        assertTrue(states.load("binlog").files.isEmpty());
        verify(tools,never()).binlog(anyString(),any());verify(store,never()).upload(anyString(),any(),anyMap());
    }

    /**
     * 已知边界之前的旧文件和旧版未知记录停止处理，边界内新文件仍照常采集并上传。
     * 不删除已存在的未知副本，也不重写其云端索引。
     */
    @Test void ignoresUnknownLegacyRecordsWhileUploadingKnownDates()throws Exception {
        var state=states.load("binlog");Entry legacy=pending(day(0),"bin.000001");legacy.backupDate=null;
        state.files.put(legacy.objectKey,legacy);DatabaseBackupService.recordCycle(state,uuid,"bin.000003",day(0));states.save("binlog",state);
        when(tools.query("SHOW BINARY LOGS")).thenReturn("bin.000001\t10\nbin.000002\t10\nbin.000003\t10");
        when(tools.binlog(anyString(),any())).thenAnswer(c->{Path path=((Path)c.getArgument(1)).resolve((String)c.getArgument(0));Files.writeString(path,"binlog");return path;});
        service.runBatch(null);
        verify(tools,never()).binlog(eq("bin.000001"),any());verify(tools,never()).binlog(eq("bin.000002"),any());
        verify(tools).binlog(eq("bin.000003"),any());verify(store,never()).upload(eq(legacy.objectKey),any(),anyMap());
        assertEquals(1,remote.get("binlog").files.size());assertEquals(2,states.load("binlog").files.size());
        assertTrue(Files.exists(Path.of(legacy.localPath)));
    }

}
