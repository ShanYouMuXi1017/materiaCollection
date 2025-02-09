package com.qiwenshare.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiwenshare.file.domain.PublicFile;
import com.qiwenshare.file.domain.UserFile;
import com.qiwenshare.file.vo.file.FileListVO;
import com.qiwenshare.file.vo.file.SearchPublicVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PublicFileMapper extends BaseMapper<PublicFile> {

    List<PublicFile> selectUserFileByLikeRightFilePath(@Param("filePath") String filePath, @Param("userId") String userId);

    List<FileListVO> selectPageVo(/*Page<?> page, */@Param("userFile") PublicFile userFile, @Param("fileTypeId") Integer fileTypeId);
    //List<SearchPublicVO> searchFile(@Param("fileName") String fileName);
    Long selectStorageSizeByUserId(@Param("userId") String userId);
}
