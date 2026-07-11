package vip.xiaonuo.fscloud.modular.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import vip.xiaonuo.fscloud.core.base.RecordedEntity;

@Data
@TableName("fs_model")
public class FsModel extends RecordedEntity {
    /**
     * 模型名称
     */
    private String name;
    /**
     * 模型描述
     */
    private String description;
    /**
     *  MODEL_SHARE_PRIVATE(1,"MODEL_SHARE_PRIVATE","私有模型"),
     *  MODEL_SHARE_FACTORY(2,"MODEL_SHARE_FACTORY","工厂内共享模型"),
     *  MODEL_SHARE_COMPANY(3,"MODEL_SHARE_COMPANY","公司内共享模型"),
     *  MODEL_SHARE_PUBLIC(4,"MODEL_SHARE_PUBLIC","公开模型");
     */
    private String modelPermission;
    /**
     * 模型状态 是否启用
     */
    private String status;
    /**
     * 模型版本
     */
    private String version;
    /**
     * 父ID
     */
    private Long parentId;
    /**
     * 模型引擎
     */
    private String engineVersion;
    /**
     * 模型用途
     */
    private String modelUsage;
    /**
     * 模型输出描述
     */
    private String outputDes;
    /**
     * 模型输入描述
     */
    private String inputDes;
    /**
     * 机构ID
     */
    private Long accountId;
    /**
     * 上传用户id
     */
    private Long userId;
    /**
     * 上传用户的昵称
     */
    private String userName;
    /**
     * 上传时所属机构昵称
     */
    private String accountName;
    /**
     * 模型是否可供其他人下载1可以下载，2不可以下载
     */
    private Integer canDownload;

    /**
     * 缩略图地址（预留字段，现在只是通过计算算出来的路径）
     * static/accountId/userId/fsName.jpg
     */
    private String image;

    /**
     * model 文件
     */
    private String model;


}
