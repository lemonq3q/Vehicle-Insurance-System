package com.example.insurancesystem.monitor.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 模拟OSS验证目录隔离及单文件签名；不连接线上备份，也不上传或删除对象。 */
class MonitorBackupDownloadServiceTest {
    /** 拒绝越界对象和目录穿越，数据库备份外文件不能获得下载链接。 */
    @Test void rejectsTraversal(){OSS oss=mock(OSS.class);MonitorBackupDownloadService service=new MonitorBackupDownloadService("bucket","database-backup","prod"){protected OSS oss(){return oss;}};
        assertThrows(RuntimeException.class,()->MonitorBackupDownloadService.safePath("../"));assertThrows(RuntimeException.class,()->MonitorBackupDownloadService.safePath("/full/"));assertThrows(RuntimeException.class,()->service.download("business/file"));assertThrows(RuntimeException.class,()->service.download("database-backup/prod/../secret"));assertThrows(RuntimeException.class,()->service.download("database-backup/prod/full/"));verifyNoInteractions(oss);
    }
    /** 仅为选择的文件签发有效期十分钟的附件下载链接，使用原对象名。 */
    @Test void signsOnlySelectedFile()throws Exception{OSS oss=mock(OSS.class);when(oss.getObjectMetadata(anyString(),anyString())).thenReturn(new ObjectMetadata());when(oss.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(new java.net.URL("https://example.test/database.sql.gz"));MonitorBackupDownloadService service=new MonitorBackupDownloadService("bucket","database-backup","prod"){protected OSS oss(){return oss;}};
        long before=System.currentTimeMillis();Map<String,Object> result=service.download("database-backup/prod/full/id/database.sql.gz");assertEquals("database.sql.gz",result.get("name"));assertEquals("https://example.test/database.sql.gz",result.get("downloadUrl"));ArgumentCaptor<GeneratePresignedUrlRequest> request=ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);verify(oss).generatePresignedUrl(request.capture());assertEquals("database-backup/prod/full/id/database.sql.gz",request.getValue().getKey());assertTrue(request.getValue().getExpiration().getTime()>=before+599000);verify(oss).getObjectMetadata("bucket","database-backup/prod/full/id/database.sql.gz");verifyNoMoreInteractions(oss);
    }
    /**
     * 根目录从两份统一索引构造，不调用ListObjects也不读取单文件附属清单。
     */
    @Test void listsFromTwoIndexes()throws Exception {
        OSS oss=mock(OSS.class);install(oss,"full",List.of(entry("FULL","full/"+day()+"/id/database.sql.gz",day())));install(oss,"binlog",List.of(entry("BINLOG","binlog/uuid/"+day()+"/bin.000001",day())));
        Map<String,Object> result=service(oss).files("","",day(),day());assertEquals(2,((List<?>)result.get("folders")).size());assertEquals("",result.get("nextMarker"));
        verify(oss).getObject("bucket","database-backup/prod/full/index.json");verify(oss).getObject("bucket","database-backup/prod/binlog/index.json");verifyNoMoreInteractions(oss);
    }

    /**
     * 日期以backupDate为准，上传时间较晚不会改变归属；未知文件只在无日期条件时可见。
     */
    @Test void filtersByBelongingDay()throws Exception {
        OSS oss=mock(OSS.class);var known=entry("FULL","full/id/database.sql.gz",day());known.uploadedAt="2000-01-01T00:00:00Z";
        var unknown=entry("BINLOG","binlog/uuid/unassigned/bin.000001",null);install(oss,"full",List.of(known));install(oss,"binlog",List.of(unknown));
        MonitorBackupDownloadService svc=service(oss);Map<String,Object> result=svc.files("full/id/","",day(),day());
        assertEquals(1,((List<?>)result.get("files")).size());assertTrue(((Map<?,?>)((List<?>)result.get("files")).get(0)).containsValue(day()));
        assertTrue(((List<?>)svc.files("binlog/uuid/unassigned/","",day(),day()).get("files")).isEmpty());
        assertEquals(1,((List<?>)svc.files("binlog/uuid/unassigned/","").get("files")).size());
    }

