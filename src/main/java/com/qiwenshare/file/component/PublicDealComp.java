package com.qiwenshare.file.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiwenshare.common.util.DateUtil;
import com.qiwenshare.common.util.MusicUtils;
import com.qiwenshare.common.util.security.SessionUtil;
import com.qiwenshare.file.api.IPublicService;
import com.qiwenshare.file.api.IShareFileService;
import com.qiwenshare.file.api.IShareService;
import com.qiwenshare.file.api.IUserService;
import com.qiwenshare.file.config.es.PublicSearch;
import com.qiwenshare.file.domain.*;
import com.qiwenshare.file.io.QiwenFile;
import com.qiwenshare.file.mapper.*;
import com.qiwenshare.file.util.QiwenFileUtil;
import com.qiwenshare.file.util.QiwenPublicUtil;
import com.qiwenshare.file.util.TreeNode;
import com.qiwenshare.ufop.factory.UFOPFactory;
import com.qiwenshare.ufop.operation.copy.Copier;
import com.qiwenshare.ufop.operation.copy.domain.CopyFile;
import com.qiwenshare.ufop.operation.download.Downloader;
import com.qiwenshare.ufop.operation.download.domain.DownloadFile;
import com.qiwenshare.ufop.operation.write.Writer;
import com.qiwenshare.ufop.operation.write.domain.WriteFile;
import com.qiwenshare.ufop.util.UFOPUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.flac.FlacFileReader;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyAPIC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 公共文件逻辑处理组件
 */
@Slf4j
@Component
public class PublicDealComp {
    @Resource
    private PublicFileMapper publicFileMapper;
    @Resource
    private PublicMapper publicMapper;
    @Resource
    private IUserService userService;
    @Resource
    private IShareService shareService;
    @Resource
    private IShareFileService shareFileService;
    @Resource
    private UFOPFactory ufopFactory;
    @Resource
    private MusicMapper musicMapper;
    @Autowired
    private ElasticsearchClient elasticsearchClient;

    public static Executor exec = Executors.newFixedThreadPool(20);

    /**
     * 获取重复文件名
     * <p>
     * 场景1: 文件还原时，在 savefilePath 路径下，保存 测试.txt 文件重名，则会生成 测试(1).txt
     * 场景2： 上传文件时，在 savefilePath 路径下，保存 测试.txt 文件重名，则会生成 测试(1).txt
     *
     * @param publicFile
     * @param savefilePath
     * @return
     */
    public String getRepeatFileName(PublicFile publicFile, String savefilePath) {
        String fileName = publicFile.getFileName();
        String extendName = publicFile.getExtendName();

        String userId = publicFile.getUserId();
        int isDir = publicFile.getIsDir();
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PublicFile::getFilePath, savefilePath)
                .eq(PublicFile::getDeleteFlag, 0)
                .eq(PublicFile::getUserId, userId)
                .eq(PublicFile::getFileName, fileName)
                .eq(PublicFile::getIsDir, isDir);
        if (publicFile.isFile()) {
            lambdaQueryWrapper.eq(PublicFile::getExtendName, extendName);
        }
        List<PublicFile> list = publicFileMapper.selectList(lambdaQueryWrapper);
        if (CollectionUtils.isEmpty(list)) {
            return fileName;
        }

        int i = 0;

        while (!CollectionUtils.isEmpty(list)) {
            i++;
            LambdaQueryWrapper<PublicFile> lambdaQueryWrapper1 = new LambdaQueryWrapper<>();
            lambdaQueryWrapper1.eq(PublicFile::getFilePath, savefilePath)
                    .eq(PublicFile::getDeleteFlag, 0)
                    .eq(PublicFile::getUserId, userId)
                    .eq(PublicFile::getFileName, fileName + "(" + i + ")")
                    .eq(PublicFile::getIsDir, isDir);
            if (publicFile.isFile()) {
                lambdaQueryWrapper1.eq(PublicFile::getExtendName, extendName);
            }
            list = publicFileMapper.selectList(lambdaQueryWrapper1);

        }

