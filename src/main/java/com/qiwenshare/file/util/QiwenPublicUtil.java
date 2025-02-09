package com.qiwenshare.file.util;

import cn.hutool.core.util.IdUtil;
import com.qiwenshare.common.util.DateUtil;
import com.qiwenshare.common.util.security.SessionUtil;
import com.qiwenshare.file.domain.PublicFile;
import com.qiwenshare.file.domain.UserFile;
import com.qiwenshare.file.io.QiwenFile;

public class QiwenPublicUtil {


    public static PublicFile getQiwenDir(String userId, String filePath, String fileName) {
        PublicFile publicFile = new PublicFile();
        publicFile.setUserFileId(IdUtil.getSnowflakeNextIdStr());
        publicFile.setUserId(userId);
        publicFile.setFileId(null);
        publicFile.setFileName(fileName);
        publicFile.setFilePath(QiwenFile.formatPathPub(filePath));
        publicFile.setExtendName(null);
        publicFile.setIsDir(1);
        publicFile.setUploadTime(DateUtil.getCurrentTime());
        publicFile.setCreateUserId(SessionUtil.getUserId());
        publicFile.setCreateTime(DateUtil.getCurrentTime());
        publicFile.setDeleteFlag(0);
        publicFile.setDeleteBatchNum(null);
        return publicFile;
    }

    public static PublicFile getQiwenFile(String userId, String fileId, String filePath, String fileName, String extendName) {
        PublicFile publicFile = new PublicFile();
        publicFile.setUserFileId(IdUtil.getSnowflakeNextIdStr());
        publicFile.setUserId(userId);
        publicFile.setFileId(fileId);
        publicFile.setFileName(fileName);
        publicFile.setFilePath(QiwenFile.formatPath(filePath));
        publicFile.setExtendName(extendName);
        publicFile.setIsDir(0);
        publicFile.setUploadTime(DateUtil.getCurrentTime());
        publicFile.setCreateTime(DateUtil.getCurrentTime());
        publicFile.setCreateUserId(SessionUtil.getUserId());
        publicFile.setDeleteFlag(0);
        publicFile.setDeleteBatchNum(null);
        return publicFile;
    }

    public static PublicFile searchQiwenFileParam(PublicFile publicFile) {
        PublicFile param = new PublicFile();
        param.setFilePath(QiwenFile.formatPath(publicFile.getFilePath()));
        param.setFileName(publicFile.getFileName());
        param.setExtendName(publicFile.getExtendName());
        param.setDeleteFlag(0);
        param.setUserId(publicFile.getUserId());
        param.setIsDir(0);
        return param;
    }

    public static String formatLikePath(String filePath) {
        String newFilePath = filePath.replace("'", "\\'");
        newFilePath = newFilePath.replace("%", "\\%");
        newFilePath = newFilePath.replace("_", "\\_");
        return newFilePath;
    }

}