    /**
     * 分页在日期过滤后进行，不漏页或重复，目录游标由页面切换时清空。
     */
    @Test void paginatesIndex()throws Exception {
        OSS oss=mock(OSS.class);List<com.example.insurancesystem.backup.BackupInventory.Entry> entries=new ArrayList<>();
        for(int i=0;i<105;i++)entries.add(entry("BINLOG","binlog/uuid/"+day()+"/bin."+String.format("%06d",i),day()));
        install(oss,"binlog",entries);install(oss,"full",List.of());MonitorBackupDownloadService svc=service(oss);
        Map<String,Object> first=svc.files("binlog/uuid/"+day()+"/","",day(),day());assertEquals(100,((List<?>)first.get("files")).size());
        Map<String,Object> second=svc.files("binlog/uuid/"+day()+"/",(String)first.get("nextMarker"),day(),day());assertEquals(5,((List<?>)second.get("files")).size());assertEquals("",second.get("nextMarker"));
    }

    /**
     * 仅索引明确不存在可返回空，权限失败及损坏JSON必须报错，不误导管理员没有备份。
     */
    @Test void rejectsCorruptOrDeniedIndex()throws Exception {
        OSS oss=mock(OSS.class);com.aliyun.oss.OSSException missing=mock(com.aliyun.oss.OSSException.class);when(missing.getErrorCode()).thenReturn("NoSuchKey");
        doThrow(missing).when(oss).getObject(anyString(),anyString());assertTrue(((List<?>)service(oss).files("","").get("files")).isEmpty());
        com.aliyun.oss.OSSException denied=mock(com.aliyun.oss.OSSException.class);when(denied.getErrorCode()).thenReturn("AccessDenied");doThrow(denied).when(oss).getObject(anyString(),anyString());
        assertThrows(RuntimeException.class,()->service(oss).files("",""));
        doAnswer(c->{OSSObject object=new OSSObject();object.setObjectContent(new java.io.ByteArrayInputStream("broken".getBytes()));return object;}).when(oss).getObject(anyString(),anyString());assertThrows(RuntimeException.class,()->service(oss).files("",""));
    }

    /**
     * 日期范围只允许北京时间最近30天，拒绝单边、倒置、未来及越界日期。
     */
    @Test void validatesWindow() {
        java.time.LocalDate today=java.time.LocalDate.parse(day());assertNotNull(MonitorBackupDownloadService.validateDates(today.minusDays(29).toString(),day()));
        assertThrows(RuntimeException.class,()->MonitorBackupDownloadService.validateDates(today.minusDays(30).toString(),day()));
        assertThrows(RuntimeException.class,()->MonitorBackupDownloadService.validateDates(day(),today.plusDays(1).toString()));
        assertThrows(RuntimeException.class,()->MonitorBackupDownloadService.validateDates(day(),null));
    }

    /**
     * 构造只读服务并隔离真实OSS客户端。
     */
    MonitorBackupDownloadService service(OSS oss){return new MonitorBackupDownloadService("bucket","database-backup","prod"){protected OSS oss(){return oss;}};}

    /**
     * 测试协议来自与协调器共享的Entry模型，确保前后端日期与索引结构一致。
     */
    com.example.insurancesystem.backup.BackupInventory.Entry entry(String type,String key,String day) {
        var entry=new com.example.insurancesystem.backup.BackupInventory.Entry();entry.type=type;entry.id=key;entry.objectKey="database-backup/prod/"+key;entry.backupDate=day;entry.sizeBytes=10;return entry;
    }

    /**
     * OSS每次返回新流，模拟真实客户端关闭对象后下一次请求仍可独立读取。
     */
    void install(OSS oss,String type,List<com.example.insurancesystem.backup.BackupInventory.Entry> entries)throws Exception {
        var index=new com.example.insurancesystem.backup.BackupInventory();for(var entry:entries)index.files.put(entry.objectKey,entry);
        byte[] bytes=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(index);
        when(oss.getObject("bucket","database-backup/prod/"+type+"/index.json")).thenAnswer(c->{OSSObject object=new OSSObject();object.setObjectContent(new java.io.ByteArrayInputStream(bytes));return object;});
    }

    /**
     * 与业务服务保持同一日期窗口时区。
     */
    String day(){return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();}
}
