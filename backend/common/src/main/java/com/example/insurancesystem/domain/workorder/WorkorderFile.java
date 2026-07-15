package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName(value = "biz_workorder_file")
@AllArgsConstructor
@NoArgsConstructor
public class WorkorderFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    private Long workorderId;

    private Long fileId;

    @TableField(exist = false)
    private String path;

    @TableField(exist = false)
    private String fileName;

    @TableField("file_type")
    private String type;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;
}
