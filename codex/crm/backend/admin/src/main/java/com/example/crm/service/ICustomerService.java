package com.example.crm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.entity.CrmCustomer;

public interface ICustomerService {
    Page<CrmCustomer> page(long current, long size, String keyword);

    CrmCustomer detail(Long id);

    void create(CrmCustomer customer);

    void update(Long id, CrmCustomer customer);

    void delete(Long id);
}
