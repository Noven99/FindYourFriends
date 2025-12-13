package com.hao.withME.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hao.withME.mapper.TagMapper;
import com.hao.withME.model.domain.Tag;
import com.hao.withME.service.TagService;
import org.springframework.stereotype.Service;

/**
* @author 86182
* @description 针对表【tag(标签)】的数据库操作Service实现
* @createDate 2025-12-11 14:27:25
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService {

}




