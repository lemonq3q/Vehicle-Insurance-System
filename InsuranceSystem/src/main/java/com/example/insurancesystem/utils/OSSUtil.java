package com.example.insurancesystem.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OSSUtil {

    @Autowired
    private RedisCache redisCache;

    private final static String BUCKET_NAME = "lemonqwq";

    public static boolean uploadFile(MultipartFile file, String objectName) {
        OSS ossClient = OSSClientSingleton.getInstance();
        try {
            String[] parts = objectName.split("/");
            String fileName = parts[parts.length - 1];
            String prefix = UUID.randomUUID().toString();
            File tmpFile = File.createTempFile(prefix, fileName);
            file.transferTo(tmpFile);
            PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, objectName, tmpFile);
            // 如果需要上传时设置存储类型和访问权限，请参考以下示例代码。
            // ObjectMetadata metadata = new ObjectMetadata();
            // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            // metadata.setObjectAcl(CannedAccessControlList.Private);
            // putObjectRequest.setMetadata(metadata);

            // 上传文件。
            PutObjectResult result = ossClient.putObject(putObjectRequest);

            // 删除临时文件
            tmpFile.deleteOnExit();
            return true;
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
            return false;
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
            return false;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static String generatePutSignedUrl(String objectName, long expire, String contentType) {
        OSS ossClient = OSSClientSingleton.getInstance();
        try {
            Date expiration = new Date(new Date().getTime() + expire);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(BUCKET_NAME, objectName, HttpMethod.PUT);
            request.setExpiration(expiration);
            if (contentType != null && !contentType.trim().isEmpty()) {
                request.setContentType(contentType.trim());
            }
            URL url = ossClient.generatePresignedUrl(request);
            return url == null ? null : url.toString();
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
            return null;
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
            return null;
        }
    }

    public String getTmpUrl(String objectName) {
        String tmpUrl = redisCache.getCacheObject("oss:" + objectName);
        if (tmpUrl != null && !tmpUrl.isEmpty()){
            return tmpUrl;
        }

        OSS ossClient = OSSClientSingleton.getInstance();
        try {
            // 设置预签名URL过期时间，单位为毫秒。设置过期时间为24小时。
            Date expiration = new Date(new Date().getTime() + 60 * 60 * 24 * 1000L);
            // 统一使用 GeneratePresignedUrlRequest，避免 V4 签名下 GET/PUT 生成方式不一致。
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(BUCKET_NAME, objectName, HttpMethod.GET);
            request.setExpiration(expiration);
            URL url = ossClient.generatePresignedUrl(request);
            tmpUrl = url.toString();
            redisCache.setCacheObject("oss:" + objectName, tmpUrl, 23, TimeUnit.HOURS);
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        }
        return tmpUrl;
    }

    /**
     * 根据OSS文件路径删除文件
     * @param objectName OSS中的文件路径（例如：avatar/2025/xxx.jpg）
     * @return 删除成功返回true，失败返回false
     */
    public static boolean deleteFile(String objectName) {
        OSS ossClient = OSSClientSingleton.getInstance();
        try {
            // 执行删除文件
            ossClient.deleteObject(BUCKET_NAME, objectName);
            return true;
        } catch (OSSException oe) {
            System.out.println("OSSException 删除文件失败：");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            return false;
        } catch (ClientException ce) {
            System.out.println("ClientException 删除文件失败：");
            System.out.println("Error Message:" + ce.getMessage());
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
