package com.example.insurancesystem.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "sys_file")
/**
 * 系统文件元数据实体，记录 OSS 对象键、原始文件名、业务归属和上传状态，不直接保存文件二进制内容。
 */
public class SystemFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    private String path;

    private String fileName;

    private Integer isLinked;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;
}
