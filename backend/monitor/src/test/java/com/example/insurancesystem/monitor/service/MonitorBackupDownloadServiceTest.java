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
    /** 目录浏览只列举当前前缀的下一层，保持OSS分页标记。 */
    @Test void listsDirectory(){OSS oss=mock(OSS.class);ObjectListing page=new ObjectListing();page.getCommonPrefixes().add("database-backup/prod/full/");page.setTruncated(true);page.setNextMarker("next");when(oss.listObjects(any(ListObjectsRequest.class))).thenReturn(page);MonitorBackupDownloadService service=new MonitorBackupDownloadService("bucket","database-backup","prod"){protected OSS oss(){return oss;}};Map<String,Object> result=service.files("","");assertEquals("next",result.get("nextMarker"));assertEquals(List.of(Map.of("path","full/","name","full")),result.get("folders"));}
}
