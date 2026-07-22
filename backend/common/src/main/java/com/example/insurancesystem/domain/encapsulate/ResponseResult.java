package com.example.insurancesystem.domain.encapsulate;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseResult<T> {
    private Integer code;

    private String msg;

    private T data;

    /** Required by Jackson when a service response is consumed through RestTemplate. */
    public ResponseResult() {
    }

    public ResponseResult(Integer code, String msg){
        this.code = code;
        this.msg = msg;
    }

    public ResponseResult(Integer code, T data){
        this.code = code;
        this.data = data;
    }

    public ResponseResult(Integer code, String msg, T data){
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public Integer getCode(){
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg(){
        return msg;
    }

    public void setMsg(String msg){
        this.msg = msg;
    }

    public T getData(){
        return data;
    }

    public void setData(T data){
        this.data = data;
    }

    @Override
    public String toString() {
        return "ResponseResult{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}
