package vip.xiaonuo.fscloud.core.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public abstract class RecordedEntity extends BaseEntity {
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "Asia/Shanghai")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT)
    protected String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE,select = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "Asia/Shanghai")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected LocalDateTime lastModifiedAt;
    @TableField(fill = FieldFill.INSERT_UPDATE,select = true)
    protected String lastModifiedBy;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
