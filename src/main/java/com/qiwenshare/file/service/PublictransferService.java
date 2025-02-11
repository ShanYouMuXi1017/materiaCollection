package com.qiwenshare.file.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiwenshare.common.util.DateUtil;
import com.qiwenshare.common.util.MimeUtils;
import com.qiwenshare.common.util.security.SessionUtil;
import com.qiwenshare.file.api.IFiletransferService;
import com.qiwenshare.file.api.IPublictransferService;
import com.qiwenshare.file.component.FileDealComp;
import com.qiwenshare.file.component.PublicDealComp;
import com.qiwenshare.file.domain.*;
import com.qiwenshare.file.dto.file.DownloadFileDTO;
import com.qiwenshare.file.dto.file.PreviewDTO;
import com.qiwenshare.file.dto.file.UploadFileDTO;
import com.qiwenshare.file.io.QiwenFile;
import com.qiwenshare.file.mapper.*;
import com.qiwenshare.file.vo.file.UploadFileVo;
import com.qiwenshare.ufop.constant.StorageTypeEnum;
import com.qiwenshare.ufop.constant.UploadFileStatusEnum;
import com.qiwenshare.ufop.exception.operation.DownloadException;
import com.qiwenshare.ufop.exception.operation.UploadException;
import com.qiwenshare.ufop.factory.UFOPFactory;
import com.qiwenshare.ufop.operation.delete.Deleter;
import com.qiwenshare.ufop.operation.delete.domain.DeleteFile;
import com.qiwenshare.ufop.operation.download.Downloader;
import com.qiwenshare.ufop.operation.download.domain.DownloadFile;
import com.qiwenshare.ufop.operation.preview.Previewer;
import com.qiwenshare.ufop.operation.preview.domain.PreviewFile;
import com.qiwenshare.ufop.operation.upload.Uploader;
import com.qiwenshare.ufop.operation.upload.domain.UploadFile;
import com.qiwenshare.ufop.operation.upload.domain.UploadFileResult;
import com.qiwenshare.ufop.util.UFOPUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@Transactional(rollbackFor=Exception.class)
public class PublictransferService implements IPublictransferService {

    @Resource
    PublicMapper publicMapper;

    @Resource
    PublicFileMapper publicFileMapper;

    @Resource
    UFOPFactory ufopFactory;
    @Resource
    PublicDealComp publicDealComp;
    @Resource
    UploadTaskDetailMapper uploadTaskDetailMapper;
    @Resource
    UploadTaskMapper uploadTaskMapper;
    @Resource
    ImageMapper imageMapper;

    @Resource
    PictureFileMapper pictureFileMapper;
    public static Executor exec = Executors.newFixedThreadPool(20);

    @Override
    public UploadFileVo uploadFileSpeed(UploadFileDTO uploadFileDTO) {
        UploadFileVo uploadFileVo = new UploadFileVo();
        Map<String, Object> param = new HashMap<>();
        param.put("identifier", uploadFileDTO.getIdentifier());
        List<PublicBean> list = publicMapper.selectByMap(param);

        String filePath = uploadFileDTO.getFilePath();
        String relativePath = uploadFileDTO.getRelativePath();
        QiwenFile qiwenFile = null;
        if (relativePath.contains("/")) {
            qiwenFile = new QiwenFile(filePath, relativePath, false);
        } else {
            qiwenFile = new QiwenFile(filePath, uploadFileDTO.getFilename(), false);
        }

        if (list != null && !list.isEmpty()) {
            PublicBean file = list.get(0);
            PublicFile userFile = new PublicFile(qiwenFile, SessionUtil.getUserId(), file.getFileId());

            try {
                publicFileMapper.insert(userFile);
                //publicDealComp.uploadESByUserFileId(userFile.getUserFileId());
            } catch (Exception e) {
                log.warn("极速上传文件冲突重命名处理: {}", JSON.toJSONString(userFile));

            }

            if (relativePath.contains("/")) {
                QiwenFile finalQiwenFile = qiwenFile;
                exec.execute(()->{
                    publicDealComp.restoreParentFilePath(finalQiwenFile, SessionUtil.getUserId());
                });
            }

            uploadFileVo.setSkipUpload(true);
        } else {
            uploadFileVo.setSkipUpload(false);

            List<Integer> uploaded = uploadTaskDetailMapper.selectUploadedChunkNumList(uploadFileDTO.getIdentifier());
            if (uploaded != null && !uploaded.isEmpty()) {
                uploadFileVo.setUploaded(uploaded);
            } else {

                LambdaQueryWrapper<UploadTask> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                lambdaQueryWrapper.eq(UploadTask::getIdentifier, uploadFileDTO.getIdentifier());
                List<UploadTask> rslist = uploadTaskMapper.selectList(lambdaQueryWrapper);
                if (rslist == null || rslist.isEmpty()) {
                    UploadTask uploadTask = new UploadTask();
                    uploadTask.setIdentifier(uploadFileDTO.getIdentifier());
                    uploadTask.setUploadTime(DateUtil.getCurrentTime());
                    uploadTask.setUploadStatus(UploadFileStatusEnum.UNCOMPLATE.getCode());
                    uploadTask.setFileName(qiwenFile.getNameNotExtend());
                    uploadTask.setFilePath(qiwenFile.getParent());
                    uploadTask.setExtendName(qiwenFile.getExtendName());
                    uploadTask.setUserId(SessionUtil.getUserId());
                    uploadTaskMapper.insert(uploadTask);
                }
            }

        }
        return uploadFileVo;
    }

