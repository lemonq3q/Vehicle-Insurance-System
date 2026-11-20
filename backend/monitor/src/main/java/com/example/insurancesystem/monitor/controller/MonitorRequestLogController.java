package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 管理员请求诊断查询；参数化SQL，列表不读取大字段，详情独立加载。 */
@RestController
@RequestMapping("/monitor/request-logs")
@PreAuthorize("hasRole('ADMIN')")
public class MonitorRequestLogController {
    private final NamedParameterJdbcTemplate jdbc;
    private final com.example.insurancesystem.monitor.integration.SaasLogDirectoryClient directory;
    /** 复用监控数据库访问设施。 */
    public MonitorRequestLogController(NamedParameterJdbcTemplate jdbc,com.example.insurancesystem.monitor.integration.SaasLogDirectoryClient directory) { this.jdbc=jdbc;this.directory=directory; }
    /** 按秒级时间、状态分类、模糊URI与耗时区间分页；未指定时间则不限时间，最大100条，非法参数返回业务400。 */
    @GetMapping public ResponseResult<Map<String,Object>> page(@RequestParam Map<String,String> query) {
        try {
            java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);
            String startValue=query.get("startTime"),endValue=query.get("endTime");
            java.time.LocalDateTime start=startValue==null||startValue.isBlank()?null:java.time.LocalDateTime.parse(startValue,format);
            java.time.LocalDateTime end=endValue==null||endValue.isBlank()?null:java.time.LocalDateTime.parse(endValue,format);
            if(start!=null&&end!=null&&start.isAfter(end)) throw new IllegalArgumentException();
            int page=Math.max(1,Integer.parseInt(query.getOrDefault("pageNo","1")));
            int size=Math.max(1,Math.min(100,Integer.parseInt(query.getOrDefault("pageSize","20"))));
            MapSqlParameterSource args=new MapSqlParameterSource();
            StringBuilder where=new StringBuilder(" WHERE 1=1");
            /* 清除日期代表不限时间，不能偷偷回退当天；有结束秒时包含其全部毫秒。 */
            if(start!=null){where.append(" AND started_at>=:start");args.addValue("start",Timestamp.valueOf(start));}
            if(end!=null){where.append(" AND started_at<:end");args.addValue("end",Timestamp.valueOf(end.plusSeconds(1)));}
            Map<String,String> fields=new LinkedHashMap<>();
            fields.put("sourceSystem","source_system"); fields.put("hasException","has_exception"); fields.put("enterpriseId","enterprise_id"); fields.put("userId","user_id"); fields.put("logId","log_id");
            for(Map.Entry<String,String> field:fields.entrySet()) { String value=query.get(field.getKey()); if(value!=null&&!value.isBlank()) {
                if(Set.of("httpStatus","hasException","enterpriseId","userId").contains(field.getKey())) { long number=Long.parseLong(value); if(field.getKey().equals("hasException") && number!=0 && number!=1) throw new IllegalArgumentException(); args.addValue(field.getKey(),number); }
                else args.addValue(field.getKey(),value);
                where.append(" AND ").append(field.getValue()).append("=:").append(field.getKey());
            }}
            /* 状态分类使用固定区间，业务码是字符串，只匹配三位数字码，避免隐式数值转换。 */
            for(String key:List.of("httpStatusClass","businessCodeClass")) {
                String value=query.get(key);if(value!=null&&!value.isBlank()) {
                    int code=Integer.parseInt(value);if(!Set.of(100,200,300,400,500).contains(code))throw new IllegalArgumentException();
                    if(key.equals("httpStatusClass")){where.append(" AND http_status>=:httpMin AND http_status<:httpMax");args.addValue("httpMin",code).addValue("httpMax",code+100);}
                    else{where.append(" AND business_code REGEXP :businessPattern");args.addValue("businessPattern","^"+(code/100)+"[0-9][0-9]$");}
                }
            }
            String uri=query.get("uri");if(uri!=null&&!uri.isBlank()){where.append(" AND uri LIKE :uri ESCAPE '!'");args.addValue("uri","%"+uri.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
            Long min=duration(query.get("minDurationMs")),max=duration(query.get("maxDurationMs"));
            if(min!=null&&max!=null&&min>max)throw new IllegalArgumentException();
            if(min!=null){where.append(" AND duration_ms>=:durationMin");args.addValue("durationMin",min);}
            if(max!=null){where.append(" AND duration_ms<=:durationMax");args.addValue("durationMax",max);}
            if(query.containsKey("userId")&&!query.get("userId").isBlank()){
                if(!query.containsKey("enterpriseId")||query.get("enterpriseId").isBlank())throw new IllegalArgumentException();
                /* 日志自身同时限定企业和用户，历史查询不依赖当前成员关系，亦不访问业务表。 */
            }
            Long total=jdbc.queryForObject("SELECT COUNT(*) FROM monitor_request_log"+where,args,Long.class);
            args.addValue("offset",((long)page-1)*size).addValue("size",size);
            List<Map<String,Object>> list=jdbc.queryForList("SELECT * FROM monitor_request_log"+where+" ORDER BY started_at DESC,id DESC LIMIT :offset,:size",args);
            return new ResponseResult<>(200,Map.of("list",list,"total",total,"pageNo",page,"pageSize",size));
        } catch(java.time.DateTimeException|IllegalArgumentException e) { throw new BusinessException(400,"日志查询参数不合法"); }
    }
    /** 耗时边界为非负整数毫秒，留空不限制，不接受小数或负数。 */
    private Long duration(String value) {if(value==null||value.isBlank())return null;long n=Long.parseLong(value);if(n<0)throw new IllegalArgumentException();return n;}
    /** 指定企业下按姓名、邮箱模糊联想；返回账号ID而不是成员主键，最多20条。 */
    @GetMapping("/user-options") public ResponseResult<?> users(@RequestParam Long enterpriseId,@RequestParam(defaultValue="") String keyword) {
        if(enterpriseId<=0)throw new BusinessException(400,"企业参数不合法");
        if(keyword.isBlank())return new ResponseResult<>(200,List.of());
        return new ResponseResult<>(200,directory.options("users",enterpriseId,keyword));
    }
    /** 企业目录由SaaS按名称搜索，监控只负责管理员权限与转发。 */
    @GetMapping("/enterprise-options") public ResponseResult<?> enterprises(@RequestParam(defaultValue="") String keyword) {return new ResponseResult<>(200,directory.options("enterprises",null,keyword));}

    /** 详情包含原始请求与响应，只对管理员开放；不存在返回404业务码。 */
    @GetMapping("/{logId}") public ResponseResult<?> detail(@PathVariable String logId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT payload FROM monitor_request_log_detail WHERE log_id=:id",new MapSqlParameterSource("id",logId));
        if(rows.isEmpty()) throw new BusinessException(404,"日志不存在");
        return new ResponseResult<>(200,rows.get(0));
    }
}
