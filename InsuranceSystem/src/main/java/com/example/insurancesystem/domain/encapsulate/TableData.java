package com.example.insurancesystem.domain.encapsulate;

import com.github.pagehelper.PageInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
