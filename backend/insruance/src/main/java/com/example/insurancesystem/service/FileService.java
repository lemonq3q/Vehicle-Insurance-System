package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 定义车险附件的服务端上传、OSS 上传和浏览器直传签名能力。
 */
public interface FileService {
    /**
     * 处理历史兼容的应用服务器文件上传。
     */
    ResponseResult uploadFile(MultipartFile file);

    /**
     * 将 MultipartFile 写入私有 OSS 并生成系统文件记录。
     */
    ResponseResult ossUploadFile(MultipartFile file);

    /**
     * 为原始文件名和类型创建 OSS 直传地址及待关联文件元数据。
     */
    ResponseResult createOssUploadSign(String fileName, String contentType);
}
