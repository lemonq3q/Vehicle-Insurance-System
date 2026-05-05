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

import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private SystemFileMapper systemFileMapper;

    @Override
    public ResponseResult uploadFile(MultipartFile file) {
        String fileName = UUID.randomUUID() + file.getOriginalFilename();
        if(fileName.length() > 200){
            fileName = fileName.substring(0, 200);
        }
        if (OSSUtil.uploadFile(file, fileName)) {
            SystemFile systemFile = new SystemFile();
            systemFile.setId(null);
            systemFile.setFileName(file.getOriginalFilename());
            systemFile.setPath(fileName);
            systemFile.setUpdateBy(SystemCommonUtil.getNowUserId());
            systemFileMapper.insert(systemFile);
            return new ResponseResult(200, systemFile);
        } else {
            return new ResponseResult(500, "上传失败");
        }
    }

    @Override
    public ResponseResult ossUploadFile(MultipartFile file) {
        String fileName = UUID.randomUUID() + file.getOriginalFilename();
        if(fileName.length() > 200){
            fileName = fileName.substring(0, 200);
        }
        if (OSSUtil.uploadFile(file, fileName)) {
            SystemFile systemFile = new SystemFile();
            systemFile.setId(null);
            systemFile.setFileName(fileName);
            systemFile.setPath(fileName);
            systemFile.setUpdateBy(SystemCommonUtil.getNowUserId());
            systemFileMapper.insert(systemFile);
            return new ResponseResult(200, systemFile);
        } else {
            return new ResponseResult(500, "上传失败");
        }
    }
}
