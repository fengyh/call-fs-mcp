/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 *
 * Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *
 * 1.请不要删除和修改根目录下的LICENSE文件。
 * 2.请不要删除和修改Snowy源码头部的版权声明。
 * 3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 * 4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 * 5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 * 6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
package vip.xiaonuo.fscloud.modular.modelfile.service;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import vip.xiaonuo.fscloud.modular.entity.FsModel;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileAddParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileEditParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileIdParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFilePageParam;
import java.io.Serializable;
import java.io.IOException;
import java.util.List;

/**
 * 仿真模型库Service接口
 *
 * @author fengyuhe
 * @date  2026/06/04 21:42
 **/
public interface FsModelFileService extends IService<FsModelFile> {

    /**
     * 获取仿真模型库分页
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    Page<FsModelFile> page(FsModelFilePageParam fsModelFilePageParam);

    /**
     * 添加仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    void add(FsModelFileAddParam fsModelFileAddParam);

    /**
     * 编辑仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    void edit(FsModelFileEditParam fsModelFileEditParam);

    /**
     * 删除仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    void delete(List<FsModelFileIdParam> fsModelFileIdParamList);

    /**
     * 获取仿真模型库详情
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    FsModelFile detail(FsModelFileIdParam fsModelFileIdParam);

    /**
     * 获取仿真模型库详情
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     **/
    FsModelFile queryEntity(Serializable id);

    /**
     * 下载仿真模型库导入模板
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    void downloadImportTemplate(HttpServletResponse response) throws IOException;

    /**
     * 导入仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     **/
    JSONObject importData(MultipartFile file);

    /**
     * 导出仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    void exportData(List<FsModelFileIdParam> fsModelFileIdParamList, HttpServletResponse response) throws IOException;


    void processUploadFile(MultipartFile file, FsModel fsModelInfo);

    /**
     * 根据 ID 列表批量查询记录（用于前端定时轮询刷新）
     *
     * @author fengyuhe
     * @date  2026/06/05
     */
    List<FsModelFile> listByIds(List<Long> ids);

}
