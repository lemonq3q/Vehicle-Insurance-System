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
/**
 * 封装车险文件在阿里云 OSS 的服务端上传、浏览器直传签名、临时下载地址缓存和删除操作。
 * 所有对象使用同一私有 Bucket，访问由短期预签名 URL 控制而不是公开读写权限。
 */
public class OSSUtil {

    @Autowired
    private RedisCache redisCache;

    private final static String BUCKET_NAME = "lemonqwq";

    /**
     * 将 MultipartFile 暂存到本地临时文件后上传到指定 OSS 对象键。供应商拒绝、网络异常或本地文件异常返回 false，
     * 调用方据此决定事务与错误提示；成功后临时文件登记为 JVM 退出时删除。
     */
    public static boolean uploadFile(MultipartFile file, String objectName) {
        OSS ossClient = OSSClientSingleton.getInstance();
        try {
            String[] parts = objectName.split("/");
            String fileName = parts[parts.length - 1];
            String prefix = UUID.randomUUID().toString();
            File tmpFile = File.createTempFile(prefix, fileName);
            file.transferTo(tmpFile);
            PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, objectName, tmpFile);
            PutObjectResult result = ossClient.putObject(putObjectRequest);

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

    /**
     * 生成指定有效毫秒数的 PUT 预签名 URL，供前端不经过应用服务器直接上传文件。
     * contentType 非空时写入签名条件，客户端上传必须使用同一类型；生成失败返回 null。
     */
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

    /**
     * 获取私有对象的 24 小时 GET 预签名地址，并在 Redis 缓存 23 小时。缓存提前一小时失效，
     * 避免客户端取到即将过期的 URL；OSS 调用失败时返回当前空值，由上层决定是否重试。
     */
    public String getTmpUrl(String objectName) {
        String tmpUrl = redisCache.getCacheObject("oss:" + objectName);
        if (tmpUrl != null && !tmpUrl.isEmpty()){
            return tmpUrl;
        }

        OSS ossClient = OSSClientSingleton.getInstance();
        try {
            Date expiration = new Date(new Date().getTime() + 60 * 60 * 24 * 1000L);
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
     * 根据完整 OSS 对象键删除私有文件。OSS 服务拒绝、网络失败或其他异常均返回 false，成功返回 true；
     * 调用方应在数据库记录处理时根据返回值决定是否继续，避免产生文件与业务记录不一致。
     * @param objectName OSS中的文件路径（例如：avatar/2025/xxx.jpg）
     * @return 删除成功返回true，失败返回false
     */
    public static boolean deleteFile(String objectName) {
        OSS ossClient = OSSClientSingleton.getInstance();
        try {
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
