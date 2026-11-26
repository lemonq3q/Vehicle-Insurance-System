package com.example.insurancesystem.coordinator.backup;

import com.aliyun.oss.*;
import com.aliyun.oss.model.*;
import com.example.insurancesystem.utils.OSSClientSingleton;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 备份专用OSS封装，复用系统凭证和Endpoint；仅允许操作独立前缀，不写业务附件表。 */
@Component
public class BackupObjectStore {
    private final BackupProperties config;private final ObjectMapper mapper;
    /** OSS客户端保持懒加载，关闭备份时不触发联网。 */
    public BackupObjectStore(BackupProperties config,ObjectMapper mapper){this.config=config;this.mapper=mapper;}
    /** 固定实例级前缀禁止空前缀或跨目录清理。 */
    public String base(){String prefix=config.getPrefix();if(prefix==null||!prefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*"))throw new IllegalArgumentException("备份OSS前缀不合法");return prefix+"/"+config.getInstance()+"/";}
    /** 流式计算文件摘要，不将数据库文件整体加载到内存。 */
    public String sha256(Path file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return HexFormat.of().formatHex(digest.digest());}
    /** 同名Put覆盖，不预删旧对象；SDK上传校验后再核对大小与自定义摘要。 */
    public String upload(String key,Path file,Map<String,String> metadata)throws Exception{
        check(key);String hash=sha256(file);ObjectMetadata meta=new ObjectMetadata();meta.setContentLength(Files.size(file));meta.addUserMetadata("sha256",hash);for(Map.Entry<String,String> item:metadata.entrySet())meta.addUserMetadata(item.getKey(),item.getValue());
        PutObjectRequest request=new PutObjectRequest(config.getBucket(),key,file.toFile());request.setMetadata(meta);OSS oss=OSSClientSingleton.getInstance();oss.putObject(request);
        ObjectMetadata remote=oss.getObjectMetadata(config.getBucket(),key);if(remote.getContentLength()!=Files.size(file)||!hash.equals(remote.getUserMetadata().get("sha256")))throw new IOException("OSS备份校验失败");return hash;
    }
    /** JSON清单最后发布，失败不能将备份标为成功。 */
    public void json(String key,Map<String,Object> value)throws Exception{check(key);byte[] bytes=mapper.writeValueAsBytes(value);OSSClientSingleton.getInstance().putObject(config.getBucket(),key,new ByteArrayInputStream(bytes));}
    /** 从OSS读取持久清单，本地状态损坏后仍可列举恢复。 */
    public Map<String,Object> read(String key)throws Exception{check(key);try(OSSObject object=OSSClientSingleton.getInstance().getObject(config.getBucket(),key)){return mapper.readValue(object.getObjectContent(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}}
    /** 分页列举指定子前缀，不能扫描或删除业务Bucket其他目录。 */
    public List<String> list(String prefix){check(prefix);List<String> keys=new ArrayList<>();String marker=null;do{ObjectListing page=OSSClientSingleton.getInstance().listObjects(new ListObjectsRequest(config.getBucket()).withPrefix(prefix).withMarker(marker));for(OSSObjectSummary object:page.getObjectSummaries())keys.add(object.getKey());marker=page.isTruncated()?page.getNextMarker():null;}while(marker!=null);return keys;}
    /** 仅清理清单明确引用的备份对象，不向MySQL发送PURGE命令。 */
    public void delete(String key){check(key);OSSClientSingleton.getInstance().deleteObject(config.getBucket(),key);}
    /** 每个写入/读取/删除都校验实例专属前缀。 */
    private void check(String key){if(!key.startsWith(base())||key.contains(".."))throw new IllegalArgumentException("对象不在备份前缀内");}
}
