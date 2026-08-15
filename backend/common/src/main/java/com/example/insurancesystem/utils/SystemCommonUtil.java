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

/**
 * 汇总外部云凭证读取、业务编号生成和当前 Spring Security 用户上下文访问等跨模块基础能力。
 */
public class SystemCommonUtil {

    private static final String ENV_FILE_NAME = ".env";

    private static final Properties ENV_PROPERTIES = loadEnvProperties();

    /**
     * 获取阿里云 AccessKey ID，优先读取已加载的 .env，缺失时回退到系统环境变量。
     */
    public static String getAccessKeyId(){
        return getRequiredConfig("ACCESS_KEY_ID");
    }

    /**
     * 获取阿里云 AccessKey Secret，缺失配置时快速失败而不创建无效云客户端。
     */
    public static String getAccessKeySecret(){
        return getRequiredConfig("ACCESS_KEY_SECRET");
    }

    /**
     * 在类初始化时读取已解析到的 .env 文件；文件不存在视为使用系统环境变量，存在但无法读取则阻止启动，
     * 避免静默使用空凭证导致运行期云服务请求失败。
     */
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

    /**
     * 依次查找当前工作目录及运行代码所在目录旁的 .env，兼容 IDE 启动和打包 JAR 部署两种目录结构。
     */
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

    /**
     * 读取必填配置并清理两端空白；.env 优先于系统环境变量，两处均为空时抛出带配置名的启动异常。
     */
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

    /**
     * 使用统一 AccessKey 配置创建阿里云默认凭证客户端，供 OCR 等 SDK 通过凭证链访问服务。
     */
    public static Client getCredentialClient() {
        Config credentialConfig = new Config();
        credentialConfig.setType("access_key");
        credentialConfig.setAccessKeyId(getAccessKeyId());
        credentialConfig.setAccessKeySecret(getAccessKeySecret());
        return new Client(credentialConfig);
    }

    /**
     * 从随机 UUID 生成十位大写业务编号候选。数据库唯一约束和 UniqueCodeRetryUtil 提供最终唯一性保证，
     * 因此该方法只负责低碰撞候选而不承诺单独调用绝对唯一。
     * @return 十位大写业务编号候选
     */
    public static String buildCode(){
        return UUID.randomUUID().toString()
                .replace("-", "")
                .toUpperCase()
                .substring(0, 10);
    }

    /**
     * 从当前 Spring Security LoginUser 获取操作用户 ID；无认证上下文时保留历史系统用户 ID 1，
     * 供内部维护任务记录默认操作人，普通 Web 请求应已由安全链完成认证。
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

    /**
     * 返回当前认证用户的权限代码集合；无认证上下文时返回可修改的空集合，表示不具备任何权限。
     */
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

    /**
     * 判断当前用户权限集合是否包含指定权限代码，统一供非注解式业务分支复用。
     */
    public static boolean hasPerm(String perm){
        List<String> perms = getNowUserPerms();
        return perms.contains(perm);
    }

    /**
     * 本地观察业务编号格式和随机性的调试入口，不参与应用运行。
     */
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++){
            System.out.println(buildCode());
        }
    }

}
