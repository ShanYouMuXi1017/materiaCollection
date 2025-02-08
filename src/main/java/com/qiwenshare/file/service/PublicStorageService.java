package com.qiwenshare.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qiwenshare.file.api.IStorageService;
import com.qiwenshare.file.domain.StorageBean;
import com.qiwenshare.file.domain.SysParam;
import com.qiwenshare.file.mapper.StorageMapper;
import com.qiwenshare.file.mapper.SysParamMapper;
import com.qiwenshare.file.mapper.UserFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;

@Slf4j
@Service
@Transactional(rollbackFor=Exception.class)
public class PublicStorageService{

    @Value("${ufop.local-storage-path}")
    private String localStoragePath;

    // 磁盘总量
    public Long getTotalStorageSize() {
        File win = new File(localStoragePath);
        if (win.exists()) {
            return win.getTotalSpace();
        }
        return 0L;
    }
    // 磁盘剩余总量
    public Long getFreeStorageSize() {
        File win = new File(localStoragePath);
        if (win.exists()) {
            return win.getFreeSpace();
        }
        return 0L;
    }

    // 磁盘已用容量
    public Long getUsedStorageSize() {
        File win = new File(localStoragePath);
        if (win.exists()) {
            return win.getTotalSpace()-win.getFreeSpace();
        }
        return 0L;
    }

    // 检查是否可以上传
    public boolean checkStorage(Long fileSize) {
        if (this.getFreeStorageSize() < fileSize) {
            return false;
        }
        return true;
    }
}
