package com.example.insurancesystem.domain.encapsulate;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * 项目统一接口响应外壳，code 表示业务结果，msg 提供提示，data 承载可选业务数据；null 字段不输出以精简响应。
 */
public class ResponseResult<T> {
    private Integer code;

    private String msg;

    private T data;

    /**
     * 提供 Jackson 和 RestTemplate 反序列化所需的无参构造器。
     */
    public ResponseResult() {
    }

    /**
     * 创建仅包含业务码和提示信息的响应，常用于失败或无数据成功结果。
     */
    public ResponseResult(Integer code, String msg){
        this.code = code;
        this.msg = msg;
    }

    /**
     * 创建包含业务码和数据但不带提示的响应。
     */
    public ResponseResult(Integer code, T data){
        this.code = code;
        this.data = data;
    }

    /**
     * 创建包含业务码、提示和业务数据的完整响应。
     */
    public ResponseResult(Integer code, String msg, T data){
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 返回业务响应码。
     */
    public Integer getCode(){
        return code;
    }

    /**
     * 设置反序列化得到的业务响应码。
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 返回用户可读的响应信息。
     */
    public String getMsg(){
        return msg;
    }

    /**
     * 设置响应提示信息。
     */
    public void setMsg(String msg){
        this.msg = msg;
    }

    /**
     * 返回泛型业务数据。
     */
    public T getData(){
        return data;
    }

    /**
     * 设置泛型业务数据。
     */
    public void setData(T data){
        this.data = data;
    }

    @Override
    /**
     * 输出响应三个核心字段，供日志和调试查看。
     */
    public String toString() {
        return "ResponseResult{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}
