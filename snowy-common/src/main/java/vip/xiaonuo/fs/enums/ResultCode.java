package vip.xiaonuo.fs.enums;

/**
 * 公共的返回码
 *    返回码code：
 *      成功：10000
 *      失败：20001
 *      未登录：20002
 *      未授权：20003
 *      抛出异常：99999
 */
public enum ResultCode {
    //---系统错误返回码-----
    SERVER_ERROR(false,99999,"抱歉，系统繁忙，请稍后重试！"),
    FAIL(false,20000,"操作失败"),
    UNAUTHENTICATED(false,20002,"您还未登录"),
    UNAUTHORISE(false,20003,"权限不足"),
    CREATION_FAILED(false,20005,"创建对象失败，请仔细检查提交属性，某些属性必须唯一（例如code，name等）！"),
    CREATION_EXIST(false, 2006, "创建对象已存在，请仔细检查核对数据！"),
    USERNAME_EXIT(false, 2006, "当前输入用户名已存在！"),
    MOBILE_EXIST(false, 2006, "当前输入手机号已存在！"),
    USER_DISABLED(false, 2006, "当前输入用户名已禁用！"),
    //---用户操作返回码  2xxxx----
    MOBILE_OR_PASSWORD_ERROR(false,30001,"用户名或密码错误"),
    CAPTCHA_ERROR(false, 30002, "验证码错误"),
    AUTHING_ERROR(false, 30010, "AUTHING 系统集成验证失败！"),
    AUTHING_USER_NOT_REG(false, 30011, "AUTHING 用户没有在本系统中注册！"),
    PASSWORD_DECRYPT_ERROR(false, 30012, "密码解密失败"),

    //---用户操作返回码  3xxxx----
    NOT_FOUND_OBJECT(false, 40001, "未找到请求对象"),
    DEPT_USER_EXIT(false, 40002, "当前部门下存在用户，请解除用户关系后在删除部门"),

    //---权限操作返回码----
    CAN_NOT_DOWNLOAD(false, 50001, "当前模型不允许下载"),
    DOWNLOAD_ERROR(false, 50002, "模型下载错误"),
    //---其他操作返回码----



    /////////////////////////////////////////
    // 业务操作作返回码  5xxxx


    ///////////////////////////////////////////////////
    // 最后一个 为正常返回
    /**
     * 正常返回
     */
    CODING (true,100001,"成功！!! BUT 系统正在构件中，请稍后访问！！！"),
    SUCCESS(true,10000,"操作成功！");

    ///////////////////////////////////////////////////////
    // 结构定义

    /**
     * 操作是否成功
     */
    public final boolean success;
    /**
     * 返回代码代码
     */
    public final int code;
    /**
     * 提示信息
     */
    public final String message;

    /**
     * 私有构造函数
     * @param success 是否成功
     * @param code 操作代码
     * @param message 消息
     */
    ResultCode(boolean success, int code, String message){
        this.success = success;
        this.code = code;
        this.message = message;
    }

    /**
     * @return 当前操作是否成功
     */
    public boolean success() {
        return success;
    }

    /**
     * @return 当前操作返回代码
     */
    public int code() {
        return code;
    }

    /**
     *
     * @return 当前操作返回信息
     */
    public String message() {
        return message;
    }

}
