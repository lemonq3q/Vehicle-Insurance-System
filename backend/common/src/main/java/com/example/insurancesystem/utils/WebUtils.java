package com.example.insurancesystem.utils;


import com.alibaba.excel.EasyExcel;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 直接写出非控制器响应的 Web 工具，供安全过滤器返回 JSON 以及列表接口导出 Excel。
 */
public class WebUtils {
    /**
     * 将已经序列化的 JSON 写入响应，统一使用 UTF-8 和 application/json；该方法用于过滤器阶段，
     * 此时无法依赖 Spring MVC 的返回值转换器。写出异常被记录，返回值保留历史 String 契约。
     */
    public static String renderString(HttpServletResponse response, String string){
        try{
            response.setStatus(200);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().print(string);
        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 使用 EasyExcel 将数据按指定注解模型写入响应流，设置兼容中文的 RFC 5987 文件名并禁止缓存旧导出。
     * 写流失败转换为运行时异常交给全局异常链处理。
     */
    public static void renderExcel(HttpServletResponse response, List<?> data, Class<?> clazz) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("机构数据导出_" + System.currentTimeMillis(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            EasyExcel.write(response.getOutputStream(), clazz)
                    .sheet("表1")
                    .doWrite(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
