package com.text.libnetwork;

//返回体的一个包装
public class ApiResponse<T> {
    public  boolean success;
    public  int status;
    public String message;
    public T body;
}
