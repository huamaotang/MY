package com.example.crm.android;

import java.util.ArrayList;
import java.util.List;

public class PageResult<T> {
    public List<T> records = new ArrayList<>();
    public int total;
    public int size;
    public int current;
}
