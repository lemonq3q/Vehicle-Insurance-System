package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.mapper.SystemFileMapper;
import com.example.insurancesystem.service.FileService;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.utils.OSSUtil;
import com.example.insurancesystem.utils.SystemCommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
/**
 * 负责车险业务附件的对象存储上传和文件元数据登记。
 * 文件记录会绑定当前企业，后续工单保存时再通过关联状态纳入具体业务数据。
 */
public class FileServiceImpl implements FileService {

    @Autowired
    private SystemFileMapper systemFileMapper;

    @Override
    /**
     * 由服务端接收文件并上传到 OSS，成功后按用户看到的原始文件名保存元数据。
     *
     * @param file 浏览器提交的附件
     * @return 上传成功后的文件记录，或上传失败响应
     */
    public ResponseResult uploadFile(MultipartFile file) {
        String objectName = buildObjectName(file.getOriginalFilename());
        if (OSSUtil.uploadFile(file, objectName)) {
            SystemFile systemFile = buildSystemFile(file.getOriginalFilename(), objectName);
            systemFileMapper.insert(systemFile);
            return new ResponseResult(200, systemFile);
        } else {
            return new ResponseResult(500, "上传失败");
        }
    }

    @Override
    /**
     * 为兼容直接使用 OSS 对象名的业务场景上传文件，文件名与存储路径均记录为对象名。
     *
     * @param file 待上传附件
     * @return 上传成功后的文件记录，或上传失败响应
     */
    public ResponseResult ossUploadFile(MultipartFile file) {
        String objectName = buildObjectName(file.getOriginalFilename());
        if (OSSUtil.uploadFile(file, objectName)) {
            SystemFile systemFile = buildSystemFile(objectName, objectName);
            systemFileMapper.insert(systemFile);
            return new ResponseResult(200, systemFile);
        } else {
            return new ResponseResult(500, "上传失败");
        }
    }

    @Override
    /**
     * 创建客户端直传 OSS 所需的短时 PUT 签名，并预先登记文件元数据。
     * 返回值同时包含签名地址、规范化后的媒体类型和业务后续关联所需的文件记录。
     *
     * @param fileName 用户选择的原始文件名
     * @param contentType 浏览器提供的媒体类型
     * @return 直传参数和文件元数据
     */
    public ResponseResult createOssUploadSign(String fileName, String contentType) {
        // 设置5分钟的过期时间
        Long expire = 5 * 60 * 1000L;
        if (fileName == null || fileName.trim().isEmpty()) {
            return new ResponseResult(400, "文件名不能为空");
        }

        String objectName = buildObjectName(fileName.trim());
        String normalizedContentType = normalizeContentType(contentType);
        String signedUrl = OSSUtil.generatePutSignedUrl(objectName, expire, normalizedContentType);
        if (signedUrl == null || signedUrl.isEmpty()) {
            return new ResponseResult(500, "生成预签名失败");
        }

        SystemFile systemFile = buildSystemFile(fileName.trim(), objectName);
        systemFileMapper.insert(systemFile);

        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("signedUrl", signedUrl);
        data.put("contentType", normalizedContentType);
        return new ResponseResult(200, data);
    }

    /**
     * 规范直传请求的媒体类型；调用方未提供类型时使用通用二进制类型，确保签名参数稳定。
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "application/octet-stream";
        }
        return contentType.trim();
    }

    /**
     * 构建尚未关联具体业务记录的企业文件元数据，并记录当前操作人。
     */
    private SystemFile buildSystemFile(String fileName, String objectName) {
        SystemFile systemFile = new SystemFile();
        systemFile.setId(null);
        systemFile.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        systemFile.setFileName(fileName);
        systemFile.setPath(objectName);
        systemFile.setIsLinked(0);
        systemFile.setIsDelete(0);
        systemFile.setUpdateBy(SystemCommonUtil.getNowUserId());
        return systemFile;
    }

    /**
     * 使用随机 UUID 隔离同名文件；同时限制对象名长度以满足数据库路径字段约束。
     */
    private String buildObjectName(String originalFileName) {
        String objectName = UUID.randomUUID() + originalFileName;
        if (objectName.length() > 200) {
            objectName = objectName.substring(objectName.length() - 200);
        }
        return objectName;
    }
}