    @Override
    public void uploadFile(HttpServletRequest request, UploadFileDTO uploadFileDto, String userId) {

        // 创建一个 UploadFile 实体，用于存储上传文件的相关信息
        UploadFile uploadFile = new UploadFile();
        uploadFile.setChunkNumber(uploadFileDto.getChunkNumber());  // 设置当前文件块的编号
        uploadFile.setChunkSize(uploadFileDto.getChunkSize());  // 设置每个文件块的大小
        uploadFile.setTotalChunks(uploadFileDto.getTotalChunks());  // 设置文件块的总数
        uploadFile.setIdentifier(uploadFileDto.getIdentifier());  // 设置文件的唯一标识符
        uploadFile.setTotalSize(uploadFileDto.getTotalSize());  // 设置文件的总大小
        uploadFile.setCurrentChunkSize(uploadFileDto.getCurrentChunkSize());  // 设置当前块的大小
        // 获取文件上传处理器
        Uploader uploader = ufopFactory.getUploader();
        if (uploader == null) {
            log.error("上传失败，请检查storageType是否配置正确");  // 如果上传器未配置，输出错误日志
            throw new UploadException("上传失败");  // 抛出上传失败异常
        }

        // 存储上传结果的列表
        List<UploadFileResult> uploadFileResultList;
        try {
            // 执行上传操作，上传文件的每一块
            uploadFileResultList = uploader.upload(request, uploadFile);
        } catch (Exception e) {
            log.error("上传失败，请检查UFOP连接配置是否正确");  // 如果上传失败，输出错误日志
            throw new UploadException("上传失败", e);  // 抛出上传失败异常
        }

        // 遍历上传结果，处理每个上传文件块的结果
        for (int i = 0; i < uploadFileResultList.size(); i++){
            UploadFileResult uploadFileResult = uploadFileResultList.get(i);
            String relativePath = uploadFileDto.getRelativePath();
            QiwenFile qiwenFile = null;

            // 判断文件路径是否包含目录结构
            if (relativePath.contains("/")) {
                qiwenFile = new QiwenFile(uploadFileDto.getFilePath(), relativePath, false);
            } else {
                qiwenFile = new QiwenFile(uploadFileDto.getFilePath(), uploadFileDto.getFilename(), false);
            }

            // 如果上传成功
            if (UploadFileStatusEnum.SUCCESS.equals(uploadFileResult.getStatus())) {
                // 将上传结果保存到数据库
                PublicBean fileBean = new PublicBean(uploadFileResult);
                fileBean.setCreateUserId(userId);
                try {
                    // 尝试插入新的文件记录
                    publicMapper.insert(fileBean);
                } catch (Exception e) {
                    log.warn("identifier Duplicate: {}", fileBean.getIdentifier());  // 如果文件标识符重复，输出警告日志
                    // 如果插入失败，查询现有的文件记录
                    fileBean = publicMapper.selectOne(new QueryWrapper<PublicBean>().lambda().eq(PublicBean::getIdentifier, fileBean.getIdentifier()));
                }

                // 创建用户文件对象并插入数据库
                PublicFile userFile = new PublicFile(qiwenFile, userId, fileBean.getFileId());
                try {
                    publicFileMapper.insert(userFile);  // 插入用户文件信息
                    publicDealComp.uploadESByUserFileId(userFile.getUserFileId());  // 上传至 Elasticsearch
                } catch (Exception e) {
                    // 如果插入失败，尝试解决文件名冲突
                    PublicFile userFile1 = publicFileMapper.selectOne(new QueryWrapper<PublicFile>().lambda()
                            .eq(PublicFile::getUserId, userFile.getUserId())
                            .eq(PublicFile::getFilePath, userFile.getFilePath())
                            .eq(PublicFile::getFileName, userFile.getFileName())
                            .eq(PublicFile::getExtendName, userFile.getExtendName())
                            .eq(PublicFile::getDeleteFlag, userFile.getDeleteFlag())
                            .eq(PublicFile::getIsDir, userFile.getIsDir()));
                    PublicBean file1 = publicMapper.selectById(userFile1.getFileId());
                    if (!StringUtils.equals(fileBean.getIdentifier(), file1.getIdentifier())) {
                        log.warn("文件冲突重命名处理: {}", JSON.toJSONString(userFile1));  // 记录文件冲突日志
                        String fileName = publicDealComp.getRepeatFileName(userFile, userFile.getFilePath());  // 获取重命名后的文件名
                        userFile.setFileName(fileName);  // 更新文件名
                        publicFileMapper.insert(userFile);  // 插入重命名后的文件记录
                        publicDealComp.uploadESByUserFileId(userFile.getUserFileId());  // 上传至 Elasticsearch
                    }
                }

                // 如果文件路径包含目录，执行恢复父目录操作
                if (relativePath.contains("/")) {
                    QiwenFile finalQiwenFile = qiwenFile;
                    exec.execute(()->{
                        publicDealComp.restoreParentFilePath(finalQiwenFile, userId);  // 异步执行父目录恢复
                    });
                }

                // 删除已上传的任务详情记录
                LambdaQueryWrapper<UploadTaskDetail> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                lambdaQueryWrapper.eq(UploadTaskDetail::getIdentifier, uploadFileDto.getIdentifier());
                uploadTaskDetailMapper.delete(lambdaQueryWrapper);

                // 更新上传任务的状态为成功
                LambdaUpdateWrapper<UploadTask> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
                lambdaUpdateWrapper.set(UploadTask::getUploadStatus, UploadFileStatusEnum.SUCCESS.getCode())
                        .eq(UploadTask::getIdentifier, uploadFileDto.getIdentifier());
                uploadTaskMapper.update(null, lambdaUpdateWrapper);

                // 如果是图片文件，生成图片的缩略图
                try {
                    if (UFOPUtils.isImageFile(uploadFileResult.getExtendName())) {
                        BufferedImage src = uploadFileResult.getBufferedImage();
                        Image image = new Image();
                        image.setImageWidth(src.getWidth());  // 设置图片宽度
                        image.setImageHeight(src.getHeight());  // 设置图片高度
                        image.setFileId(fileBean.getFileId());  // 设置文件 ID
                        imageMapper.insert(image);  // 保存图片信息
                    }
                } catch (Exception e) {
                    log.error("生成图片缩略图失败！", e);  // 如果生成缩略图失败，输出错误日志
                }

                // 解析音乐文件的相关信息
                publicDealComp.parseMusicFile(uploadFileResult.getExtendName(), uploadFileResult.getStorageType().getCode(), uploadFileResult.getFileUrl(), fileBean.getFileId());
            }

            // 如果上传任务尚未完成
            else if (UploadFileStatusEnum.UNCOMPLATE.equals(uploadFileResult.getStatus())) {
                // 插入上传任务详情，表示该文件块未上传完成
                UploadTaskDetail uploadTaskDetail = new UploadTaskDetail();
                uploadTaskDetail.setFilePath(qiwenFile.getParent());
                uploadTaskDetail.setFilename(qiwenFile.getNameNotExtend());
                uploadTaskDetail.setChunkNumber(uploadFileDto.getChunkNumber());
                uploadTaskDetail.setChunkSize((int)uploadFileDto.getChunkSize());
                uploadTaskDetail.setRelativePath(uploadFileDto.getRelativePath());
                uploadTaskDetail.setTotalChunks(uploadFileDto.getTotalChunks());
                uploadTaskDetail.setTotalSize((int)uploadFileDto.getTotalSize());
                uploadTaskDetail.setIdentifier(uploadFileDto.getIdentifier());
                uploadTaskDetailMapper.insert(uploadTaskDetail);  // 插入上传任务详情记录
            }

            // 如果上传失败
            else if (UploadFileStatusEnum.FAIL.equals(uploadFileResult.getStatus())) {
                // 删除失败的上传任务详情
                LambdaQueryWrapper<UploadTaskDetail> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                lambdaQueryWrapper.eq(UploadTaskDetail::getIdentifier, uploadFileDto.getIdentifier());
                uploadTaskDetailMapper.delete(lambdaQueryWrapper);

                // 更新上传任务的状态为失败
                LambdaUpdateWrapper<UploadTask> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
                lambdaUpdateWrapper.set(UploadTask::getUploadStatus, UploadFileStatusEnum.FAIL.getCode())
                        .eq(UploadTask::getIdentifier, uploadFileDto.getIdentifier());
                uploadTaskMapper.update(null, lambdaUpdateWrapper);
            }
        }
    }



