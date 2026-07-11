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
package vip.xiaonuo.fscloud.modular.modelfile.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.fs.entity.Result;
import vip.xiaonuo.fscloud.modular.entity.FsModel;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileAddParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileEditParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileIdParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFilePageParam;
import vip.xiaonuo.fscloud.modular.modelfile.service.FsModelFileService;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 仿真模型库控制器
 *
 * @author fengyuhe
 * @date  2026/06/04 21:42
 */
@Tag(name = "仿真模型库控制器")
@RestController
@Validated
@Slf4j
public class FsModelFileController {

    @Resource
    private FsModelFileService fsModelFileService;

    /**
     * 上传文件信息与FS文件
     * @param fsModelInfo 这个为FS文件信息
     * @param file 这个为FS模型文件
     * @return 返回是否成功
     */
    @Operation(summary = "上传模型")
    @CommonLog("上传模型")
    @SaCheckPermission("/fscloud/modelfile/upload")
    @PostMapping("/fscloud/modelfile/upload")
    public Result<?> upload(FsModel fsModelInfo, @RequestParam("file") MultipartFile[] file) throws IOException {
        log.info("========== FsModelController.upload method called ==========");
        log.info("fsModelInfo: {}, file count: {}", fsModelInfo, file != null ? file.length : 0);
        // 保存数据库对象
        /*
        if(null==fsModelInfo.getId() || null==fsModelService.getById(fsModelInfo.getId())) {
            fsModelService.save(fsModelInfo);
        } else {
            fsModelService.updateById(fsModelInfo);
        }
        */
        // 存放文件对象
        try {
            //Arrays.stream(file).forEach(e->fsModelService.processUploadFile(e, fsModelInfo));
            Arrays.stream(file).forEach(e->fsModelFileService.processUploadFile(e, fsModelInfo));
        } catch (RuntimeException e) {
            return Result.fail(e);
        }
        return Result.success();
    }


    /**
     * 获取仿真模型库分页
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "获取仿真模型库分页")
    @SaCheckPermission("/fscloud/modelfile/page")
    @GetMapping("/fscloud/modelfile/page")
    public CommonResult<Page<FsModelFile>> page(FsModelFilePageParam fsModelFilePageParam) {
        return CommonResult.data(fsModelFileService.page(fsModelFilePageParam));
    }

    /**
     * 添加仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "添加仿真模型库")
    @CommonLog("添加仿真模型库")
    @SaCheckPermission("/fscloud/modelfile/add")
    @PostMapping("/fscloud/modelfile/add")
    public CommonResult<String> add(@RequestBody @Valid FsModelFileAddParam fsModelFileAddParam) {
        fsModelFileService.add(fsModelFileAddParam);
        return CommonResult.ok();
    }

    /**
     * 编辑仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "编辑仿真模型库")
    @CommonLog("编辑仿真模型库")
    @SaCheckPermission("/fscloud/modelfile/edit")
    @PostMapping("/fscloud/modelfile/edit")
    public CommonResult<String> edit(@RequestBody @Valid FsModelFileEditParam fsModelFileEditParam) {
        fsModelFileService.edit(fsModelFileEditParam);
        return CommonResult.ok();
    }

    /**
     * 删除仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "删除仿真模型库")
    @CommonLog("删除仿真模型库")
    @SaCheckPermission("/fscloud/modelfile/delete")
    @PostMapping("/fscloud/modelfile/delete")
    public CommonResult<String> delete(@RequestBody @Valid @NotEmpty(message = "集合不能为空")
                                                   List<FsModelFileIdParam> fsModelFileIdParamList) {
        fsModelFileService.delete(fsModelFileIdParamList);
        return CommonResult.ok();
    }

    /**
     * 获取仿真模型库详情
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "获取仿真模型库详情")
    @SaCheckPermission("/fscloud/modelfile/detail")
    @GetMapping("/fscloud/modelfile/detail")
    public CommonResult<FsModelFile> detail(@Valid FsModelFileIdParam fsModelFileIdParam) {
        return CommonResult.data(fsModelFileService.detail(fsModelFileIdParam));
    }

    /**
     * 下载仿真模型库导入模板
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "下载仿真模型库导入模板")
    @GetMapping(value = "/fscloud/modelfile/downloadImportTemplate", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        fsModelFileService.downloadImportTemplate(response);
    }

    /**
     * 导入仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "导入仿真模型库")
    @CommonLog("导入仿真模型库")
    @SaCheckPermission("/fscloud/modelfile/importData")
    @PostMapping("/fscloud/modelfile/importData")
    public CommonResult<JSONObject> importData(@RequestPart("file") MultipartFile file) {
        return CommonResult.data(fsModelFileService.importData(file));
    }

    /**
     * 导出仿真模型库
     *
     * @author fengyuhe
     * @date  2026/06/04 21:42
     */
    @Operation(summary = "导出仿真模型库")
    @SaCheckPermission("/fscloud/modelfile/exportData")
    @PostMapping(value = "/fscloud/modelfile/exportData", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void exportData(@RequestBody List<FsModelFileIdParam> fsModelFileIdParamList, HttpServletResponse response) throws IOException {
        fsModelFileService.exportData(fsModelFileIdParamList, response);
    }

    /**
     * 根据 ID 列表批量查询记录（用于前端定时轮询刷新状态）
     *
     * @author fengyuhe
     * @date  2026/06/05
     */
    @Operation(summary = "根据ID列表批量查询记录")
    @SaCheckPermission("/fscloud/modelfile/listByIds")
    @PostMapping("/fscloud/modelfile/listByIds")
    public CommonResult<List<FsModelFile>> listByIds(@RequestBody @Valid @NotEmpty(message = "id集合不能为空")
                                                         List<Long> ids) {
        return CommonResult.data(fsModelFileService.listByIds(ids));
    }
}
