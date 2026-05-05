package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName(value = "workorder_file")
@AllArgsConstructor
@NoArgsConstructor
public class WorkorderFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workorderId;

    private Long fileId;

    @TableField(exist = false)
    private String path;

    @TableField(exist = false)
    private String fileName;

    private String type;

    private Long createTime;

    private Long updateTime;

    private Long updateBy;

    private Integer isDelete;
}
