package com.example.insurancesystem.coordinator.backup;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 维护备份独立配置，默认关闭；凭证仅通过权限受限的MySQL客户端文件传递，不拼接到命令行。 */
@Component
@ConfigurationProperties(prefix="maintenance.backup")
public class BackupProperties {
    private boolean enabled=false;
    private String root="/var/data/MaintenanceCoordinator/mysql-backup";
    private String database="insurance_saas";
    private String instance="prod";
    private String bucket="lemonqwq";
    private String prefix="database-backup";
    private String defaultsFile="";
    private String mysql="mysql";
    private String mysqldump="mysqldump";
    private String mysqlbinlog="mysqlbinlog";
    private String sourceDataOption="--source-data=2";
    private String monitorUrl="http://localhost:8083";
    private int timeoutSeconds=1800;
    private int retentionDays=30;
    /** 读取备份配置 enabled，不携带数据库密码。 */
    public boolean isEnabled(){return enabled;}
    /** 由Spring配置绑定设置 enabled，启用时统一校验。 */
    public void setEnabled(boolean value){this.enabled=value;}
    /** 读取备份配置 root，不携带数据库密码。 */
    public String getRoot(){return root;}
    /** 由Spring配置绑定设置 root，启用时统一校验。 */
    public void setRoot(String value){this.root=value;}
    /** 读取备份配置 database，不携带数据库密码。 */
    public String getDatabase(){return database;}
    /** 由Spring配置绑定设置 database，启用时统一校验。 */
    public void setDatabase(String value){this.database=value;}
    /** 读取备份配置 instance，不携带数据库密码。 */
    public String getInstance(){return instance;}
    /** 由Spring配置绑定设置 instance，启用时统一校验。 */
    public void setInstance(String value){this.instance=value;}
    /** 读取备份配置 bucket，不携带数据库密码。 */
    public String getBucket(){return bucket;}
    /** 由Spring配置绑定设置 bucket，启用时统一校验。 */
    public void setBucket(String value){this.bucket=value;}
    /** 读取备份配置 prefix，不携带数据库密码。 */
    public String getPrefix(){return prefix;}
    /** 由Spring配置绑定设置 prefix，启用时统一校验。 */
    public void setPrefix(String value){this.prefix=value;}
    /** 读取备份配置 defaultsFile，不携带数据库密码。 */
    public String getDefaultsFile(){return defaultsFile;}
    /** 由Spring配置绑定设置 defaultsFile，启用时统一校验。 */
    public void setDefaultsFile(String value){this.defaultsFile=value;}
    /** 读取备份配置 mysql，不携带数据库密码。 */
    public String getMysql(){return mysql;}
    /** 由Spring配置绑定设置 mysql，启用时统一校验。 */
    public void setMysql(String value){this.mysql=value;}
    /** 读取备份配置 mysqldump，不携带数据库密码。 */
    public String getMysqldump(){return mysqldump;}
    /** 由Spring配置绑定设置 mysqldump，启用时统一校验。 */
    public void setMysqldump(String value){this.mysqldump=value;}
    /** 读取备份配置 mysqlbinlog，不携带数据库密码。 */
    public String getMysqlbinlog(){return mysqlbinlog;}
    /** 由Spring配置绑定设置 mysqlbinlog，启用时统一校验。 */
    public void setMysqlbinlog(String value){this.mysqlbinlog=value;}
    /** 读取备份配置 sourceDataOption，不携带数据库密码。 */
    public String getSourceDataOption(){return sourceDataOption;}
    /** 由Spring配置绑定设置 sourceDataOption，启用时统一校验。 */
    public void setSourceDataOption(String value){this.sourceDataOption=value;}
    /** 读取备份配置 monitorUrl，不携带数据库密码。 */
    public String getMonitorUrl(){return monitorUrl;}
    /** 由Spring配置绑定设置 monitorUrl，启用时统一校验。 */
    public void setMonitorUrl(String value){this.monitorUrl=value;}
    /** 读取备份配置 timeoutSeconds，不携带数据库密码。 */
    public int getTimeoutSeconds(){return timeoutSeconds;}
    /** 由Spring配置绑定设置 timeoutSeconds，启用时统一校验。 */
    public void setTimeoutSeconds(int value){this.timeoutSeconds=value;}
    /** 读取备份配置 retentionDays，不携带数据库密码。 */
    public int getRetentionDays(){return retentionDays;}
    /** 由Spring配置绑定设置 retentionDays，启用时统一校验。 */
    public void setRetentionDays(int value){this.retentionDays=value;}
}
