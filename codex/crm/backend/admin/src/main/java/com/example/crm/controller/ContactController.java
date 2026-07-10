package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.entity.CrmContact;
import com.example.crm.service.IContactService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/contacts")
public class ContactController {
    private final IContactService contactService;

    public ContactController(IContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:contact:list')")
    public ApiResponse<List<CrmContact>> list(@RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(contactService.list(customerId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:update')")
    public ApiResponse<Void> create(@RequestBody CrmContact contact) {
        contactService.create(contact);
        return ApiResponse.ok();
    }
}
