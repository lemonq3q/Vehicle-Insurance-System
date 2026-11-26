package com.example.insurancesystem.monitor.service;

import com.aliyun.oss.*;
import com.aliyun.oss.model.*;
import com.example.insurancesystem.utils.OSSClientSingleton;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

/** 管理员按目录浏览OSS备份并手动选择文件下载；仅签发私有短期链接，不打包、不上传、不删除对象。 */
@Service
public class MonitorBackupDownloadService {
    private final String bucket,base;
    /** 读取与协调器一致的备份位置，构造时不访问OSS。 */
    public MonitorBackupDownloadService(@Value("${monitor.backup.bucket:lemonqwq}")String bucket,@Value("${monitor.backup.prefix:database-backup}")String prefix,@Value("${monitor.backup.instance:prod}")String instance){
        if(!prefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")||!instance.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("备份下载配置不合法");this.bucket=bucket;this.base=prefix+"/"+instance+"/";
    }
    /** 复用现有阿里云凭证；单元测试替换客户端，不访问真实备份。 */
    protected OSS oss(){return OSSClientSingleton.getInstance();}
    /** 目录只接受相对路径，阻止跳出配置前缀。 */
    public static String safePath(String path){String value=path==null?"":path;if(value.startsWith("/")||value.contains("\\")||value.contains("\0")||Arrays.asList(value.split("/",-1)).contains("..")||Arrays.asList(value.split("/",-1)).contains(".")||(!value.isEmpty()&&!value.endsWith("/")))throw new HttpBusinessException(400,"备份目录不合法");return value;}
    /** 按OSS原生目录和marker分页，不将凭证和预签名URL混入目录列表。 */
    public Map<String,Object> files(String path,String marker){String relative=safePath(path);try{
        ObjectListing listing=oss().listObjects(new ListObjectsRequest(bucket).withPrefix(base+relative).withDelimiter("/").withMarker(marker==null||marker.isBlank()?null:marker).withMaxKeys(100));
        List<Map<String,Object>> folders=new ArrayList<>(),files=new ArrayList<>();
        for(String folder:listing.getCommonPrefixes()){if(!folder.startsWith(base))continue;String rel=folder.substring(base.length());String trimmed=rel.substring(0,rel.length()-1);folders.add(Map.of("path",rel,"name",trimmed.substring(trimmed.lastIndexOf('/')+1)));}
        for(OSSObjectSummary item:listing.getObjectSummaries()){if(item.getKey().endsWith("/"))continue;files.add(Map.of("key",item.getKey(),"name",item.getKey().substring(item.getKey().lastIndexOf('/')+1),"sizeBytes",item.getSize(),"modifiedAt",item.getLastModified().toInstant().toString()));}
        return Map.of("path",relative,"folders",folders,"files",files,"nextMarker",listing.isTruncated()?Objects.toString(listing.getNextMarker(),""):"");
    }catch(OSSException|ClientException e){throw new HttpBusinessException(502,"读取OSS备份目录失败，请稍后重试");}}
    /** 校验对象归属和存在性后签发10分钟下载链接，浏览器直接从OSS下载。 */
    public Map<String,Object> download(String key){
        if(key==null||!key.startsWith(base)||key.endsWith("/"))throw new HttpBusinessException(400,"备份文件路径不合法");
        String relative=key.substring(base.length());safePath(relative+"/");if(relative.isBlank())throw new HttpBusinessException(400,"备份文件路径不合法");
        try{oss().getObjectMetadata(bucket,key);String name=key.substring(key.lastIndexOf('/')+1);Date expires=Date.from(Instant.now().plusSeconds(600));GeneratePresignedUrlRequest request=new GeneratePresignedUrlRequest(bucket,key);request.setExpiration(expires);ResponseHeaderOverrides headers=new ResponseHeaderOverrides();headers.setContentDisposition(ContentDisposition.attachment().filename(name,java.nio.charset.StandardCharsets.UTF_8).build().toString());request.setResponseHeaders(headers);return Map.of("name",name,"downloadUrl",oss().generatePresignedUrl(request).toString(),"expiresAt",expires.toInstant().toString());}
        catch(OSSException e){if("NoSuchKey".equals(e.getErrorCode()))throw new HttpBusinessException(404,"备份文件不存在或已过期清理，请刷新目录");throw new HttpBusinessException(502,"获取备份下载链接失败，请稍后重试");}
        catch(ClientException e){throw new HttpBusinessException(502,"OSS暂不可用，请稍后重试");}
    }
}
