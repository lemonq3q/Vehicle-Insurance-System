package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
/**
 * 车险业务文件入口，支持传统服务端接收上传和浏览器直传 OSS 两种模式，统一由 FileService 生成文件元数据。
 */
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping
    /**
     * 通过应用服务器接收 MultipartFile 并保存，适用于现有兼容上传流程。
     */
    public ResponseResult uploadFile(@RequestParam("file") MultipartFile file) {
        return fileService.uploadFile(file);
    }

    @PostMapping("/oss")
    /**
     * 将上传文件由服务端转存至私有 OSS，并返回可关联工单的 SystemFile 信息。
     */
    public ResponseResult uploadFileTest(@RequestParam("file") MultipartFile file) {
        return fileService.ossUploadFile(file);
    }

    @PostMapping("/oss/sign")
    /**
     * 根据原始文件名和可选 Content-Type 创建 OSS PUT 预签名地址及对象元数据，供前端直接上传以降低服务器带宽压力。
     */
    public ResponseResult createOssUploadSign(@RequestParam("fileName") String fileName,
                                              @RequestParam(value = "contentType", required = false) String contentType) {
        return fileService.createOssUploadSign(fileName, contentType);
    }
}
