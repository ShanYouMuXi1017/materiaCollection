package com.qiwenshare.file.controller;

import com.qiwenshare.common.anno.MyLog;
import com.qiwenshare.common.result.RestResult;
import com.qiwenshare.common.util.MimeUtils;
import com.qiwenshare.common.util.security.JwtUser;
import com.qiwenshare.common.util.security.SessionUtil;
import com.qiwenshare.file.api.*;
import com.qiwenshare.file.component.FileDealComp;
import com.qiwenshare.file.component.PublicDealComp;
import com.qiwenshare.file.domain.PublicBean;
import com.qiwenshare.file.domain.StorageBean;
import com.qiwenshare.file.domain.PublicFile;
import com.qiwenshare.file.dto.file.*;
import com.qiwenshare.file.io.QiwenFile;
import com.qiwenshare.file.service.PublicStorageService;
import com.qiwenshare.file.service.StorageService;
import com.qiwenshare.file.vo.file.UploadFileVo;
import com.qiwenshare.ufop.factory.UFOPFactory;
import com.qiwenshare.ufop.operation.download.Downloader;
import com.qiwenshare.ufop.operation.download.domain.DownloadFile;
import com.qiwenshare.ufop.operation.download.domain.Range;
import com.qiwenshare.ufop.util.UFOPUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "publictransfer", description = "该接口为公共区域文件传输接口，主要用来做文件的上传、下载和预览")
@RestController
@RequestMapping("/publictransfer")
public class PublictransferController {

    // 公共文件操作的路径
    @Value("${ufop.local-storage-path}")
    private String localStoragePath;
    @Value("${ufop.bucket-name-pub}")
    private String bucketNamePub;

    @Resource
    IPublictransferService publictransferService;

    @Autowired
    IPublicService publicService;
    @Autowired
    IPublicFileService publicFileService;
    @Autowired
    PublicDealComp publicDealComp;
    @Resource
    StorageService storageService;

    @Resource
    PublicStorageService publicStorageService;
    @Resource
    UFOPFactory ufopFactory;


    public static final String CURRENT_MODULE = "文件传输接口";

    @Operation(summary = "极速上传", description = "校验文件MD5判断文件是否存在，如果存在直接上传成功并返回skipUpload=true，如果不存在返回skipUpload=false需要再次调用该接口的POST方法", tags = {"filetransfer"})
    @RequestMapping(value = "/uploadfile", method = RequestMethod.GET)
    @MyLog(operation = "极速上传", module = CURRENT_MODULE)
    @ResponseBody
    public RestResult<UploadFileVo> uploadFileSpeed(UploadFileDTO uploadFileDto) {

        JwtUser sessionUserBean = SessionUtil.getSession();

        boolean isCheckSuccess = storageService.checkStorage(SessionUtil.getUserId(), uploadFileDto.getTotalSize());
        if (!isCheckSuccess) {
            return RestResult.fail().message("存储空间不足");
        }
        UploadFileVo uploadFileVo = publictransferService.uploadFileSpeed(uploadFileDto);
        return RestResult.success().data(uploadFileVo);

    }

    @Operation(summary = "上传文件", description = "真正的上传文件接口", tags = {"filetransfer"})
    @RequestMapping(value = "/uploadfile", method = RequestMethod.POST)
    @MyLog(operation = "上传文件", module = CURRENT_MODULE)
    @ResponseBody
    public RestResult<UploadFileVo> uploadFile(HttpServletRequest request, UploadFileDTO uploadFileDto) {

        JwtUser sessionUserBean = SessionUtil.getSession();

        publictransferService.uploadFile(request, uploadFileDto, sessionUserBean.getUserId());

        UploadFileVo uploadFileVo = new UploadFileVo();
        return RestResult.success().data(uploadFileVo);

    }


