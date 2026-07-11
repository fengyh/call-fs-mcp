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
package vip.xiaonuo.fscloud.modular.modelfile.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import cn.dev33.satoken.stp.StpUtil;
import vip.xiaonuo.auth.core.util.StpLoginUserUtil;
import vip.xiaonuo.common.util.CommonServletUtil;
import vip.xiaonuo.common.util.CommonSqlUtil;
import vip.xiaonuo.common.enums.CommonSortOrderEnum;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.common.page.CommonPageRequest;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;

import vip.xiaonuo.fscloud.core.base.ZipFactory;
import vip.xiaonuo.fscloud.modular.entity.FsModel;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.modelfile.mapper.FsModelFileMapper;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileAddParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileEditParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFileIdParam;
import vip.xiaonuo.fscloud.modular.modelfile.param.FsModelFilePageParam;
import vip.xiaonuo.fscloud.modular.modelfile.service.FsModelFileService;

import vip.xiaonuo.common.util.CommonDownloadUtil;
import vip.xiaonuo.common.util.CommonResponseUtil;

import java.io.File;
import java.io.Serializable;
import java.io.IOException;
import java.util.List;

/**
 * 仿真模型库Service接口实现类
 *
 * @author fengyuhe
 * @date  2026/06/04 21:42
 **/
@Service
@Slf4j
public class FsModelFileServiceImpl extends ServiceImpl<FsModelFileMapper, FsModelFile> implements FsModelFileService {

    @jakarta.annotation.Resource
    private vip.xiaonuo.fscloud.modular.simulation.service.SimulationModelTrackingMap simulationModelTrackingMap;

    /** fscloud 上传文件根路径，从 application 配置读取（不同 profile 不同值）,
     * 如果没有定义 fscloud.upload.static-path, 则默认JVM工作目录下的 static/upload 目录
     */
    @org.springframework.beans.factory.annotation.Value("${fscloud.upload.static-path:${user.dir}${file.separator}static}")
    private String fscloudUploadStaticPath;

