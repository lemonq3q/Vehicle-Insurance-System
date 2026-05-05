package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.BusinessLicense;
import com.example.insurancesystem.domain.IdCard;
import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.VehicleCertificate;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import com.example.insurancesystem.service.FileService;
import com.example.insurancesystem.service.OCRService;
import com.example.insurancesystem.utils.OCRUtil;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class OCRServiceImpl implements OCRService {

    @Autowired
    private FileService fileService;

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private OCRUtil ocrUtil;

    @Override
    public ResponseResult idCardRecognition(MultipartFile file) {
        ResponseResult result = fileService.uploadFile(file);
        if (result.getCode() != 200){
            return result;
        }
        SystemFile systemFile = (SystemFile) result.getData();
        String url = ossUtil.getTmpUrl(systemFile.getPath());
        IdCard idCard = ocrUtil.recognizeIdcard(url);

        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("recognitionData", idCard);
        return new ResponseResult(200, data);
    }

    @Override
    public ResponseResult businessLicenseRecognition(MultipartFile file) {
        ResponseResult result = fileService.uploadFile(file);
        if (result.getCode() != 200){
            return result;
        }
        SystemFile systemFile = (SystemFile) result.getData();
        String url = ossUtil.getTmpUrl(systemFile.getPath());
        BusinessLicense businessLicense = ocrUtil.recognizeBusinessLicense(url);

        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("recognitionData", businessLicense);
        return new ResponseResult(200, data);
    }

    @Override
    public ResponseResult vehicleCertificateRecognition(MultipartFile file) {
        ResponseResult result = fileService.uploadFile(file);
        if (result.getCode() != 200){
            return result;
        }
        SystemFile systemFile = (SystemFile) result.getData();
        String url = ossUtil.getTmpUrl(systemFile.getPath());
        VehicleCertificate vehicleCertificate = ocrUtil.recognizeVehicleCertificate(url);

        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("recognitionData", vehicleCertificate);
        return new ResponseResult(200, data);
    }

    @Override
    public ResponseResult vehicleInvoiceRecognition(MultipartFile file) {
        ResponseResult result = fileService.uploadFile(file);
        if (result.getCode() != 200){
            return result;
        }
        SystemFile systemFile = (SystemFile) result.getData();
        String url = ossUtil.getTmpUrl(systemFile.getPath());
        VehicleInvoice vehicleInvoice = ocrUtil.recognizeVehicleInvoice(url);
        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("recognitionData", vehicleInvoice);
        return new ResponseResult(200, data);
    }

    @Override
    public ResponseResult vehicleLicenseRecognition(MultipartFile file) {
        ResponseResult result = fileService.uploadFile(file);
        if (result.getCode() != 200){
            return result;
        }
        SystemFile systemFile = (SystemFile) result.getData();
        String url = ossUtil.getTmpUrl(systemFile.getPath());
        VehicleLicense vehicleLicense = ocrUtil.recognizeVehicleLicense(url);

        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("recognitionData", vehicleLicense);
        return new ResponseResult(200, data);
    }


}
