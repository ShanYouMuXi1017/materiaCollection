package com.qiwenshare.file.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiwenshare.file.domain.FileBean;
import com.qiwenshare.file.domain.PublicBean;

import java.util.List;

public interface PublicMapper extends BaseMapper<PublicBean> {


    void batchInsertFile(List<PublicBean> fileBeanList);



}
