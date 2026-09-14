package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.service.MonitorPromotionService;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 监控平台销售推广接口，向信息录入页提供目标 CRUD、模板下载和批量导入，向信息推广页提供
 * Excel 一次性解析结果、渠道可触达数量预览和模拟群发。Excel 名单由浏览器暂存，后端不维护临时名单。
 */
@RestController
@RequestMapping("/monitor")
public class MonitorPromotionController {
    private final MonitorPromotionService service;
    public MonitorPromotionController(MonitorPromotionService service) { this.service = service; }

    /** 按组合条件分页查询已录入推广目标。 */
    @GetMapping("/promotion-targets")
    public ResponseResult<Map<String,Object>> page(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String sourceType, @RequestParam(required=false) Integer status,
            @RequestParam(required=false) String channel,
            @RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="10") int pageSize) {
        return new ResponseResult<>(200, service.page(keyword, sourceType, status, channel, pageNo, pageSize));
    }

    /** 下载固定 xlsx 导入模板。 */
    @GetMapping("/promotion-targets/template")
    public void template(HttpServletResponse response) { service.writeTemplate(response); }

    /** 新增单个推广目标。 */
    @PostMapping("/promotion-targets")
    public ResponseResult<Map<String,Object>> create(@RequestBody Map<String,Object> body, Authentication authentication) {
        LoginUser user=operator(authentication); return new ResponseResult<>(200, service.create(body,user.getUser().getId(),user.getUser().getRealName()));
    }

    /** 修改单个推广目标基础资料和可推广状态。 */
    @PutMapping("/promotion-targets/{id}")
    public ResponseResult<Map<String,Object>> update(@PathVariable Long id,@RequestBody Map<String,Object> body,Authentication authentication) {
        LoginUser user=operator(authentication); return new ResponseResult<>(200, service.update(id,body,user.getUser().getId(),user.getUser().getRealName()));
    }

    /** 软删除推广目标，该业务无需提交请求体或删除原因。 */
    @DeleteMapping("/promotion-targets/{id}")
    public ResponseResult<?> delete(@PathVariable Long id,Authentication authentication) {
        LoginUser user=operator(authentication); service.delete(id,user.getUser().getId()); return new ResponseResult<>(200,"删除成功",null);
    }

    /** 按官方模板批量导入并返回逐行成功与失败结果。 */
    @PostMapping(value="/promotion-targets/import",consumes="multipart/form-data")
    public ResponseResult<Map<String,Object>> importTargets(@RequestPart("file") MultipartFile file,Authentication authentication) {
        LoginUser user=operator(authentication); return new ResponseResult<>(200,service.importTargets(file,user.getUser().getId(),user.getUser().getRealName()));
    }

    /** 一次性解析推广 Excel 并返回全部有效行，不写目标表且不在服务端缓存名单。 */
    @PostMapping(value="/promotions/import-preview",consumes="multipart/form-data")
    public ResponseResult<Map<String,Object>> importPreview(@RequestPart("file") MultipartFile file) { return new ResponseResult<>(200,service.previewImport(file)); }

    /** 计算所选渠道下的有效收件人数和 6000 条截断提示。 */
    @PostMapping("/promotions/preview")
    public ResponseResult<Map<String,Object>> preview(@RequestBody Map<String,Object> body) { return new ResponseResult<>(200,service.preview(body)); }

    /** 通过当前 mock 渠道执行群发，并返回模拟批次结果。 */
    @PostMapping("/promotions/send")
    public ResponseResult<Map<String,Object>> promote(@RequestBody Map<String,Object> body,Authentication authentication) {
        LoginUser user=operator(authentication); return new ResponseResult<>(200,service.promote(body,user.getUser().getId(),user.getUser().getRealName()));
    }

    /** 从 Spring Security 上下文取得监控账号，拒绝匿名操作。 */
    private LoginUser operator(Authentication authentication) { if(authentication==null||!(authentication.getPrincipal() instanceof LoginUser)) throw new BusinessException(401,"请先登录"); return (LoginUser)authentication.getPrincipal(); }
}
