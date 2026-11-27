package com.example.insurancesystem.ai;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.Workorder;
import com.example.insurancesystem.mapper.SystemFileMapper;
import com.example.insurancesystem.mapper.WorkorderMapper;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 编排承保附件的企业校验、单次 OCR、完整校验和有界整理重试。
 * 不修改工单或文件关联；识别值只是承保表单候选，用户保存时才通过现有事务写入。
 */
@Service
public class PolicyOcrService {
    private final WorkorderMapper workorderMapper;
    private final SystemFileMapper systemFileMapper;
    private final OSSUtil ossUtil;
    private final PolicyOcrCaller caller;
    private final PolicyOcrWorkflow workflow;

    public PolicyOcrService(WorkorderMapper workorderMapper, SystemFileMapper systemFileMapper,
                            OSSUtil ossUtil, PolicyOcrCaller caller, PolicyOcrWorkflow workflow) {
        this.workorderMapper = workorderMapper;
        this.systemFileMapper = systemFileMapper;
        this.ossUtil = ossUtil;
        this.caller = caller;
        this.workflow = workflow;
    }

    /**
     * 对当前企业工单中的上传文件识别所有可见险种资料，允许待报价等任意有效工单状态预识别。
     * 首次 OCR 固定一次；结构不合格时首次整理加配置的重试次数，每次整理后完整复查所有字段。
     *
     * @param request 当前工单与文件 ID
     * @return 统一响应，成功时含文件和已校验的扁平字段，不向前端返回字段质量警告
     */
    public ResponseResult<?> recognize(PolicyOcrRequest request) {
        return recognize(request, EnterpriseContextHolder.requireEnterpriseId());
    }

    /**
     * 后台任务使用提交时确认的企业身份执行识别，不依赖只存在于 HTTP 线程的登录上下文。
     * 执行前再次核对工单和文件归属，避免任务排队期间对象被删除后仍访问供应商。
     *
     * @param request 提交任务时的工单与文件 ID
     * @param enterpriseId 提交请求认证主体所属企业 ID
     * @return 原同步接口的识别结果，供异步任务保存到 Redis
     */
    public ResponseResult<?> recognize(PolicyOcrRequest request, Long enterpriseId) {
        ResponseResult<?> invalid = validateRequest(request, enterpriseId, true);
        if (invalid != null) return invalid;
        SystemFile file = systemFileMapper.selectById(request.fileId());
        boolean pdf = file.getFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        try {
            String url = ossUtil.getTmpUrl(file.getPath());
            if (url == null || url.isBlank()) return new ResponseResult<>(500, "生成文件访问地址失败");

            /*
             * 每个后台任务只执行一次完整文件 OCR。临时供应商故障由调用器有界重试，
             * 有内容返回后只用原文进行结构整理，不重新读取文件。
             */
            String raw = caller.recognize(url, pdf, enterpriseId);
            PolicyOcrValidator.ValidationResult result = workflow.validateAndOrganize(raw);
            /*
             * 合法全空结果正常返回空候选，由页面按上传类别提示；仅校验未通过且没有可用字段
             * 才视为识别失败。部分有效字段继续保留原有容错回填行为，不修改工单数据。
             */
            if (!result.valid() && result.values().isEmpty()) {
                return new ResponseResult<>(502, "保单识别结果无法解析，请重试或手动填写");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileInfo", file);
            data.put("recognitionData", result.values());
            // 保留旧响应字段兼容调用方；校验详情只在服务端记录，不作为用户警告返回。
            data.put("warnings", java.util.List.of());
            return new ResponseResult<>(200, "识别成功", data);
        } catch (IllegalStateException ex) {
            return new ResponseResult<>(503, ex.getMessage());
        } catch (Exception ex) {
            return new ResponseResult<>(502, "保单识别服务暂不可用，请稍后重试");
        }
    }

    /**
     * 在任务入队与实际执行时复用同一租户和文件类型检查。
     * OSS 完整性只在后台执行时检查，避免提交请求等待对象存储网络操作。
     *
     * @param request 当前工单与文件 ID
     * @param enterpriseId 当前认证企业或入队时固定的企业 ID
     * @param checkObject 是否在后台执行前确认 OSS 对象存在
     * @return 校验失败的统一响应，全部通过时为 null
     */
    public ResponseResult<?> validateRequest(PolicyOcrRequest request, Long enterpriseId, boolean checkObject) {
        if (request == null || request.workorderId() == null || request.fileId() == null) {
            return new ResponseResult<>(400, "工单和文件 ID 不能为空");
        }
        Workorder workorder = workorderMapper.selectById(request.workorderId());
        if (workorder == null || !enterpriseId.equals(workorder.getEnterpriseId())
                || Integer.valueOf(1).equals(workorder.getIsDelete())) {
            return new ResponseResult<>(404, "工单不存在");
        }
        SystemFile file = systemFileMapper.selectById(request.fileId());
        if (file == null || !enterpriseId.equals(file.getEnterpriseId()) || Integer.valueOf(1).equals(file.getIsDelete())) {
            return new ResponseResult<>(404, "文件不存在");
        }
        String name = file.getFileName() == null ? "" : file.getFileName().toLowerCase(java.util.Locale.ROOT);
        boolean pdf = name.endsWith(".pdf");
        boolean image = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
            || name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".tif") || name.endsWith(".tiff");
        if (!pdf && !image) return new ResponseResult<>(400, "仅支持 PDF 或图片保单");
        if (checkObject) {
            try {
                if (!OSSUtil.objectExists(file.getPath())) return new ResponseResult<>(400, "文件尚未上传完成");
            } catch (Exception ex) {
                return new ResponseResult<>(502, "无法确认文件上传状态，请稍后重试");
            }
        }
        return null;
    }
}
