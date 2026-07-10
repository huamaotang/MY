package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.entity.CrmContact;
import com.example.crm.mapper.CrmContactMapper;
import com.example.crm.service.IContactService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactServiceImpl implements IContactService {
    private final CrmContactMapper contactMapper;

    public ContactServiceImpl(CrmContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    @Override
    public List<CrmContact> list(Long customerId) {
        LambdaQueryWrapper<CrmContact> query = new LambdaQueryWrapper<CrmContact>()
                .eq(customerId != null, CrmContact::getCustomerId, customerId)
                .orderByDesc(CrmContact::getUpdatedAt);
        return contactMapper.selectList(query);
    }

    @Override
    public void create(CrmContact contact) {
        contactMapper.insert(contact);
    }
}
