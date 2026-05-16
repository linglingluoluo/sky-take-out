package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/admin/workspace")
@Slf4j
@Api(tags = "工作台相关接口")
public class WorkspaceController {
    @Autowired
    private WorkspaceService workspaceService;

    @GetMapping("/businessData")
    @ApiOperation("获取今日数据")
    public Result<BusinessDataVO> businessData(){
        log.info("开始获取今日营业数据...");
        return Result.success(workspaceService.getTodayBusinessData());
    }

    @GetMapping("/overviewOrders")
    @ApiOperation("订单总览")
    public Result<OrderOverViewVO> orderOverView(){
        log.info("开始获取订单总览...");
        return Result.success(workspaceService.getOrderOverView());
    }

    @GetMapping("/overviewDishes")
    @ApiOperation("菜品总览")
    public Result<DishOverViewVO> dishOverView(){
        log.info("开始获取菜品总览...");
        return Result.success(workspaceService.getDishOverView());
    }

    @GetMapping("/overviewSetmeals")
    @ApiOperation("订单总览")
    public Result<SetmealOverViewVO> setmealOverView(){
        log.info("开始获取套餐总览...");
        return Result.success(workspaceService.getSetmealOverView());
    }
}