    private String formatChatset(String str) {
        if (str == null) {
            return "";
        }
        if (Charset.forName("ISO-8859-1").newEncoder().canEncode(str)) {
            byte[] bytes = str.getBytes(StandardCharsets.ISO_8859_1);
            return new String(bytes, Charset.forName("GBK"));
        }
        return str;
    }

    @Override
    public void downloadFile(HttpServletResponse httpServletResponse, DownloadFileDTO downloadFileDTO) {
        PublicFile userFile = publicFileMapper.selectById(downloadFileDTO.getUserFileId());

        if (userFile.isFile()) {

            PublicBean fileBean = publicMapper.selectById(userFile.getFileId());
            Downloader downloader = ufopFactory.getDownloader(fileBean.getStorageType());
            if (downloader == null) {
                log.error("下载失败，文件存储类型不支持下载，storageType:{}", fileBean.getStorageType());
                throw new DownloadException("下载失败");
            }
            DownloadFile downloadFile = new DownloadFile();

            downloadFile.setFileUrl(fileBean.getFileUrl());
            httpServletResponse.setContentLengthLong(fileBean.getFileSize());
            downloader.download(httpServletResponse, downloadFile);
        } else {

            QiwenFile qiwenFile = new QiwenFile(userFile.getFilePath(), userFile.getFileName(), true);
            List<PublicFile> userFileList = publicFileMapper.selectUserFileByLikeRightFilePath(qiwenFile.getPath() , userFile.getUserId());
            List<String> userFileIds = userFileList.stream().map(PublicFile::getUserFileId).collect(Collectors.toList());

            downloadUserFileList(httpServletResponse, userFile.getFilePath(), userFile.getFileName(), userFileIds);
        }
    }

