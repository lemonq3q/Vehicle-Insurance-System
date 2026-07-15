package com.example.insurancesystem.utils;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;
import com.example.insurancesystem.domain.authenticate.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class SystemCommonUtil {

    private static final String ENV_FILE_NAME = ".env";

    private static final Properties ENV_PROPERTIES = loadEnvProperties();

    public static String getAccessKeyId(){
        return getRequiredConfig("ACCESS_KEY_ID");
    }

    public static String getAccessKeySecret(){
        return getRequiredConfig("ACCESS_KEY_SECRET");
    }

    private static Properties loadEnvProperties() {
        Properties properties = new Properties();
        Path envPath = resolveEnvPath();
        if (envPath == null) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(envPath)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("读取 .env 配置失败: " + envPath.toAbsolutePath(), e);
        }
    }

    private static Path resolveEnvPath() {
        Path workDirEnv = Paths.get(System.getProperty("user.dir"), ENV_FILE_NAME);
        if (Files.exists(workDirEnv)) {
            return workDirEnv;
        }

        try {
            Path jarDir = Paths.get(SystemCommonUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParent();
            if (jarDir != null) {
                Path jarDirEnv = jarDir.resolve(ENV_FILE_NAME);
                if (Files.exists(jarDirEnv)) {
                    return jarDirEnv;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String getRequiredConfig(String key) {
        String value = ENV_PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少配置项: " + key + "，请在 .env 文件或系统环境变量中配置");
        }
        return value.trim();
    }

    public static Client getCredentialClient() {
        Config credentialConfig = new Config();
        credentialConfig.setType("access_key");
        credentialConfig.setAccessKeyId(getAccessKeyId());
        credentialConfig.setAccessKeySecret(getAccessKeySecret());
        return new Client(credentialConfig);
    }

    /**
     * 未确定算法，暂时用时间戳代替
     * @return 不重复的code
     */
    public static String buildCode(){
        return UUID.randomUUID().toString()
                .replace("-", "")
                .toUpperCase()
                .substring(0, 10);
    }

    /**
     * 获取进行操作的用户id
     * @return 当前上下文认证的用户id
     */
    public static Long getNowUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null){
            LoginUser nowUser = (LoginUser) authentication.getPrincipal();
            return nowUser.getUser().getId();
        }
        else{
            return 1L;
        }
    }

    public static List<String> getNowUserPerms(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null){
            LoginUser nowUser = (LoginUser) authentication.getPrincipal();
            return nowUser.getPermissions();
        }
        else{
            return new ArrayList<>();
        }
    }

    public static boolean hasPerm(String perm){
        List<String> perms = getNowUserPerms();
        return perms.contains(perm);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++){
            System.out.println(buildCode());
        }
    }

}
