package vip.xiaonuo.auth.modular.login.fsdto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResult {

    /** 手机号 */
    private String mobile;

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** Token */
    private String token;

}
