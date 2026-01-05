package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;

import java.util.List;

public interface CategoryService {
    //分页查询
    PageResult page(CategoryPageQueryDTO categoryPageQueryDTO);

    void save(CategoryDTO categoryDTO);

    void startOrStop(Integer status, Long id);

    void deleteById(Long id);

    void update(CategoryDTO categoryDTO);

    List<Category> list(Integer type);
}
