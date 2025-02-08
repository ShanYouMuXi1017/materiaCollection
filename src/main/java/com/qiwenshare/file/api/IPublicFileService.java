package com.qiwenshare.file.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qiwenshare.file.domain.PublicFile;
import com.qiwenshare.file.domain.UserFile;
import com.qiwenshare.file.vo.file.FileListVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IPublicFileService extends IService<PublicFile> {
    List<PublicFile> selectUserFileByNameAndPath(String fileName, String filePath, String userId);
    List<PublicFile> selectSameUserFile(String fileName, String filePath, String extendName, String userId);

    IPage<FileListVO> userFileList(String userId, String filePath, Long beginCount, Long pageCount);
    void updateFilepathByUserFileId(String userFileId, String newfilePath, String userId);
    void userFileCopy(String userId, String userFileId, String newfilePath);

    IPage<FileListVO> getFileByFileType(Integer fileTypeId, Long currentPage, Long pageCount, String userId);
    List<PublicFile> selectUserFileListByPath(String filePath, String userId);
    List<PublicFile> selectFilePathTreeByUserId(String userId);
    void deleteUserFile(String userFileId, String sessionUserId);

    List<PublicFile> selectUserFileByLikeRightFilePath(@Param("filePath") String filePath, @Param("userId") String userId);

}
