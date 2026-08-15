package com.example.insurancesystem.domain.encapsulate;

import com.github.pagehelper.PageInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 通用分页列表响应，封装当前页业务数据和符合查询条件的总记录数，供各前端分页组件统一消费。
 */
public class TableData<T> {

    private Long total;

    private List<T> table;

    public TableData(List<T> data) {
        this(new PageInfo<>(data));
    }

    public TableData(PageInfo<T> pageInfo) {
        this.total = pageInfo.getTotal();
        this.table = pageInfo.getList();
    }

}
