package com.example.insurancesystem.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提供读取 classpath Excel 基础数据的轻量能力，目前用于从单列配置表导入地区或字典值。
 */
public class ExcelUtil {

    /**
     * 使用 EasyExcel 流式读取 classpath 中工作簿的指定列。列号按业务习惯从 1 开始，内部转换为零基索引；
     * 可选择跳过首行表头，缺失单元格仍追加空字符串以保持结果与原始行位置一致。
     * @param filePath 文件路径，如file/test.xlsx
     * @param columnIndex 列号，从 1 开始（1=第一列，2=第二列）
     * @param isReadHeader 是否读取表头（true=读取，false=跳过）
     * @return 该列所有数据 List<String>
     * @throws Exception
     */
    public static List<String> readSingleColumn(
            String filePath,
            int columnIndex,
            boolean isReadHeader
    ) throws Exception {
        ClassPathResource resource = new ClassPathResource(filePath);
        InputStream inputStream = resource.getInputStream();

        List<String> resultList = new ArrayList<>();
        int realIndex = columnIndex - 1;

        EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            /**
             * 逐行提取目标列；配置为不读表头时跳过索引零，其他空行或空单元格以空字符串占位。
             */
            public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
                if (!isReadHeader && context.readRowHolder().getRowIndex() == 0) {
                    return;
                }

                String value = "";
                if (rowData != null) {
                    Object obj = rowData.get(realIndex);
                    if (obj != null) {
                        value = obj.toString().trim();
                    }
                }

                resultList.add(value);
            }

            @Override
            /**
             * 本读取器无需批次收尾操作，结果已经在逐行回调中完整收集。
             */
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().doRead();

        return resultList;
    }
}