    @Override
    public void downloadUserFileList(HttpServletResponse httpServletResponse, String filePath, String fileName, List<String> userFileIds) {
        String staticPath = UFOPUtils.getStaticPath();
        String tempPath = staticPath + "temp" + File.separator;
        File tempDirFile = new File(tempPath);
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        FileOutputStream f = null;
        try {
            f = new FileOutputStream(tempPath + fileName + ".zip");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        CheckedOutputStream csum = new CheckedOutputStream(f, new Adler32());
        ZipOutputStream zos = new ZipOutputStream(csum);
        BufferedOutputStream out = new BufferedOutputStream(zos);

        try {
            for (String userFileId : userFileIds) {
                PublicFile userFile1 = publicFileMapper.selectById(userFileId);
                if (userFile1.isFile()) {
                    PublicBean fileBean = publicMapper.selectById(userFile1.getFileId());
                    Downloader downloader = ufopFactory.getDownloader(fileBean.getStorageType());
                    if (downloader == null) {
                        log.error("下载失败，文件存储类型不支持下载，storageType:{}", fileBean.getStorageType());
                        throw new UploadException("下载失败");
                    }
                    DownloadFile downloadFile = new DownloadFile();
                    downloadFile.setFileUrl(fileBean.getFileUrl());
                    InputStream inputStream = downloader.getInputStream(downloadFile);
                    BufferedInputStream bis = new BufferedInputStream(inputStream);
                    try {
                        QiwenFile qiwenFile = new QiwenFile(StrUtil.removePrefix(userFile1.getFilePath(), filePath), userFile1.getFileName() + "." + userFile1.getExtendName(), false);
                        zos.putNextEntry(new ZipEntry(qiwenFile.getPath()));

                        byte[] buffer = new byte[1024];
                        int i = bis.read(buffer);
                        while (i != -1) {
                            out.write(buffer, 0, i);
                            i = bis.read(buffer);
                        }
                    } catch (IOException e) {
                        log.error("" + e);
                        e.printStackTrace();
                    } finally {
                        IOUtils.closeQuietly(bis);
                        try {
                            out.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    QiwenFile qiwenFile = new QiwenFile(StrUtil.removePrefix(userFile1.getFilePath(), filePath), userFile1.getFileName(), true);
                    // 空文件夹的处理
                    zos.putNextEntry(new ZipEntry(qiwenFile.getPath() + QiwenFile.separator));
                    // 没有文件，不需要文件的copy
                    zos.closeEntry();
                }
            }

        } catch (Exception e) {
            log.error("压缩过程中出现异常:"+ e);
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String zipPath = "";
        try {
            Downloader downloader = ufopFactory.getDownloader(StorageTypeEnum.LOCAL.getCode());
            DownloadFile downloadFile = new DownloadFile();
            downloadFile.setFileUrl("temp" + File.separator + fileName + ".zip");
            File tempFile = new File(UFOPUtils.getStaticPath() + downloadFile.getFileUrl());
            httpServletResponse.setContentLengthLong(tempFile.length());
            downloader.download(httpServletResponse, downloadFile);
            zipPath = UFOPUtils.getStaticPath() + "temp" + File.separator + fileName + ".zip";
        } catch (Exception e) {
            //org.apache.catalina.connector.ClientAbortException: java.io.IOException: Connection reset by peer
            if (e.getMessage().contains("ClientAbortException")) {
                //该异常忽略不做处理
            } else {
                log.error("下传zip文件出现异常：{}", e.getMessage());
            }

        } finally {
            File file = new File(zipPath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    public void previewFile(HttpServletResponse httpServletResponse, PreviewDTO previewDTO) {
        PublicFile userFile = publicFileMapper.selectById(previewDTO.getUserFileId());
        PublicBean fileBean = publicMapper.selectById(userFile.getFileId());
        Previewer previewer = ufopFactory.getPreviewer(fileBean.getStorageType());
        if (previewer == null) {
            log.error("预览失败，文件存储类型不支持预览，storageType:{}", fileBean.getStorageType());
            throw new UploadException("预览失败");
        }
        PreviewFile previewFile = new PreviewFile();
        previewFile.setFileUrl(fileBean.getFileUrl());
        try {
            if ("true".equals(previewDTO.getIsMin())) {
                previewer.imageThumbnailPreview(httpServletResponse, previewFile);
            } else {
                previewer.imageOriginalPreview(httpServletResponse, previewFile);
            }
        } catch (Exception e){
                //org.apache.catalina.connector.ClientAbortException: java.io.IOException: 你的主机中的软件中止了一个已建立的连接。
                if (e.getMessage().contains("ClientAbortException")) {
                //该异常忽略不做处理
            } else {
                log.error("预览文件出现异常：{}", e.getMessage());
            }

        }

    }

    @Override
    public void previewPictureFile(HttpServletResponse httpServletResponse, PreviewDTO previewDTO) {
        byte[] bytesUrl = Base64.getDecoder().decode(previewDTO.getUrl());
        PictureFile pictureFile = new PictureFile();
        pictureFile.setFileUrl(new String(bytesUrl));
        pictureFile = pictureFileMapper.selectOne(new QueryWrapper<>(pictureFile));
        Previewer previewer = ufopFactory.getPreviewer(pictureFile.getStorageType());
        if (previewer == null) {
            log.error("预览失败，文件存储类型不支持预览，storageType:{}", pictureFile.getStorageType());
            throw new UploadException("预览失败");
        }
        PreviewFile previewFile = new PreviewFile();
        previewFile.setFileUrl(pictureFile.getFileUrl());
//        previewFile.setFileSize(pictureFile.getFileSize());
        try {

            String mime= MimeUtils.getMime(pictureFile.getExtendName());
            httpServletResponse.setHeader("Content-Type", mime);

            String fileName = pictureFile.getFileName() + "." + pictureFile.getExtendName();
            try {
                fileName = new String(fileName.getBytes("utf-8"), "ISO-8859-1");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            httpServletResponse.addHeader("Content-Disposition", "fileName=" + fileName);// 设置文件名

            previewer.imageOriginalPreview(httpServletResponse, previewFile);
        } catch (Exception e){
            //org.apache.catalina.connector.ClientAbortException: java.io.IOException: 你的主机中的软件中止了一个已建立的连接。
            if (e.getMessage().contains("ClientAbortException")) {
                //该异常忽略不做处理
            } else {
                log.error("预览文件出现异常：{}", e.getMessage());
            }

        }
    }

    @Override
    public void deleteFile(PublicBean fileBean) {
        Deleter deleter = null;

        deleter = ufopFactory.getDeleter(fileBean.getStorageType());
        DeleteFile deleteFile = new DeleteFile();
        deleteFile.setFileUrl(fileBean.getFileUrl());
        deleter.delete(deleteFile);
    }



    @Override
    public Long selectStorageSizeByUserId(String userId){
        return publicFileMapper.selectStorageSizeByUserId(userId);
    }
}