        return fileName + "(" + i + ")";

    }

    /**
     * 还原父文件路径
     * <p>
     * 1、回收站文件还原操作会将文件恢复到原来的路径下,当还原文件的时候，如果父目录已经不存在了，则需要把父母录给还原
     * 2、上传目录
     *
     * @param sessionUserId
     */
    public void restoreParentFilePath(QiwenFile qiwenFile, String sessionUserId) {

        if (qiwenFile.isFile()) {
            qiwenFile = qiwenFile.getParentFile();
        }
        while (qiwenFile.getParent() != null) {
            String fileName = qiwenFile.getName();
            String parentFilePath = qiwenFile.getParent();

            LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(PublicFile::getFilePath, parentFilePath)
                    .eq(PublicFile::getFileName, fileName)
                    .eq(PublicFile::getDeleteFlag, 0)
                    .eq(PublicFile::getIsDir, 1)
                    .eq(PublicFile::getUserId, sessionUserId);
            List<PublicFile> publicFileList = publicFileMapper.selectList(lambdaQueryWrapper);
            if (publicFileList.size() == 0) {
                PublicFile publicFile = QiwenPublicUtil.getQiwenDir(sessionUserId, parentFilePath, fileName);
                try {
                    publicFileMapper.insert(publicFile);
                } catch (Exception e) {
                    //ignore
                }
            }
            qiwenFile = new QiwenFile(parentFilePath, true);
        }
    }


    /**
     * 删除重复的子目录文件
     * <p>
     * 当还原目录的时候，如果其子目录在文件系统中已存在，则还原之后进行去重操作
     *
     * @param filePath
     * @param sessionUserId
     */
    public void deleteRepeatSubDirFile(String filePath, String sessionUserId) {
        log.debug("删除子目录：" + filePath);
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        lambdaQueryWrapper.select(PublicFile::getFileName, PublicFile::getFilePath)
                .likeRight(PublicFile::getFilePath, QiwenFileUtil.formatLikePath(filePath))
                .eq(PublicFile::getIsDir, 1)
                .eq(PublicFile::getDeleteFlag, 0)
                .eq(PublicFile::getUserId, sessionUserId)
                .groupBy(PublicFile::getFilePath, PublicFile::getFileName)
                .having("count(fileName) >= 2");
        List<PublicFile> repeatList = publicFileMapper.selectList(lambdaQueryWrapper);

        for (PublicFile publicFile : repeatList) {
            LambdaQueryWrapper<PublicFile> lambdaQueryWrapper1 = new LambdaQueryWrapper<>();
            lambdaQueryWrapper1.eq(PublicFile::getFilePath, publicFile.getFilePath())
                    .eq(PublicFile::getFileName, publicFile.getFileName())
                    .eq(PublicFile::getDeleteFlag, "0");
            List<PublicFile> userFiles = publicFileMapper.selectList(lambdaQueryWrapper1);
            for (int i = 0; i < userFiles.size() - 1; i++) {
                publicFileMapper.deleteById(userFiles.get(i).getUserFileId());
            }
        }
    }

    /**
     * 组织一个树目录节点，文件移动的时候使用
     *
     * @param treeNode
     * @param id
     * @param filePath
     * @param nodeNameQueue
     * @return
     */
    public TreeNode insertTreeNode(TreeNode treeNode, long id, String filePath, Queue<String> nodeNameQueue) {

        List<TreeNode> childrenTreeNodes = treeNode.getChildren();
        String currentNodeName = nodeNameQueue.peek();
        if (currentNodeName == null) {
            return treeNode;
        }

        QiwenFile qiwenFile = new QiwenFile(filePath, currentNodeName, true);
        filePath = qiwenFile.getPath();

        if (!isExistPath(childrenTreeNodes, currentNodeName)) {  //1、判断有没有该子节点，如果没有则插入
            //插入
            TreeNode resultTreeNode = new TreeNode();

            resultTreeNode.setFilePath(filePath);
            resultTreeNode.setLabel(nodeNameQueue.poll());
            resultTreeNode.setId(++id);

            childrenTreeNodes.add(resultTreeNode);

        } else {  //2、如果有，则跳过
            nodeNameQueue.poll();
        }

        if (nodeNameQueue.size() != 0) {
            for (int i = 0; i < childrenTreeNodes.size(); i++) {

                TreeNode childrenTreeNode = childrenTreeNodes.get(i);
                if (currentNodeName.equals(childrenTreeNode.getLabel())) {
                    childrenTreeNode = insertTreeNode(childrenTreeNode, id * 10, filePath, nodeNameQueue);
                    childrenTreeNodes.remove(i);
                    childrenTreeNodes.add(childrenTreeNode);
                    treeNode.setChildren(childrenTreeNodes);
                }

            }
        } else {
            treeNode.setChildren(childrenTreeNodes);
        }

        return treeNode;

    }

    /**
     * 判断该路径在树节点中是否已经存在
     *
     * @param childrenTreeNodes
     * @param path
     * @return
     */
    public boolean isExistPath(List<TreeNode> childrenTreeNodes, String path) {
        boolean isExistPath = false;

        try {
            for (TreeNode childrenTreeNode : childrenTreeNodes) {
                if (path.equals(childrenTreeNode.getLabel())) {
                    isExistPath = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        return isExistPath;
    }


    public void uploadESByUserFileId(String userFileId) {
        exec.execute(()->{
            try {

                Map<String, Object> param = new HashMap<>();
                param.put("userFileId", userFileId);
                List<PublicFile> userfileResult = publicFileMapper.selectByMap(param);
                if (userfileResult != null && userfileResult.size() > 0) {
                    PublicSearch PublicSearch = new PublicSearch();
                    BeanUtil.copyProperties(userfileResult.get(0), PublicSearch);
                /*if (PublicSearch.getIsDir() == 0) {

                    Reader reader = ufopFactory.getReader(PublicSearch.getStorageType());
                    ReadFile readFile = new ReadFile();
                    readFile.setFileUrl(PublicSearch.getFileUrl());
                    String content = reader.read(readFile);
                    //全文搜索
                    PublicSearch.setContent(content);

                }*/
                    elasticsearchClient.index(i -> i.index("publicsearch").id(PublicSearch.getUserFileId()).document(PublicSearch));
                }
            } catch (Exception e) {
                log.debug("ES更新操作失败，请检查配置");
            }
        });


    }

    public void deleteESByUserFileId(String userFileId) {
        exec.execute(() -> {
            try {
                elasticsearchClient.delete(d -> d
                        .index("publicsearch")
                        .id(userFileId));
            } catch (Exception e) {
                log.debug("ES删除操作失败，请检查配置");
            }
        });


    }

    /**
     * 根据用户传入的参数，判断是否有下载或者预览权限
     *
     * @return
     */
    public boolean checkAuthDownloadAndPreview(String shareBatchNum,
                                               String extractionCode,
                                               String token,
                                               String userFileIds,
                                               Integer platform) {
        log.debug("权限检查开始：shareBatchNum:{}, extractionCode:{}, token:{}, userFileIds{}", shareBatchNum, extractionCode, token, userFileIds);
        if (platform != null && platform == 2) {
            return true;
        }
        String[] userFileIdArr = userFileIds.split(",");
        for (String userFileId : userFileIdArr) {

            PublicFile publicFile = publicFileMapper.selectById(userFileId);
            log.debug(JSON.toJSONString(publicFile));
            if ("undefined".equals(shareBatchNum) || StringUtils.isEmpty(shareBatchNum)) {

                String userId = userService.getUserIdByToken(token);
                log.debug(JSON.toJSONString("当前登录session用户id：" + userId));
                if (userId == null) {
                    return false;
                }
                log.debug("文件所属用户id：" + publicFile.getUserId());
                log.debug("登录用户id:" + userId);
                if (!publicFile.getUserId().equals(userId)) {
                    log.info("用户id不一致，权限校验失败");
                    return false;
                }
            } else {
                Map<String, Object> param = new HashMap<>();
                param.put("shareBatchNum", shareBatchNum);
                List<Share> shareList = shareService.listByMap(param);
                //判断批次号
                if (shareList.size() <= 0) {
                    log.info("分享批次号不存在，权限校验失败");
                    return false;
                }
                Integer shareType = shareList.get(0).getShareType();
                if (1 == shareType) {
                    //判断提取码
                    if (!shareList.get(0).getExtractionCode().equals(extractionCode)) {
                        log.info("提取码错误，权限校验失败");
                        return false;
                    }
                }
                param.put("userFileId", userFileId);
                List<ShareFile> shareFileList = shareFileService.listByMap(param);
                if (shareFileList.size() <= 0) {
                    log.info("用户id和分享批次号不匹配，权限校验失败");
                    return false;
                }

            }

        }
        return true;
    }

    /**
     * 拷贝文件
     * 场景：修改的文件被多处引用时，需要重新拷贝一份，然后在新的基础上修改
     *
     * @param fileBean
     * @param publicFile
     * @return
     */
    public String copyFile(PublicBean fileBean, PublicFile publicFile) {
        Copier copier = ufopFactory.getCopier();
        Downloader downloader = ufopFactory.getDownloader(fileBean.getStorageType());
        DownloadFile downloadFile = new DownloadFile();
        downloadFile.setFileUrl(fileBean.getFileUrl());
        CopyFile copyFile = new CopyFile();
        copyFile.setExtendName(publicFile.getExtendName());
        String fileUrl = copier.copy(downloader.getInputStream(downloadFile), copyFile);
        if (downloadFile.getOssClient() != null) {
            downloadFile.getOssClient().shutdown();
        }
        fileBean.setFileUrl(fileUrl);
        fileBean.setFileId(IdUtil.getSnowflakeNextIdStr());
        publicMapper.insert(fileBean);
        publicFile.setFileId(fileBean.getFileId());
        publicFile.setUploadTime(DateUtil.getCurrentTime());
        publicFile.setModifyTime(DateUtil.getCurrentTime());
        publicFile.setModifyUserId(SessionUtil.getUserId());
        publicFileMapper.updateById(publicFile);
        return fileUrl;
    }

    public String getIdentifierByFile(String fileUrl, int storageType) throws IOException {
        DownloadFile downloadFile = new DownloadFile();
        downloadFile.setFileUrl(fileUrl);
        InputStream inputStream = ufopFactory.getDownloader(storageType).getInputStream(downloadFile);
        return DigestUtils.md5Hex(inputStream);
    }

    public void saveFileInputStream(int storageType, String fileUrl, InputStream inputStream) throws IOException {
        Writer writer1 = ufopFactory.getWriter(storageType);
        WriteFile writeFile = new WriteFile();
        writeFile.setFileUrl(fileUrl);
        int fileSize = inputStream.available();
        writeFile.setFileSize(fileSize);
        writer1.write(inputStream, writeFile);
    }

    // 这个人上传的文件夹名存在吗？
    public boolean isDirExist(String fileName, String filePath, String userId) {
        LambdaQueryWrapper<PublicFile> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PublicFile::getFileName, fileName)
                .eq(PublicFile::getFilePath, QiwenFile.formatPath(filePath))
                .eq(PublicFile::getUserId, userId)
                .eq(PublicFile::getDeleteFlag, 0)
                .eq(PublicFile::getIsDir, 1);
        List<PublicFile> list = publicFileMapper.selectList(lambdaQueryWrapper);
        if (list != null && !list.isEmpty()) {
            return true;
        }
        return false;
    }


    public void parseMusicFile(String extendName, int storageType, String fileUrl, String fileId) {
        File outFile = null;
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            if ("mp3".equalsIgnoreCase(extendName) || "flac".equalsIgnoreCase(extendName)) {
                Downloader downloader = ufopFactory.getDownloader(storageType);
                DownloadFile downloadFile = new DownloadFile();
                downloadFile.setFileUrl(fileUrl);
                inputStream = downloader.getInputStream(downloadFile);
                outFile = UFOPUtils.getTempFile(fileUrl);
                if (!outFile.exists()) {
                    outFile.createNewFile();
                }
                fileOutputStream = new FileOutputStream(outFile);
                IOUtils.copy(inputStream, fileOutputStream);
                Music music = new Music();
                music.setMusicId(IdUtil.getSnowflakeNextIdStr());
                music.setFileId(fileId);

                Tag tag = null;
                AudioHeader audioHeader = null;
                if ("mp3".equalsIgnoreCase(extendName)) {
                    MP3File f = (MP3File) AudioFileIO.read(outFile);
                    tag = f.getTag();
                    audioHeader = f.getAudioHeader();
                    MP3File mp3file = new MP3File(outFile);
                    if (mp3file.hasID3v2Tag()) {
                        AbstractID3v2Tag id3v2Tag = mp3file.getID3v2TagAsv24();
                        AbstractID3v2Frame frame = (AbstractID3v2Frame) id3v2Tag.getFrame("APIC");
                        FrameBodyAPIC body;
                        if (frame != null && !frame.isEmpty()) {
                            body = (FrameBodyAPIC) frame.getBody();
                            byte[] imageData = body.getImageData();
                            music.setAlbumImage(Base64.getEncoder().encodeToString(imageData));
                        }
                        if (tag != null) {
                            music.setArtist(tag.getFirst(FieldKey.ARTIST));
                            music.setTitle(tag.getFirst(FieldKey.TITLE));
                            music.setAlbum(tag.getFirst(FieldKey.ALBUM));
                            music.setYear(tag.getFirst(FieldKey.YEAR));
                            try {
                                music.setTrack(tag.getFirst(FieldKey.TRACK));
                            } catch (Exception e) {
                                // ignore
                            }

                            music.setGenre(tag.getFirst(FieldKey.GENRE));
                            music.setComment(tag.getFirst(FieldKey.COMMENT));
                            music.setLyrics(tag.getFirst(FieldKey.LYRICS));
                            music.setComposer(tag.getFirst(FieldKey.COMPOSER));
                            music.setAlbumArtist(tag.getFirst(FieldKey.ALBUM_ARTIST));
                            music.setEncoder(tag.getFirst(FieldKey.ENCODER));
                        }
                    }
                } else if ("flac".equalsIgnoreCase(extendName)) {
                    AudioFile f = new FlacFileReader().read(outFile);
                    tag = f.getTag();
                    audioHeader = f.getAudioHeader();
                    if (tag != null) {
                        music.setArtist(StringUtils.join(tag.getFields(FieldKey.ARTIST), ","));
                        music.setTitle(StringUtils.join(tag.getFields(FieldKey.TITLE), ","));
                        music.setAlbum(StringUtils.join(tag.getFields(FieldKey.ALBUM), ","));
                        music.setYear(StringUtils.join(tag.getFields(FieldKey.YEAR), ","));
                        music.setTrack(StringUtils.join(tag.getFields(FieldKey.TRACK), ","));
                        music.setGenre(StringUtils.join(tag.getFields(FieldKey.GENRE), ","));
                        music.setComment(StringUtils.join(tag.getFields(FieldKey.COMMENT), ","));
                        music.setLyrics(StringUtils.join(tag.getFields(FieldKey.LYRICS), ","));
                        music.setComposer(StringUtils.join(tag.getFields(FieldKey.COMPOSER), ","));
                        music.setAlbumArtist(StringUtils.join(tag.getFields(FieldKey.ALBUM_ARTIST), ","));
                        music.setEncoder(StringUtils.join(tag.getFields(FieldKey.ENCODER), ","));
                        List<Artwork> artworkList = tag.getArtworkList();
                        if (artworkList != null && !artworkList.isEmpty()) {
                            Artwork artwork = artworkList.get(0);
                            byte[] binaryData = artwork.getBinaryData();
                            music.setAlbumImage(Base64.getEncoder().encodeToString(binaryData));
                        }
                    }

                }

                if (audioHeader != null) {
                    music.setTrackLength(Float.parseFloat(audioHeader.getTrackLength() + ""));
                }

                if (StringUtils.isEmpty(music.getLyrics())) {
                    try {

                        String lyc = MusicUtils.getLyc(music.getArtist(), music.getTitle(), music.getAlbum());
                        music.setLyrics(lyc);
                    } catch (Exception e) {
                        log.info(e.getMessage());
                    }
                }
                musicMapper.insert(music);
            }
        } catch (Exception e) {
            log.error("解析音乐信息失败！", e);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);
            if (outFile != null) {
                if (outFile.exists()) {
                    outFile.delete();
                }
            }
        }
    }

}
