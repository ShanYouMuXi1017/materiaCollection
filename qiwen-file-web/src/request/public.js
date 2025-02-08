// 公共区域文件模块相关接口
import { get, post } from './http'

/**
 * 获取文件列表相关接口
 */
// 获取文件列表（区分文件路径）
export const getFileListByPath = (p) => get('/public/getfilelist', p)
// 获取文件列表（区分文件类型）
export const getFileListByType = (p) => get('/public/selectfilebyfiletype', p)
// 获取回收站文件列表
export const getRecoveryFile = (p) => get('/recoveryfile/list', p)

// 获取存储占用
export const getStorage = (p) => get('/filetransfer/getstorage', p)
// 获取文件目录树
export const getFoldTree = (p) => get('/public/getfiletree', p)

/**
 * 单文件操作相关接口
 */
// 创建文件
export const createFold = (p) => post('/public/createFold', p)
// 获取文件详细信息
export const getFileDetail = (p) => get('/public/detail', p)
// 删除文件
export const deleteFile = (p) => post('/public/deletefile', p)
// 复制文件
export const copyFile = (p) => post('/public/copyfile', p)
// 移动文件
export const moveFile = (p) => post('/public/movefile', p)
// 重命名文件
export const renameFile = (p) => post('/public/renamefile', p)
// 解压文件
export const unzipFile = (p) => post('/public/unzipfile', p)
// 全局搜索文件
export const searchFile = (p) => get('/public/search', p)


/**
 * 文件批量操作相关接口
 */
// 批量删除文件
export const batchDeleteFile = (p) => post('/public/batchdeletefile', p)
// 批量移动文件
export const batchMoveFile = (p) => post('/public/batchmovefile', p)

/**
 * 回收站文件操作相关接口
 */
// 回收站文件删除
export const deleteRecoveryFile = (p) =>
	post('/recoveryfile/deleterecoveryfile', p)
// 回收站文件还原
export const restoreRecoveryFile = (p) => post('/recoveryfile/restorefile', p)
// 回收站文件批量删除
export const batchDeleteRecoveryFile = (p) =>
	post('/recoveryfile/batchdelete', p)

/**
 * 文件公共接口
 */
// 文件预览
export const getFilePreview = (p) => get('/filetransfer/preview', p)
// 文件修改
export const modifyFileContent = (p) => post('/public/update', p)


// 获取我已分享的文件列表
export const getMyShareFileList = (p) => get('/share/shareList', p)
