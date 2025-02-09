package com.qiwenshare.file.service;

import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qiwenshare.common.constant.FileConstant;
import com.qiwenshare.common.util.DateUtil;
import com.qiwenshare.common.util.security.JwtUser;
import com.qiwenshare.common.util.security.SessionUtil;
import com.qiwenshare.file.api.IPublicFileService;
import com.qiwenshare.file.component.PublicDealComp;
import com.qiwenshare.file.domain.PublicBean;
import com.qiwenshare.file.domain.PublicFile;
import com.qiwenshare.file.domain.RecoveryFile;
import com.qiwenshare.file.io.QiwenFile;
import com.qiwenshare.file.mapper.PublicFileMapper;
import com.qiwenshare.file.mapper.PublicMapper;
import com.qiwenshare.file.mapper.RecoveryFileMapper;
import com.qiwenshare.file.vo.file.FileListVO;
import com.qiwenshare.file.vo.file.SearchPublicVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class PublicFileService extends ServiceImpl<PublicFileMapper, PublicFile> implements IPublicFileService {
    @Autowired
    PublicFileMapper publicFileMapper;

    @Autowired
    PublicMapper publicMapper;
    @Resource
    RecoveryFileMapper recoveryFileMapper;
    @Autowired
    PublicDealComp fileDealComp;

    public static Executor executor = Executors.newFixedThreadPool(20);


    @Override
    public List<PublicFile> selectUserFileByNameAndPath(String fileName, String filePath, String userId) {
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PublicFile::getFileName, fileName)
                .eq(PublicFile::getFilePath, filePath)
                .eq(PublicFile::getUserId, userId)
                .eq(PublicFile::getDeleteFlag, 0);
        return publicFileMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    public List<PublicFile> selectSameUserFile(String fileName, String filePath, String extendName, String userId) {
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PublicFile::getFileName, fileName)
                .eq(PublicFile::getFilePath, filePath)
                .eq(PublicFile::getUserId, userId)
                .eq(PublicFile::getExtendName, extendName)
                .eq(PublicFile::getDeleteFlag, "0");
        return publicFileMapper.selectList(lambdaQueryWrapper);
    }


    @Override
    public List<FileListVO> userFileList(String userId, String filePath /*,Long currentPage, Long pageCount*/) {
        //Page<FileListVO> page = new Page<>(/*currentPage, pageCount*/);
        PublicFile userFile = new PublicFile();
        JwtUser sessionUserBean = SessionUtil.getSession();
        if (userId == null) {
            userFile.setUserId(sessionUserBean.getUserId());
        } else {
            userFile.setUserId(userId);
        }

        userFile.setFilePath(URLDecoder.decodeForPath(filePath, StandardCharsets.UTF_8));

        return publicFileMapper.selectPageVo(/*page,*/ userFile, null);
    }


    @Override
    public List<FileListVO> searchFile(String fileName) {
        // 创建 LambdaQueryWrapper
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(PublicFile::getFileName, fileName);
        // 模糊查询 PublicFile
        List<PublicFile> publicFileList = publicFileMapper.selectList(lambdaQueryWrapper);

        // 用于存储最终结果
        List<FileListVO> fileListVOList = new ArrayList<>();

        // 遍历查询结果
        for (PublicFile publicFile : publicFileList) {
            FileListVO fileListVO = new FileListVO();
            // 公共字段赋值
            fileListVO.setUserFileId(publicFile.getUserFileId());
            fileListVO.setFileName(publicFile.getFileName());
            fileListVO.setFilePath(publicFile.getFilePath());
            fileListVO.setExtendName(publicFile.getExtendName());
            fileListVO.setIsDir(publicFile.getIsDir());
            fileListVO.setUploadTime(publicFile.getUploadTime());
            fileListVO.setDeleteFlag(publicFile.getDeleteFlag());
            fileListVO.setDeleteTime(publicFile.getDeleteTime());
            fileListVO.setDeleteBatchNum(publicFile.getDeleteBatchNum());
            fileListVO.setUserId(Long.valueOf(publicFile.getUserId())); // 转换为 Long 类型

            // 判断是否是文件夹
            if (publicFile.getIsDir() == 1) {
                // 如果是文件夹，直接添加到结果列表
                fileListVOList.add(fileListVO);
            } else {
                // 如果是文件，需要查询 PublicBean 表获取额外信息
                PublicBean publicBean = publicMapper.selectById(publicFile.getFileId());
                if (publicBean != null) {
                    // 设置文件大小、文件 URL、存储类型、唯一标识符等字段
                    fileListVO.setFileId(publicBean.getFileId());
                    fileListVO.setFileUrl(publicBean.getFileUrl());
                    fileListVO.setFileSize(publicBean.getFileSize());
                    fileListVO.setStorageType(publicBean.getStorageType());
                    fileListVO.setIdentifier(publicBean.getIdentifier());
                }
                // 添加到结果列表
                fileListVOList.add(fileListVO);
            }
        }

        return fileListVOList;
    }

    @Override
    public void updateFilepathByUserFileId(String userFileId, String newfilePath, String userId) {
        PublicFile userFile = publicFileMapper.selectById(userFileId);
        String oldfilePath = userFile.getFilePath();
        String fileName = userFile.getFileName();

        userFile.setFilePath(newfilePath);
        if (userFile.getIsDir() == 0) {
            String repeatFileName = fileDealComp.getRepeatFileName(userFile, userFile.getFilePath());
            userFile.setFileName(repeatFileName);
        }
        try {
            publicFileMapper.updateById(userFile);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        //移动子目录
        oldfilePath = new QiwenFile(oldfilePath, fileName, true).getPath();
        newfilePath = new QiwenFile(newfilePath, fileName, true).getPath();

        if (userFile.isDirectory()) { //如果是目录，则需要移动子目录
            List<PublicFile> list = selectUserFileByLikeRightFilePath(oldfilePath, userId);

            for (PublicFile newUserFile : list) {
                newUserFile.setFilePath(newUserFile.getFilePath().replaceFirst(oldfilePath, newfilePath));
                if (newUserFile.getIsDir() == 0) {
                    String repeatFileName = fileDealComp.getRepeatFileName(newUserFile, newUserFile.getFilePath());
                    newUserFile.setFileName(repeatFileName);
                }
                try {
                    publicFileMapper.updateById(newUserFile);
                } catch (Exception e) {
                    log.warn(e.getMessage());
                }
            }
        }

    }

    @Override
    public void userFileCopy(String userId, String userFileId, String newfilePath) {
        PublicFile userFile = publicFileMapper.selectById(userFileId);
        String oldfilePath = userFile.getFilePath();
        String oldUserId = userFile.getUserId();
        String fileName = userFile.getFileName();

        userFile.setFilePath(newfilePath);
        userFile.setUserId(userId);
        userFile.setUserFileId(IdUtil.getSnowflakeNextIdStr());
        if (userFile.getIsDir() == 0) {
            String repeatFileName = fileDealComp.getRepeatFileName(userFile, userFile.getFilePath());
            userFile.setFileName(repeatFileName);
        }
        try {
            publicFileMapper.insert(userFile);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }

        oldfilePath = new QiwenFile(oldfilePath, fileName, true).getPath();
        newfilePath = new QiwenFile(newfilePath, fileName, true).getPath();


        if (userFile.isDirectory()) {
            List<PublicFile> subUserFileList = publicFileMapper.selectUserFileByLikeRightFilePath(oldfilePath, oldUserId);

            for (PublicFile newUserFile : subUserFileList) {
                newUserFile.setFilePath(newUserFile.getFilePath().replaceFirst(oldfilePath, newfilePath));
                newUserFile.setUserFileId(IdUtil.getSnowflakeNextIdStr());
                if (newUserFile.isDirectory()) {
                    String repeatFileName = fileDealComp.getRepeatFileName(newUserFile, newUserFile.getFilePath());
                    newUserFile.setFileName(repeatFileName);
                }
                newUserFile.setUserId(userId);
                try {
                    publicFileMapper.insert(newUserFile);
                } catch (Exception e) {
                    log.warn(e.getMessage());
                }
            }
        }

    }

    @Override
    public List<FileListVO> getFileByFileType(Integer fileTypeId, /*Long currentPage, Long pageCount,*/ String userId) {
        //Page<FileListVO> page = new Page<>(/*currentPage, pageCount*/);

        PublicFile userFile = new PublicFile();
        userFile.setUserId(userId);
        return publicFileMapper.selectPageVo(/*page,*/ userFile, fileTypeId);
    }

    @Override
    public List<PublicFile> selectUserFileListByPath(String filePath, String userId) {
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper
                .eq(PublicFile::getFilePath, filePath)
                .eq(PublicFile::getUserId, userId)
                .eq(PublicFile::getDeleteFlag, 0);
        return publicFileMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    public List<PublicFile> selectFilePathTreeByUserId(String userId) {
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PublicFile::getUserId, userId)
                .eq(PublicFile::getIsDir, 1)
                .eq(PublicFile::getDeleteFlag, 0);
        return publicFileMapper.selectList(lambdaQueryWrapper);
    }


    @Override
    public void deleteUserFile(String userFileId, String sessionUserId) {
        PublicFile userFile = publicFileMapper.selectById(userFileId);
        String uuid = UUID.randomUUID().toString();
        if (userFile.getIsDir() == 1) {
            LambdaUpdateWrapper<PublicFile> userFileLambdaUpdateWrapper = new LambdaUpdateWrapper<PublicFile>();
            userFileLambdaUpdateWrapper.set(PublicFile::getDeleteFlag, RandomUtil.randomInt(FileConstant.deleteFileRandomSize))
                    .set(PublicFile::getDeleteBatchNum, uuid)
                    .set(PublicFile::getDeleteTime, DateUtil.getCurrentTime())
                    .eq(PublicFile::getUserFileId, userFileId);
            publicFileMapper.update(null, userFileLambdaUpdateWrapper);

            String filePath = new QiwenFile(userFile.getFilePath(), userFile.getFileName(), true).getPath();
            updateFileDeleteStateByFilePath(filePath, uuid, sessionUserId);

        } else {
            LambdaUpdateWrapper<PublicFile> userFileLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            userFileLambdaUpdateWrapper.set(PublicFile::getDeleteFlag, RandomUtil.randomInt(1, FileConstant.deleteFileRandomSize))
                    .set(PublicFile::getDeleteTime, DateUtil.getCurrentTime())
                    .set(PublicFile::getDeleteBatchNum, uuid)
                    .eq(PublicFile::getUserFileId, userFileId);
            publicFileMapper.update(null, userFileLambdaUpdateWrapper);
        }

        RecoveryFile recoveryFile = new RecoveryFile();
        recoveryFile.setUserFileId(userFileId);
        recoveryFile.setDeleteTime(DateUtil.getCurrentTime());
        recoveryFile.setDeleteBatchNum(uuid);
        recoveryFileMapper.insert(recoveryFile);


    }

    @Override
    public List<PublicFile> selectUserFileByLikeRightFilePath(String filePath, String userId) {
        return publicFileMapper.selectUserFileByLikeRightFilePath(filePath, userId);
    }

    private void updateFileDeleteStateByFilePath(String filePath, String deleteBatchNum, String userId) {
        executor.execute(() -> {
            List<PublicFile> fileList = selectUserFileByLikeRightFilePath(filePath, userId);
            List<String> userFileIds = fileList.stream().map(PublicFile::getUserFileId).collect(Collectors.toList());

                //标记删除标志
            if (CollectionUtils.isNotEmpty(userFileIds)) {
                LambdaUpdateWrapper<PublicFile> userFileLambdaUpdateWrapper1 = new LambdaUpdateWrapper<>();
                userFileLambdaUpdateWrapper1.set(PublicFile::getDeleteFlag, RandomUtil.randomInt(FileConstant.deleteFileRandomSize))
                        .set(PublicFile::getDeleteTime, DateUtil.getCurrentTime())
                        .set(PublicFile::getDeleteBatchNum, deleteBatchNum)
                        .in(PublicFile::getUserFileId, userFileIds)
                        .eq(PublicFile::getDeleteFlag, 0);
                publicFileMapper.update(null, userFileLambdaUpdateWrapper1);
            }
            for (String userFileId : userFileIds) {
                fileDealComp.deleteESByUserFileId(userFileId);
            }
        });
    }


}
