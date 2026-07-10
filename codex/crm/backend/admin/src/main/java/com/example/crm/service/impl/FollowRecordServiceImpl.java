package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.entity.CrmFollowRecord;
import com.example.crm.mapper.CrmFollowRecordMapper;
import com.example.crm.service.IFollowRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FollowRecordServiceImpl implements IFollowRecordService {
    private final CrmFollowRecordMapper followRecordMapper;

    public FollowRecordServiceImpl(CrmFollowRecordMapper followRecordMapper) {
        this.followRecordMapper = followRecordMapper;
    }

    @Override
    public List<CrmFollowRecord> list(Long customerId) {
        LambdaQueryWrapper<CrmFollowRecord> query = new LambdaQueryWrapper<CrmFollowRecord>()
                .eq(customerId != null, CrmFollowRecord::getCustomerId, customerId)
                .orderByDesc(CrmFollowRecord::getCreatedAt);
        return followRecordMapper.selectList(query);
    }

    @Override
    public void create(CrmFollowRecord record) {
        followRecordMapper.insert(record);
    }
}
