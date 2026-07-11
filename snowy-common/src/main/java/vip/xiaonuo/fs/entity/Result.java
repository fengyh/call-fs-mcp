package vip.xiaonuo.fs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import vip.xiaonuo.fs.enums.ResultCode;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据响应对象
 *    {
 *      success ：是否成功
 *      code    ：返回码
 *      message ：返回信息
 *      //返回数据
 *      data：  ：{
 *
 *      }
 *    }
 */
@Data
@NoArgsConstructor // 需要保留 否则 OM 反序列化会出问题
@AllArgsConstructor
public class Result<T> implements Serializable {

    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 返回码 @Link ResultCode
     */
    private Integer code;
    /**
     * 返回信息
     */
    private String message;
    /**
     * 返回数据
     */
    private T data;

    public Result(ResultCode rc) {
        this(rc, null);
    }

    public Result(ResultCode rc, T data) {
        this(rc.code, data, rc.message, rc.success);
    }

    public Result(Integer code, T data, String message, boolean success) {
        this.code = code;
        this.data = data;
        this.message = message;
        this.success = success;
    }

    public Result<?> message(String message) {
        this.setMessage(message);
        return this;
    }

    public Result<?> code(Integer code) {
        this.setCode(code);
        return this;
    }

    public static Result<?> judge(boolean status){
        return status?Result.success():Result.error();
    }

    /**
     * 设置单一对象为返回值
     * @param key 返回对象 key 值
     * @param value 返回对象值
     * @return 返回当前result
     */
    public Result data(String key, Object value) {
        if (data == null || !(data instanceof Map)) {
            data = (T) new LinkedHashMap<>();
        }
        Map map = (Map) data;
        map.put(key, value);
        return this;
    }

    /**
     * 重设整个返回值
     * @param data 返回值
     * @return 当前 result
     */
    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

    ////////////////////////////////////////////////////
    // static method

    public static Result success(){
        return new Result<>(ResultCode.SUCCESS);
    }

    public static  Result error(){
        return new Result<>(ResultCode.SERVER_ERROR);
    }

    public static Result fail(){
        return new Result<>(ResultCode.FAIL);
    }

    public static <T> Result<T> success(T data){
        return new Result<T>(ResultCode.SUCCESS).data(data);
    }

    public static <T> Result<T> error(T data){
        return new Result<T>(ResultCode.SERVER_ERROR).data(data);
    }

    public static <T> Result<T> fail(T data){
        return new Result<T>(ResultCode.FAIL).data(data);
    }
}
