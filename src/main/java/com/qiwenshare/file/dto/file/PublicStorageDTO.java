package com.qiwenshare.file.dto.file;

import lombok.Data;

/**
 * @ Description： 公共区域磁盘显示
 * @ Author： 程序员好冰
 * @ Date： 2025/02/08/20:33
 */

@Data
public class PublicStorageDTO {
    private Long totalStorageSize;
    private Long freeStorageSize;
    private Long usedStorageSize;
}
