package com.example.crm.service;

import com.example.crm.entity.CrmContact;

import java.util.List;

public interface IContactService {
    List<CrmContact> list(Long customerId);

    void create(CrmContact contact);
}
