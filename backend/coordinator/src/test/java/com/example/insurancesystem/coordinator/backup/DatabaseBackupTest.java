package com.example.insurancesystem.coordinator.backup;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 备份只使用假工具和假HTTP，验证自动建目录、坐标和失败恢复，不访问真实数据库或OSS。 */
class DatabaseBackupTest {
    @TempDir Path dir;
    /** 目录不存在时自动创建全量、binlog和状态子目录。 */
    @Test void createsDirectoriesOnDemand()throws Exception{
        Path client=dir.resolve("client.cnf");Files.writeString(client,"[client]");BackupProperties p=new BackupProperties();p.setDefaultsFile(client.toString());p.setRoot(dir.resolve("missing/nested").toString());
        Path root=new MySqlBackupTools(p).root();assertTrue(Files.isDirectory(root.resolve("full")));assertTrue(Files.isDirectory(root.resolve("binlog")));assertTrue(Files.isDirectory(root.resolve("state")));
    }
    /** 同时识别新旧MySQL工具的恢复坐标，缺失时拒绝发布备份。 */
    @Test void extractsSnapshotPosition()throws Exception{
        Path file=dir.resolve("dump.sql");Files.writeString(file,"-- CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE='mysql-bin.000123', SOURCE_LOG_POS=157;\n");assertArrayEquals(new String[]{"mysql-bin.000123","157"},DatabaseBackupService.position(file));
        Files.writeString(file,"-- CHANGE MASTER TO MASTER_LOG_FILE='bin.000001', MASTER_LOG_POS=4;\n");assertEquals("4",DatabaseBackupService.position(file)[1]);Files.writeString(file,"SELECT 1;");assertThrows(java.io.IOException.class,()->DatabaseBackupService.position(file));
    }
    /** 导出失败仍恢复日志写入，不上传或清理远端任何对象。 */
    @Test void resumesConsumerAfterDumpFailure()throws Exception{
        BackupProperties p=new BackupProperties();p.setEnabled(true);p.setRoot(dir.toString());MySqlBackupTools tools=mock(MySqlBackupTools.class);when(tools.root()).thenReturn(dir);when(tools.query("SELECT @@log_bin")).thenReturn("1");when(tools.query(contains("COUNT(*)"))).thenReturn("0");when(tools.query("SELECT @@server_uuid")).thenReturn("12345678-1234-1234-1234-123456789abc");when(tools.query("SELECT VERSION()")).thenReturn("8.0.40");doThrow(new java.io.IOException("模拟导出失败")).when(tools).dump(any());
        RestTemplate rest=mock(RestTemplate.class);when(rest.exchange(any(java.net.URI.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of("code",200)));
        RestTemplateBuilder builder=mock(RestTemplateBuilder.class,RETURNS_SELF);when(builder.build()).thenReturn(rest);BackupObjectStore store=mock(BackupObjectStore.class);
        DatabaseBackupService service=new DatabaseBackupService(p,tools,store,new ObjectMapper(),builder,new CoordinatorProperties());try{service.afterMaintenance("run-1");verify(rest).exchange(argThat((java.net.URI uri)->uri.getPath().endsWith("/pause")),eq(HttpMethod.POST),any(HttpEntity.class),eq(Map.class));verify(rest).exchange(argThat((java.net.URI uri)->uri.getPath().endsWith("/resume")),eq(HttpMethod.POST),any(HttpEntity.class),eq(Map.class));verifyNoInteractions(store);}finally{service.close();}
    }
    /** OSS键必须保持在当前实例专属前缀内，避免清理误删业务文件。 */
    @Test void refusesUnsafeOssPrefix(){BackupProperties p=new BackupProperties();p.setPrefix("../business");assertThrows(IllegalArgumentException.class,()->new BackupObjectStore(p,new ObjectMapper()).base());}
    /** 过期清理保留窗口前锚点及其起点日志，不因文件超过30天就破坏恢复链。 */
    @Test void retentionProtectsAnchorAndDependentBinlogs()throws Exception {
        BackupProperties p=new BackupProperties();BackupObjectStore store=mock(BackupObjectStore.class);when(store.base()).thenReturn("backup/prod/");
        String recent="backup/prod/full/recent/manifest.json",anchor="backup/prod/full/anchor/manifest.json",old="backup/prod/full/old/manifest.json";
        when(store.list("backup/prod/full/")).thenReturn(List.of(recent,anchor,old));
        for(String key:List.of(recent,anchor,old)){long days=key.equals(recent)?1:key.equals(anchor)?31:40;String start=key.equals(recent)?"bin.000012":key.equals(anchor)?"bin.000010":"bin.000009";when(store.read(key)).thenReturn(new LinkedHashMap<>(Map.of("serverUuid","uuid","startedAt",java.time.Instant.now().minus(java.time.Duration.ofDays(days)).toString(),"binlogStartFile",start,"objectKey",key+".gz")));}
        String expired="backup/prod/binlog/uuid/bin.000009.json",protectedKey="backup/prod/binlog/uuid/bin.000010.json";when(store.list("backup/prod/binlog/")).thenReturn(List.of(expired,protectedKey));
        for(String key:List.of(expired,protectedKey)){when(store.read(key)).thenReturn(new LinkedHashMap<>(Map.of("serverUuid","uuid","file",key.equals(expired)?"bin.000009":"bin.000010","objectKey",key.substring(0,key.length()-5),"closed",true,"uploadedAt",java.time.Instant.now().minus(java.time.Duration.ofDays(35)).toString())));}
        RestTemplateBuilder builder=mock(RestTemplateBuilder.class,RETURNS_SELF);when(builder.build()).thenReturn(mock(RestTemplate.class));DatabaseBackupService service=new DatabaseBackupService(p,mock(MySqlBackupTools.class),store,new ObjectMapper(),builder,new CoordinatorProperties());
        try{org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"cleanup");verify(store).delete(old);verify(store).delete(expired);verify(store,never()).delete(anchor);verify(store,never()).delete(protectedKey);}finally{service.close();}
    }
}
