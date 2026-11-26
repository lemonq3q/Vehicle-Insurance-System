package com.example.insurancesystem.coordinator.backup;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** 原生MySQL工具边界，使用参数数组、固定超时及独立输出文件，不通过shell执行SQL或泄露密码。 */
@Component
public class MySqlBackupTools {
    private final BackupProperties config;
    /** 配置文件由运维提供，包含client连接与TLS设置；备份工具版本需与服务器匹配。 */
    public MySqlBackupTools(BackupProperties config){this.config=config;}
    /** 启用时才验证配置并自动创建根目录及暂存目录，避免默认关闭影响现有服务。 */
    public Path root() throws IOException {
        if(config.getDefaultsFile().isBlank()||!Files.isRegularFile(Path.of(config.getDefaultsFile())))throw new IOException("必须配置有效MySQL defaults-file");
        if(!config.getDatabase().matches("[a-zA-Z0-9_]+")||!config.getInstance().matches("[a-zA-Z0-9_-]+")||config.getTimeoutSeconds()<1||config.getTimeoutSeconds()>3600||config.getRetentionDays()<1)throw new IOException("备份配置不合法");
        Path root=Path.of(config.getRoot()).toAbsolutePath().normalize();Files.createDirectories(root);Files.createDirectories(root.resolve("full"));Files.createDirectories(root.resolve("binlog"));Files.createDirectories(root.resolve("state"));return root;
    }
    /** 只把连接文件路径置于首个选项，不从业务数据源读取或打印密码。 */
    private List<String> command(String executable){return new ArrayList<>(List.of(executable,"--defaults-extra-file="+config.getDefaultsFile()));}
    /** 执行只读元数据或明确授权的FLUSH语句，返回TSV供清单生成使用。 */
    public String query(String sql)throws Exception{
        Path result=Files.createTempFile(root().resolve("state"),"query-",".txt");
        try{List<String> args=command(config.getMysql());args.addAll(List.of("--batch","--raw","--skip-column-names","-e",sql));run(args,result);return Files.readString(result).trim();}finally{Files.deleteIfExists(result);}
    }
    /** 导出一致性快照及恢复坐标；禁止force忽略错误，结构变更必须与此阶段隔离。 */
    public void dump(Path target)throws Exception{
        if(!Set.of("--source-data=2","--master-data=2").contains(config.getSourceDataOption()))throw new IOException("source-data-option不受支持");
        List<String> args=command(config.getMysqldump());args.addAll(List.of("--single-transaction",config.getSourceDataOption(),"--flush-logs","--routines","--events","--triggers","--hex-blob","--no-tablespaces","--set-gtid-purged=OFF","--databases",config.getDatabase()));run(args,target);
    }
    /** 每次从服务端重新获取完整事件副本，不复制正在写的MySQL数据文件，不使用stop-never无限等待。 */
    public Path binlog(String name,Path directory)throws Exception{
        if(!name.matches("[a-zA-Z0-9_.-]+")||name.contains(".."))throw new IOException("binlog文件名不合法");
        Files.createDirectories(directory);List<String> args=command(config.getMysqlbinlog());args.addAll(List.of("--read-from-remote-server","--raw","--result-file="+directory.toAbsolutePath()+File.separator,name));
        run(args,directory.resolve("capture.stdout"));Path file=directory.resolve(name);if(!Files.isRegularFile(file)||Files.size(file)<4)throw new IOException("binlog副本不完整");
        List<String> verify=new ArrayList<>(List.of(config.getMysqlbinlog(),"--verify-binlog-checksum",file.toString()));run(verify,directory.resolve("verify.txt"));Files.deleteIfExists(directory.resolve("verify.txt"));return file;
    }
    /** 子进程输出重定向以避免管道阻塞；失败仅报告工具名，不将可能敏感的stderr写入请求日志。 */
    protected void run(List<String> args,Path output)throws Exception{
        Path error=Files.createTempFile(output.getParent(),"tool-",".stderr");Process process=null;
        try{ProcessBuilder builder=new ProcessBuilder(args).redirectError(error.toFile());
            if(output.getFileName().toString().equals("verify.txt"))builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);else builder.redirectOutput(output.toFile());
            process=builder.start();
            if(!process.waitFor(config.getTimeoutSeconds(),TimeUnit.SECONDS)){throw new IOException("MySQL备份工具超时");}
            if(process.exitValue()!=0)throw new IOException("MySQL备份工具失败："+Path.of(args.get(0)).getFileName()+"，退出码="+process.exitValue()+"；请检查连接文件、版本与权限");
        }finally{if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}Files.deleteIfExists(error);}
    }
}