    @Override
    public Page<FsModelFile> page(FsModelFilePageParam fsModelFilePageParam) {
        QueryWrapper<FsModelFile> queryWrapper = new QueryWrapper<FsModelFile>().checkSqlInjection();
        if(ObjectUtil.isAllNotEmpty(fsModelFilePageParam.getSortField(), fsModelFilePageParam.getSortOrder())) {
            CommonSortOrderEnum.validate(fsModelFilePageParam.getSortOrder());
            queryWrapper.orderBy(true, fsModelFilePageParam.getSortOrder().equals(CommonSortOrderEnum.ASC.getValue()),
                    StrUtil.toUnderlineCase(fsModelFilePageParam.getSortField()));
        } else {
            queryWrapper.lambda().orderByAsc(FsModelFile::getId);
        }
        // 校验数据范围
        List<String> loginUserDataScope = StpLoginUserUtil.getLoginUserDataScope();
        if(loginUserDataScope != null && loginUserDataScope.isEmpty()) {
            return new Page<>();
        }
        if(ObjectUtil.isNotEmpty(loginUserDataScope)) {
            // 根据业务需要，取消下面注释并修改为实际的数据范围字段，例如：orgId
            // CommonSqlUtil.scopeIn(queryWrapper.lambda(), FsModelFile::getOrgId, StpUtil.getLoginIdAsString(), CommonServletUtil.getRequest().getServletPath());
        }
        // 过滤条件
        if(ObjectUtil.isNotEmpty(fsModelFilePageParam.getModelUsage())) {
            queryWrapper.lambda().like(FsModelFile::getModelUsage, fsModelFilePageParam.getModelUsage());
        }
        if(ObjectUtil.isNotEmpty(fsModelFilePageParam.getModelCompany())) {
            queryWrapper.lambda().like(FsModelFile::getModelCompany, fsModelFilePageParam.getModelCompany());
        }
        if(ObjectUtil.isNotEmpty(fsModelFilePageParam.getModelFactory())) {
            queryWrapper.lambda().like(FsModelFile::getModelFactory, fsModelFilePageParam.getModelFactory());
        }
        if(ObjectUtil.isNotEmpty(fsModelFilePageParam.getModelWorkshop())) {
            queryWrapper.lambda().like(FsModelFile::getModelWarehouse, fsModelFilePageParam.getModelWorkshop());
        }
        if(ObjectUtil.isNotEmpty(fsModelFilePageParam.getModelVersion())) {
            queryWrapper.lambda().like(FsModelFile::getModelVersion, fsModelFilePageParam.getModelVersion());
        }
        return this.page(CommonPageRequest.defaultPage(), queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void add(FsModelFileAddParam fsModelFileAddParam) {
        FsModelFile fsModelFile = BeanUtil.toBean(fsModelFileAddParam, FsModelFile.class);
        // 校验数据范围
        List<String> loginUserDataScope = StpLoginUserUtil.getLoginUserDataScope();
        if(loginUserDataScope != null && loginUserDataScope.isEmpty()) {
            throw new CommonException("您没有权限增加仿真模型库");
        }
        if(ObjectUtil.isNotEmpty(loginUserDataScope)) {
            // 根据业务需要，取消下面注释并修改为实际的数据范围字段，例如：orgId
            // if(!loginUserDataScope.contains(fsModelFile.getOrgId())) {
            //     throw new CommonException("您没有权限在该机构下增加仿真模型库");
            // }
        }

        this.save(fsModelFile);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void edit(FsModelFileEditParam fsModelFileEditParam) {
        FsModelFile fsModelFile = this.queryEntity(fsModelFileEditParam.getId());
        BeanUtil.copyProperties(fsModelFileEditParam, fsModelFile);
        // 校验数据范围
        List<String> loginUserDataScope = StpLoginUserUtil.getLoginUserDataScope();
        if(loginUserDataScope != null && loginUserDataScope.isEmpty()) {
            throw new CommonException("您没有权限编辑仿真模型库");
        }
        if(ObjectUtil.isNotEmpty(loginUserDataScope)) {
            // 根据业务需要，取消下面注释并修改为实际的数据范围字段，例如：orgId
            // if(!loginUserDataScope.contains(fsModelFile.getOrgId())) {
            //     throw new CommonException("您没有权限编辑该机构下的仿真模型库");
            // }
        }

        this.updateById(fsModelFile);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(List<FsModelFileIdParam> fsModelFileIdParamList) {
        // 校验数据范围
        List<String> loginUserDataScope = StpLoginUserUtil.getLoginUserDataScope();
        if(loginUserDataScope != null && loginUserDataScope.isEmpty()) {
            throw new CommonException("您没有权限删除仿真模型库");
        }
        if(ObjectUtil.isNotEmpty(loginUserDataScope)) {
            // 根据业务需要，取消下面注释并修改为实际的数据范围字段，例如对删除的记录进行数据范围校验
            // Set<String> orgIdSet = this.listByIds(CollStreamUtil.toList(fsModelFileIdParamList, FsModelFileIdParam::getId))
            //         .stream().map(FsModelFile::getOrgId).collect(Collectors.toSet());
            // if(!new HashSet<>(loginUserDataScope).containsAll(orgIdSet)) {
            //     throw new CommonException("您没有权限删除这些机构下的仿真模型库");
            // }
        }
        // 执行删除
        this.removeByIds(CollStreamUtil.toList(fsModelFileIdParamList, FsModelFileIdParam::getId));
    }

    @Override
    public FsModelFile detail(FsModelFileIdParam fsModelFileIdParam) {
        return this.queryEntity(fsModelFileIdParam.getId());
    }

    @Override
    public FsModelFile queryEntity(Serializable id) {
        FsModelFile fsModelFile = this.getById(id);
        if(ObjectUtil.isEmpty(fsModelFile)) {
            throw new CommonException("仿真模型库不存在，id值为：{}", id);
        }
        return fsModelFile;
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
       File tempFile = null;
       try {
         List<FsModelFileEditParam> dataList = CollectionUtil.newArrayList();
         String fileName = "仿真模型库导入模板_" + DateUtil.format(DateTime.now(), DatePattern.PURE_DATETIME_PATTERN) + ".xlsx";
         tempFile = FileUtil.file(FileUtil.getTmpDir() + FileUtil.FILE_SEPARATOR + fileName);
         EasyExcel.write(tempFile.getPath(), FsModelFileEditParam.class).sheet("仿真模型库").doWrite(dataList);
         CommonDownloadUtil.download(tempFile, response);
       } catch (Exception e) {
         log.error(">>> 仿真模型库导入模板下载失败：", e);
         CommonResponseUtil.renderError(response, "仿真模型库导入模板下载失败");
       } finally {
         FileUtil.del(tempFile);
       }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public JSONObject importData(MultipartFile file) {
        try {
            int successCount = 0;
            int errorCount = 0;
            JSONArray errorDetail = JSONUtil.createArray();
            // 创建临时文件
            File tempFile = FileUtil.writeBytes(file.getBytes(), FileUtil.file(FileUtil.getTmpDir() +
                    FileUtil.FILE_SEPARATOR + "fsModelFileImportTemplate.xlsx"));
            // 读取excel
            List<FsModelFileEditParam> fsModelFileEditParamList =  EasyExcel.read(tempFile).head(FsModelFileEditParam.class).sheet()
                    .headRowNumber(1).doReadSync();
            List<FsModelFile> allDataList = this.list();
            for (int i = 0; i < fsModelFileEditParamList.size(); i++) {
                JSONObject jsonObject = this.doImport(allDataList, fsModelFileEditParamList.get(i), i);
                if(jsonObject.getBool("success")) {
                    successCount += 1;
                } else {
                    errorCount += 1;
                    errorDetail.add(jsonObject);
                }
            }
            return JSONUtil.createObj()
                    .set("totalCount", fsModelFileEditParamList.size())
                    .set("successCount", successCount)
                    .set("errorCount", errorCount)
                    .set("errorDetail", errorDetail);
        } catch (Exception e) {
            log.error(">>> 仿真模型库导入失败：", e);
            throw new CommonException("仿真模型库导入失败");
        }
    }

    public JSONObject doImport(List<FsModelFile> allDataList, FsModelFileEditParam fsModelFileEditParam, int i) {
        Long id = fsModelFileEditParam.getId();
        if(ObjectUtil.hasEmpty(id)) {
            return JSONUtil.createObj().set("index", i + 1).set("success", false).set("msg", "必填字段存在空值");
        } else {
            try {
                int index = CollStreamUtil.toList(allDataList, FsModelFile::getId).indexOf(fsModelFileEditParam.getId());
                FsModelFile fsModelFile;
                boolean isAdd = false;
                if(index == -1) {
                    isAdd = true;
                    fsModelFile = new FsModelFile();
                } else {
                    fsModelFile = allDataList.get(index);
                }
                BeanUtil.copyProperties(fsModelFileEditParam, fsModelFile);
                if(isAdd) {
                    allDataList.add(fsModelFile);
                } else {
                    allDataList.remove(index);
                    allDataList.add(index, fsModelFile);
                }
                this.saveOrUpdate(fsModelFile);
                return JSONUtil.createObj().set("success", true);
            } catch (Exception e) {
              log.error(">>> 数据导入异常：", e);
              return JSONUtil.createObj().set("success", false).set("index", i + 1).set("msg", "数据导入异常");
            }
        }
    }

    @Override
    public void exportData(List<FsModelFileIdParam> fsModelFileIdParamList, HttpServletResponse response) throws IOException {
       File tempFile = null;
       try {
         List<FsModelFileEditParam> dataList;
         if(ObjectUtil.isNotEmpty(fsModelFileIdParamList)) {
            List<Serializable> idList = CollStreamUtil.toList(fsModelFileIdParamList, FsModelFileIdParam::getId);
            dataList = BeanUtil.copyToList(this.listByIds(idList), FsModelFileEditParam.class);
         } else {
            dataList = BeanUtil.copyToList(this.list(), FsModelFileEditParam.class);
         }
         String fileName = "仿真模型库_" + DateUtil.format(DateTime.now(), DatePattern.PURE_DATETIME_PATTERN) + ".xlsx";
         tempFile = FileUtil.file(FileUtil.getTmpDir() + FileUtil.FILE_SEPARATOR + fileName);
         EasyExcel.write(tempFile.getPath(), FsModelFileEditParam.class).sheet("仿真模型库").doWrite(dataList);
         CommonDownloadUtil.download(tempFile, response);
       } catch (Exception e) {
         log.error(">>> 仿真模型库导出失败：", e);
         CommonResponseUtil.renderError(response, "仿真模型库导出失败");
       } finally {
         FileUtil.del(tempFile);
       }
    }

    @Override
    public List<FsModelFile> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<FsModelFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(FsModelFile::getId, ids);
        List<FsModelFile> records = this.list(queryWrapper);
        // 深拷贝一份原始数据进行对比（mergeDbWithTracking 原地修改对象）
        List<FsModelFile> recordsBackup = records.stream()
                .map(r -> {
                    FsModelFile backup = new FsModelFile();
                    BeanUtil.copyProperties(r, backup);
                    return backup;
                })
                .collect(java.util.stream.Collectors.toList());

        // 用 tracking map 中的实时状态覆盖 DB 状态
        List<FsModelFile> result = records.stream()
                .map(r -> simulationModelTrackingMap.mergeDbWithTracking(r))
                .collect(java.util.stream.Collectors.toList());

        // 对比：simStatus 或 modelId 发生变化时，反写回 DB
        for (int i = 0; i < result.size(); i++) {
            FsModelFile original = recordsBackup.get(i);
            FsModelFile modified = result.get(i);
            if (!ObjectUtil.equals(original.getSimStatus(), modified.getSimStatus())
                    || !ObjectUtil.equals(original.getModelId(), modified.getModelId())) {
                this.updateById(modified);
            }
        }

        return result;

    }

    @Override
    public void processUploadFile(MultipartFile file, FsModel fsModelInfo) {
        log.info("Processing uploaded file: {}, fsModelInfo: {}", file.getOriginalFilename(), fsModelInfo);

        // 文件落盘
        try{
            // 从配置读取上传根路径，上传到 {static-path}/uploaded/<userId>/
            Path webRoot = Paths.get(fscloudUploadStaticPath);
            Path uploadBasePath = webRoot.resolve("uploaded").resolve(StpUtil.getLoginIdAsString());
            if (!Files.exists(uploadBasePath)) {
                Files.createDirectories(uploadBasePath);
            }
            String fileName = file.getOriginalFilename();
            if(fileName!=null) {
                Path filePath = uploadBasePath.resolve(fsModelInfo.getUserId() + "_" + fileName);
                try (FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile())) {
                    fileOutputStream.write(file.getBytes());
                }
                // 处理 zip 文件
                if(fileName.endsWith(".zfs")) {
                    ZipFactory.unzip(filePath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 元数据落库: 将文件信息保存到 fs_model_file 表中，关联到 fsModelInfo.getId()
        FsModelFile fsModelFile = new FsModelFile();
        //fsModelFile.setName(file.getOriginalFilename());
        fsModelFile.setName(fsModelInfo.getName());
        fsModelFile.setDescription(fsModelInfo.getDescription());
        fsModelFile.setEngineVersion(fsModelInfo.getEngineVersion());
        fsModelFile.setModelUsage(fsModelInfo.getModelUsage());
        fsModelFile.setUserId(fsModelInfo.getUserId());
        fsModelFile.setUserName(fsModelInfo.getUserName());
        fsModelFile.setImage(fsModelInfo.getImage());
        fsModelFile.setModel(fsModelInfo.getModel());
        fsModelFile.setModelVersion(fsModelInfo.getVersion());
        fsModelFile.setShareFlag(fsModelInfo.getModelPermission());
        fsModelFile.setSimStatus("SIM_STATUS_NOTYET");
        fsModelFile.setSimTimes(0);
        this.save(fsModelFile);


    }
}
