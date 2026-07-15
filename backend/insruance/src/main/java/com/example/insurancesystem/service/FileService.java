package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    ResponseResult uploadFile(MultipartFile file);

    ResponseResult ossUploadFile(MultipartFile file);

    ResponseResult createOssUploadSign(String fileName, String contentType);
}