    @Operation(summary = "下载文件", description = "下载文件接口", tags = {"filetransfer"})
    @MyLog(operation = "下载文件", module = CURRENT_MODULE)
    @RequestMapping(value = "/downloadfile", method = RequestMethod.GET)
    public void downloadFile(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, DownloadFileDTO downloadFileDTO) {
        Cookie[] cookieArr = httpServletRequest.getCookies();
        String token = "";
        if (cookieArr != null) {
            for (Cookie cookie : cookieArr) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }
        boolean authResult = publicDealComp.checkAuthDownloadAndPreview(downloadFileDTO.getShareBatchNum(),
                downloadFileDTO.getExtractionCode(),
                token,
                downloadFileDTO.getUserFileId(), null);
        if (!authResult) {
            log.error("没有权限下载！！！");
            return;
        }
        httpServletResponse.setContentType("application/force-download");// 设置强制下载不打开
        PublicFile userFile = publicFileService.getById(downloadFileDTO.getUserFileId());
        String fileName = "";
        if (userFile.getIsDir() == 1) {
            fileName = userFile.getFileName() + ".zip";
        } else {
            fileName = userFile.getFileName() + "." + userFile.getExtendName();

        }
        try {
            fileName = new String(fileName.getBytes("utf-8"), "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        httpServletResponse.addHeader("Content-Disposition", "attachment;fileName=" + fileName);// 设置文件名

        publictransferService.downloadFile(httpServletResponse, downloadFileDTO);
    }

    @Operation(summary = "批量下载文件", description = "批量下载文件", tags = {"filetransfer"})
    @RequestMapping(value = "/batchDownloadFile", method = RequestMethod.GET)
    @MyLog(operation = "批量下载文件", module = CURRENT_MODULE)
    @ResponseBody
    public void batchDownloadFile(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, BatchDownloadFileDTO batchDownloadFileDTO) {
        Cookie[] cookieArr = httpServletRequest.getCookies();
        String token = "";
        if (cookieArr != null) {
            for (Cookie cookie : cookieArr) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }
        boolean authResult = publicDealComp.checkAuthDownloadAndPreview(batchDownloadFileDTO.getShareBatchNum(),
                batchDownloadFileDTO.getExtractionCode(),
                token,
                batchDownloadFileDTO.getUserFileIds(), null);
        if (!authResult) {
            log.error("没有权限下载！！！");
            return;
        }

        String files = batchDownloadFileDTO.getUserFileIds();
        String[] userFileIdStrs = files.split(",");
        List<String> userFileIds = new ArrayList<>();
        for(String userFileId : userFileIdStrs) {
            PublicFile userFile = publicFileService.getById(userFileId);
            if (userFile.getIsDir() == 0) {
                userFileIds.add(userFileId);
            } else {
                QiwenFile qiwenFile = new QiwenFile(userFile.getFilePath(), userFile.getFileName(), true);
                List<PublicFile> userFileList = publicFileService.selectUserFileByLikeRightFilePath(qiwenFile.getPath(), userFile.getUserId());
                List<String> userFileIds1 = userFileList.stream().map(PublicFile::getUserFileId).collect(Collectors.toList());
                userFileIds.add(userFile.getUserFileId());
                userFileIds.addAll(userFileIds1);
            }

        }
        PublicFile userFile = publicFileService.getById(userFileIdStrs[0]);
        httpServletResponse.setContentType("application/force-download");// 设置强制下载不打开
        Date date = new Date();
        String fileName = String.valueOf(date.getTime());
        httpServletResponse.addHeader("Content-Disposition", "attachment;fileName=" + fileName + ".zip");// 设置文件名
        publictransferService.downloadUserFileList(httpServletResponse, userFile.getFilePath(), fileName, userFileIds);
    }

    @Operation(summary="预览文件", description="用于文件预览", tags = {"filetransfer"})
    @GetMapping("/preview")
    public void preview(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,  PreviewDTO previewDTO) throws IOException {

        if (previewDTO.getPlatform() != null && previewDTO.getPlatform() == 2) {
            publictransferService.previewPictureFile(httpServletResponse, previewDTO);
            return ;
        }
        String token = "";
        if (StringUtils.isNotEmpty(previewDTO.getToken())) {
            token = previewDTO.getToken();
        } else {
            Cookie[] cookieArr = httpServletRequest.getCookies();
            if (cookieArr != null) {
                for (Cookie cookie : cookieArr) {
                    if ("token".equals(cookie.getName())) {
                        token = cookie.getValue();
                    }
                }
            }
        }

        PublicFile userFile = publicFileService.getById(previewDTO.getUserFileId());
        //boolean authResult = publicDealComp.checkAuthDownloadAndPreview(previewDTO.getShareBatchNum(),
        //        previewDTO.getExtractionCode(),
        //        token,
        //        previewDTO.getUserFileId(),
        //        previewDTO.getPlatform());
        //
        //if (!authResult) {
        //    log.error("没有权限预览！！！");
        //    return;
        //}

        String fileName = userFile.getFileName() + "." + userFile.getExtendName();
        try {
            fileName = new String(fileName.getBytes("utf-8"), "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        httpServletResponse.addHeader("Content-Disposition", "fileName=" + fileName);// 设置文件名
        String mime = MimeUtils.getMime(userFile.getExtendName());
        httpServletResponse.setHeader("Content-Type", mime);
        if (UFOPUtils.isImageFile(userFile.getExtendName())) {
            httpServletResponse.setHeader("cache-control", "public");
        }

        PublicBean fileBean = publicService.getById(userFile.getFileId());
        if (UFOPUtils.isVideoFile(userFile.getExtendName()) || "mp3".equalsIgnoreCase(userFile.getExtendName()) || "flac".equalsIgnoreCase(userFile.getExtendName())) {
            //获取从那个字节开始读取文件
            String rangeString = httpServletRequest.getHeader("Range");
            int start = 0;
            if (StringUtils.isNotBlank(rangeString)) {
                start = Integer.parseInt(rangeString.substring(rangeString.indexOf("=") + 1, rangeString.indexOf("-")));
            }

            Downloader downloader = ufopFactory.getDownloader(fileBean.getStorageType());
            DownloadFile downloadFile = new DownloadFile();
            downloadFile.setFileUrl(fileBean.getFileUrl());
            Range range = new Range();
            range.setStart(start);

            if (start + 1024 * 1024 * 1 >= fileBean.getFileSize().intValue()) {
                range.setLength(fileBean.getFileSize().intValue() - start);
            } else {
                range.setLength(1024 * 1024 * 1);
            }
            downloadFile.setRange(range);
            InputStream inputStream = downloader.getInputStream(downloadFile);

            OutputStream outputStream = httpServletResponse.getOutputStream();
            try {

                //返回码需要为206，代表只处理了部分请求，响应了部分数据

                httpServletResponse.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                // 每次请求只返回1MB的视频流

                httpServletResponse.setHeader("Accept-Ranges", "bytes");
                //设置此次相应返回的数据范围
                httpServletResponse.setHeader("Content-Range", "bytes " + start + "-" + (fileBean.getFileSize() - 1) + "/" + fileBean.getFileSize());
                IOUtils.copy(inputStream, outputStream);


            } finally {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(outputStream);
                if (downloadFile.getOssClient() != null) {
                    downloadFile.getOssClient().shutdown();
                }
            }

        } else {
            publictransferService.previewFile(httpServletResponse, previewDTO);
        }

    }

    @Operation(summary = "获取存储信息", description = "获取存储信息", tags = {"publictransfer"})
    @RequestMapping(value = "/getstorage", method = RequestMethod.GET)
    @ResponseBody
    public RestResult<StorageBean> getStorage() {
        PublicStorageDTO publicStorageDTO = new PublicStorageDTO();
        publicStorageDTO.setTotalStorageSize(publicStorageService.getTotalStorageSize());
        publicStorageDTO.setUsedStorageSize(publicStorageService.getUsedStorageSize());
        publicStorageDTO.setFreeStorageSize(publicStorageService.getFreeStorageSize());
        return RestResult.success().data(publicStorageDTO);
    }

}
