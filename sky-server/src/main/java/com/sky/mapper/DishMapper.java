package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;


@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /***
     * 插入菜品数据
     * @param dish
     */
//    @Options(useGeneratedKeys = true, keyProperty = "id") 要使用该注解, insert语句只能以注解的形式写出,
//    注解和注解一起使用, xml和xml一起使用
    @AutoFill(OperationType.INSERT)
    void insert(Dish dish);
}
