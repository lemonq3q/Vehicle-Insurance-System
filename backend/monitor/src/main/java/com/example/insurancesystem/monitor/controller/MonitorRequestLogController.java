package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDate;
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
    /** 复用监控数据库访问设施。 */
    public MonitorRequestLogController(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    /** 按日期闭区间及精确字段分页；默认当天，最大100条，非法参数返回业务400。 */
    @GetMapping public ResponseResult<Map<String,Object>> page(@RequestParam Map<String,String> query) {
        try {
            LocalDate start=LocalDate.parse(query.getOrDefault("startDate",LocalDate.now().toString()));
            LocalDate end=LocalDate.parse(query.getOrDefault("endDate",start.toString()));
            if(start.isAfter(end)) throw new IllegalArgumentException();
            int page=Math.max(1,Integer.parseInt(query.getOrDefault("pageNo","1")));
            int size=Math.max(1,Math.min(100,Integer.parseInt(query.getOrDefault("pageSize","20"))));
            MapSqlParameterSource args=new MapSqlParameterSource().addValue("start",Timestamp.valueOf(start.atStartOfDay())).addValue("end",Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
            StringBuilder where=new StringBuilder(" WHERE started_at>=:start AND started_at<:end");
            Map<String,String> fields=new LinkedHashMap<>();
            fields.put("sourceSystem","source_system"); fields.put("uri","uri"); fields.put("httpStatus","http_status"); fields.put("businessCode","business_code"); fields.put("hasException","has_exception"); fields.put("enterpriseId","enterprise_id"); fields.put("userId","user_id"); fields.put("logId","log_id");
            for(Map.Entry<String,String> field:fields.entrySet()) { String value=query.get(field.getKey()); if(value!=null&&!value.isBlank()) {
                if(Set.of("httpStatus","hasException","enterpriseId","userId").contains(field.getKey())) { long number=Long.parseLong(value); if(field.getKey().equals("hasException") && number!=0 && number!=1) throw new IllegalArgumentException(); args.addValue(field.getKey(),number); }
                else args.addValue(field.getKey(),value);
                where.append(" AND ").append(field.getValue()).append("=:").append(field.getKey());
            }}
            Long total=jdbc.queryForObject("SELECT COUNT(*) FROM monitor_request_log"+where,args,Long.class);
            args.addValue("offset",((long)page-1)*size).addValue("size",size);
            List<Map<String,Object>> list=jdbc.queryForList("SELECT * FROM monitor_request_log"+where+" ORDER BY started_at DESC,id DESC LIMIT :offset,:size",args);
            return new ResponseResult<>(200,Map.of("list",list,"total",total,"pageNo",page,"pageSize",size));
        } catch(java.time.DateTimeException|IllegalArgumentException e) { throw new BusinessException(400,"日志查询参数不合法"); }
    }
    /** 详情包含原始请求与响应，只对管理员开放；不存在返回404业务码。 */
    @GetMapping("/{logId}") public ResponseResult<?> detail(@PathVariable String logId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT payload FROM monitor_request_log_detail WHERE log_id=:id",new MapSqlParameterSource("id",logId));
        if(rows.isEmpty()) throw new BusinessException(404,"日志不存在");
        return new ResponseResult<>(200,rows.get(0));
    }
}
