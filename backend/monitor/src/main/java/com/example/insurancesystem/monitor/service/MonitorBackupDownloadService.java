package com.example.insurancesystem.monitor.service;

import com.aliyun.oss.*;
import com.aliyun.oss.model.*;
import com.example.insurancesystem.backup.BackupInventory;
import com.example.insurancesystem.backup.BackupInventory.Entry;
import com.example.insurancesystem.utils.OSSClientSingleton;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

/**
 * 管理员只读备份浏览：每次读取全量和binlog统一索引，在内存按所属日期、目录及分页组织结果。
 * 不逐文件请求元数据、不查询业务表，不参与备份上传和过期删除；下载签发十分钟私有链接。
 */
@Service
public class MonitorBackupDownloadService {
    private final String bucket,base;
    private final ObjectMapper mapper=new ObjectMapper();

    /**
     * 使用与协调器相同的OSS位置，构造时不联网；仅允许明确的实例前缀，防止越界浏览。
     */
    public MonitorBackupDownloadService(@Value("${monitor.backup.bucket:lemonqwq}")String bucket,
            @Value("${monitor.backup.prefix:database-backup}")String prefix,
            @Value("${monitor.backup.instance:prod}")String instance) {
        if(!prefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")||!instance.matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("备份下载配置不合法");
        this.bucket=bucket;this.base=prefix+"/"+instance+"/";
    }

    /**
     * 复用现有阿里云配置，测试替换客户端后不触及真实备份。
     */
    protected OSS oss(){return OSSClientSingleton.getInstance();}

    /**
     * 目录只接受相对路径，以斜杠结尾；下载也复用检查，拒绝目录穿越及绝对路径。
     */
    public static String safePath(String path) {
        String value=path==null?"":path;
        if(value.startsWith("/")||value.contains("\\")||value.contains("\0")
                ||Arrays.asList(value.split("/",-1)).contains("..")||Arrays.asList(value.split("/",-1)).contains(".")
                ||value.contains("//")||(!value.isEmpty()&&!value.endsWith("/")))
            throw new HttpBusinessException(400,"备份目录不合法");
        return value;
    }

    /**
     * 无日期参数可浏览日期未知或因恢复依赖额外保留的文件，保持旧调用方式兼容。
     */
    public Map<String,Object> files(String path,String marker){return files(path,marker,null,null);}

    /**
     * 每份索引读取一次，按固定backupDate筛选后提取下一层目录。分页使用最后一个名称为游标，
     * 新旧请求均不会靠OSS上传时间推断归属；索引不可读时返回502，不伪装为空目录。
     */
    public Map<String,Object> files(String path,String marker,String startDate,String endDate) {
        String relative=safePath(path);LocalDate[] dates=validateDates(startDate,endDate);
        try {
            SortedMap<String,Map<String,Object>> folders=new TreeMap<>(), files=new TreeMap<>();
            for(String type:List.of("full","binlog"))for(Entry entry:inventory(type).files.values()) {
                if(dates!=null&&(entry.backupDate==null||LocalDate.parse(entry.backupDate).isBefore(dates[0])
                        ||LocalDate.parse(entry.backupDate).isAfter(dates[1])))continue;
                String key=entry.objectKey.substring(base.length());if(!key.startsWith(relative))continue;
                String rest=key.substring(relative.length());int slash=rest.indexOf('/');
                if(slash>=0) {
                    String name=rest.substring(0,slash);Map<String,Object> folder=folders.computeIfAbsent(name,n->{
                        Map<String,Object> item=new LinkedHashMap<>();item.put("path",relative+n+"/");item.put("name",n);item.put("dataRangeKnown",false);return item;
                    });
                    mergeDate(folder,entry.backupDate);
                } else if(!rest.isEmpty()) {
                    Map<String,Object> row=new LinkedHashMap<>();row.put("key",entry.objectKey);row.put("name",rest);
                    row.put("sizeBytes",entry.sizeBytes);row.put("modifiedAt",Objects.toString(entry.uploadedAt,""));
                    row.put("dataRangeKnown",false);mergeDate(row,entry.backupDate);files.put(rest,row);
                }
            }
            SortedMap<String,Map<String,Object>> rows=new TreeMap<>();folders.forEach((key,value)->rows.put("D:"+key,value));files.forEach((key,value)->rows.put("F:"+key,value));
            String after=marker==null?"":marker;
            if(!after.isEmpty()&&!after.startsWith("D:")&&!after.startsWith("F:"))throw new HttpBusinessException(400,"备份分页标记不合法");
            List<Map<String,Object>> folderPage=new ArrayList<>(),filePage=new ArrayList<>();String last="";int count=0;
            for(Map.Entry<String,Map<String,Object>> row:rows.tailMap(after).entrySet()) {
                if(row.getKey().equals(after))continue;if(count==100)break;
                if(row.getKey().startsWith("D:"))folderPage.add(row.getValue());else filePage.add(row.getValue());
                last=row.getKey();count++;
            }
            String next=!last.isEmpty()&&!rows.tailMap(last+"\0").isEmpty()?last:"";
            return Map.of("path",relative,"folders",folderPage,"files",filePage,"nextMarker",next);
        }catch(OSSException|ClientException|IOException|IllegalArgumentException e) {
            throw new HttpBusinessException(502,"读取OSS备份索引失败，请稍后重试");
        }
    }

    /**
     * 仅NoSuchKey代表尚未生成索引；权限、网络、JSON或对象范围异常必须原样中止目录组装。
     */
    protected BackupInventory inventory(String type)throws IOException {
        BackupInventory index;
        try(OSSObject object=oss().getObject(bucket,base+type+"/index.json")) {
            com.fasterxml.jackson.databind.JsonNode node=mapper.readTree(object.getObjectContent());BackupInventory.validateShape(node);
            index=mapper.treeToValue(node,BackupInventory.class);
        }catch(OSSException e){if(!"NoSuchKey".equals(e.getErrorCode()))throw e;return new BackupInventory();}
        if(index==null)throw new IOException("备份索引为空");index.validate();
        for(Entry entry:index.files.values()) {
            if(!entry.objectKey.startsWith(base+type+"/")||entry.objectKey.equals(base+type+"/index.json")
                    ||!type.equalsIgnoreCase(entry.type))throw new IOException("索引对象越界");
            try{safePath(entry.objectKey.substring(base.length())+"/");}catch(HttpBusinessException e){throw new IOException("索引路径不合法",e);}
        }
        return index;
    }

    /**
     * 子目录汇总所属日期最小值与最大值仅作展示；文件日期始终是固定backupDate，未知不猜测。
     */
    private static void mergeDate(Map<String,Object> row,String day) {
        if(day==null)return;
        if(!Boolean.TRUE.equals(row.get("dataRangeKnown"))) {row.put("dataStartDate",day);row.put("dataEndDate",day);row.put("dataRangeKnown",true);}
        else {if(day.compareTo((String)row.get("dataStartDate"))<0)row.put("dataStartDate",day);if(day.compareTo((String)row.get("dataEndDate"))>0)row.put("dataEndDate",day);}
        if(Objects.equals(row.get("dataStartDate"),row.get("dataEndDate")))row.put("backupDate",day);else row.remove("backupDate");
    }

    /**
     * 日期必须一起指定且位于北京时间最近30个自然日；不允许倒置、单边或未来日期。
     */
    public static LocalDate[] validateDates(String start,String end) {
        if((start==null||start.isBlank())&&(end==null||end.isBlank()))return null;
        try{LocalDate a=LocalDate.parse(start),b=LocalDate.parse(end),today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
            if(a.isAfter(b)||a.isBefore(today.minusDays(29))||b.isAfter(today))throw new IllegalArgumentException();return new LocalDate[]{a,b};
        }catch(Exception e){throw new HttpBusinessException(400,"请选择最近30天内的完整日期范围");}
    }

    /**
     * 对选中文件验证前缀与存在性后签发附件链接，保留原文件名，浏览器直接从私有OSS下载。
     * 不存在返回404，依赖故障502；普通请求失败仍由监控统一通知处理。
     */
    public Map<String,Object> download(String key) {
        if(key==null||!key.startsWith(base)||key.endsWith("/"))throw new HttpBusinessException(400,"备份文件路径不合法");
        String relative=key.substring(base.length());safePath(relative+"/");if(relative.isBlank())throw new HttpBusinessException(400,"备份文件路径不合法");
        try{oss().getObjectMetadata(bucket,key);String name=key.substring(key.lastIndexOf('/')+1);Date expires=Date.from(Instant.now().plusSeconds(600));
            GeneratePresignedUrlRequest request=new GeneratePresignedUrlRequest(bucket,key);request.setExpiration(expires);
            ResponseHeaderOverrides headers=new ResponseHeaderOverrides();headers.setContentDisposition(ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString());request.setResponseHeaders(headers);
            return Map.of("name",name,"downloadUrl",oss().generatePresignedUrl(request).toString(),"expiresAt",expires.toInstant().toString());
        }catch(OSSException e){if("NoSuchKey".equals(e.getErrorCode()))throw new HttpBusinessException(404,"备份文件不存在或已过期清理，请刷新目录");throw new HttpBusinessException(502,"获取备份下载链接失败，请稍后重试");}
        catch(ClientException e){throw new HttpBusinessException(502,"OSS暂不可用，请稍后重试");}
    }
}
