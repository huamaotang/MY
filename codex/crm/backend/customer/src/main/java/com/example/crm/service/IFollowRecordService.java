package com.example.crm.service;

import com.example.crm.entity.CrmFollowRecord;

import java.util.List;

public interface IFollowRecordService {
    List<CrmFollowRecord> list(Long customerId);

    void create(CrmFollowRecord record);
}
