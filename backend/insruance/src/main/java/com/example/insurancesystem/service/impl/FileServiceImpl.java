package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.mapper.SystemFileMapper;
import com.example.insurancesystem.service.FileService;
import com.example.insurancesystem.utils.OSSUtil;
import com.example.insurancesystem.utils.SystemCommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private SystemFileMapper systemFileMapper;

    @Override
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

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "application/octet-stream";
        }
        return contentType.trim();
    }

    private SystemFile buildSystemFile(String fileName, String objectName) {
        SystemFile systemFile = new SystemFile();
        systemFile.setId(null);
        systemFile.setEnterpriseId(1L);
        systemFile.setFileName(fileName);
        systemFile.setPath(objectName);
        systemFile.setIsLinked(0);
        systemFile.setIsDelete(0);
        systemFile.setUpdateBy(SystemCommonUtil.getNowUserId());
        return systemFile;
    }

    private String buildObjectName(String originalFileName) {
        String objectName = UUID.randomUUID() + originalFileName;
        if (objectName.length() > 200) {
            objectName = objectName.substring(objectName.length() - 200);
        }
        return objectName;
    }
}
