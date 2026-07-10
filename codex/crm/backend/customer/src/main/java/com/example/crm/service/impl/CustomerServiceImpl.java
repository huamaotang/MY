package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.entity.CrmCustomer;
import com.example.crm.mapper.CrmCustomerMapper;
import com.example.crm.service.ICustomerService;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl implements ICustomerService {
    private final CrmCustomerMapper customerMapper;

    public CustomerServiceImpl(CrmCustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Override
    public Page<CrmCustomer> page(long current, long size, String keyword) {
        LambdaQueryWrapper<CrmCustomer> query = new LambdaQueryWrapper<CrmCustomer>()
                .like(keyword != null && !keyword.trim().isEmpty(), CrmCustomer::getCustomerName, keyword)
                .orderByDesc(CrmCustomer::getUpdatedAt);
        return customerMapper.selectPage(new Page<>(current, size), query);
    }

    @Override
    public CrmCustomer detail(Long id) {
        return customerMapper.selectById(id);
    }

    @Override
    public void create(CrmCustomer customer) {
        customerMapper.insert(customer);
    }

    @Override
    public void update(Long id, CrmCustomer customer) {
        customer.setId(id);
        customerMapper.updateById(customer);
    }

    @Override
    public void delete(Long id) {
        customerMapper.deleteById(id);
    }
}
