package com.text.libnetwork;


import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.IntDef;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public abstract class Request<T,R> {
    protected String mUrl;
    protected HashMap<String,String> headers = new HashMap<>();
    protected HashMap<String,Object> params = new HashMap<>();

    public static final int CACHE_ONLY = 1;//只访问缓存，即便本地缓存不存在，也不会发起网络请求
    public static final int CACHE_FIRST = 2;//先访问缓存，同时发起网络请求，成功后缓存到本地
    public static final int NET_ONLY = 3;//只访问服务器，不做任何存储
    public static final int NET_CACHE = 4;
    private String cacheKey;
    private Type mType;
    private Class mClaz;

    @IntDef({CACHE_ONLY,CACHE_FIRST,NET_CACHE,NET_ONLY})
    public @interface  CacheStrategy{

    }
    public Request(String url){
        this.mUrl = url;

    }
    public R addHeader(String key,String value){
        headers.put(key,value);
        return (R)this;
    }
    public R addParam(String key,String value){
        try {
            Field field = value.getClass().getField("TYPE");
            Class claz = (Class) field.get(null);
            if (claz.isPrimitive()){
                params.put(key,value);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return (R)this;
    }
    public R cacheKey(String key){
        this.cacheKey = key;
        return (R) this;
    }

    public R responseType(Type type){
        mType = type;
        return (R) this;
    }

    public R responseType(Class claz){
        mClaz = claz;
        return (R) this;
    }
    public void execult(final JsonCallback<T> callback){
        getCall().enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                ApiResponse<T> response = new ApiResponse<>();
                response.message =e.getMessage();
                callback.onError(response);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ApiResponse<T> apiResponse  =  parseResponse(response,callback);
                if (!apiResponse.success){
                    callback.onError(apiResponse);
                }else {
                    callback.onSuccess(apiResponse);
                }
            }
        });
    }

    protected  ApiResponse<T> parseResponse(Response response, JsonCallback<T> callback){
        String message = null;
        int status = response.code();
        boolean success = response.isSuccessful();
        ApiResponse<T> result = new ApiResponse<>();
        Convert convert = ApiService.sConvert;
        try{
            String content = response.body().toString();
            if (success){
                if (callback!=null){
                    ParameterizedType type = (ParameterizedType) callback.getClass().getGenericSuperclass();
                    Type argument = type.getActualTypeArguments()[0];
                    result.body = (T) convert.convert(content, argument);
                }else if (mType != null){
                    result.body = (T) convert.convert(content,mType);
                }else if (mClaz != null){
                    result.body = (T) convert.convert(content,mClaz);
                }else {
                    Log.e("request","paresRespon 无法解析");
                }
            }else {
                message = content;
            }
        }catch (Exception e){
        message = e.getMessage();
        success  = false;
        }
        result.success = success;
        result.status = status;
        result.message = message;
    return result;
    };

    protected Call getCall(){
        okhttp3.Request.Builder builder =   new okhttp3.Request.Builder();
        addHeaders(builder);
        okhttp3.Request request = generateRequest(builder);
        okhttp3.Call call = ApiService.okHttpClient.newCall(request);
        return call;
    }

    protected abstract okhttp3.Request generateRequest(okhttp3.Request.Builder builder);

    protected  void addHeaders(okhttp3.Request.Builder builder){
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(),entry.getValue());

        }
    }


    public ApiResponse<T> execult(){
            try {

                Response response = getCall().execute();
                ApiResponse<T> result = parseResponse(response,null);
                return result;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
    }
}
