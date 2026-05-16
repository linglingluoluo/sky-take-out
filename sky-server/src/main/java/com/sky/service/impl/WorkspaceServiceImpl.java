package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 获取当天营业数据
     *
     * @return
     */
    @Override
    public BusinessDataVO getTodayBusinessData() {
        //获取新增用户数
        LocalDateTime begin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        //或者下面的写法
//        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        Map map = new HashMap();
        map.put("begin", begin);
        Integer newUsers = userMapper.countByMap(map);
        //获取总订单数
        Integer totalOrderCount = orderMapper.countByMap(map);
        //获取有效订单数
        map.put("status", Orders.COMPLETED);
        Integer validOrderCount = orderMapper.countByMap(map);
        //订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        //获取营业额
        Double turnover = orderMapper.sumByMap(map);
        Double unitPrice = 0.0;
        if (validOrderCount != 0) {
            unitPrice = turnover / validOrderCount;
        }
        //截取小数点后两位
        unitPrice = Double.parseDouble(String.format("%.2f", unitPrice));
        return BusinessDataVO.builder()
                .newUsers(newUsers)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .turnover(turnover)
                .unitPrice(unitPrice)
                .build();
    }

    /**
     * 统计当天订单总览数据
     * @return
     */
    @Override
    public OrderOverViewVO getOrderOverView() {
        LocalDateTime begin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        List<HashMap<String, Object>> list = orderMapper.getTodayOverView(begin, null);
        Map<Integer, Integer> result = getResultMap(list);
        //总订单数
        Integer allOrders = 0;
        for (Integer value : result.values()) {
            allOrders += value;
        }
        return OrderOverViewVO.builder()
                .allOrders(allOrders)
                .cancelledOrders(result.get(Orders.CANCELLED))
                .completedOrders(result.get(Orders.COMPLETED))
                .deliveredOrders(result.get(Orders.CONFIRMED))
                .waitingOrders(result.get(Orders.TO_BE_CONFIRMED))
                .build();
    }

    /**
     * 获取菜品总览
     * @return
     */
    @Override
    public DishOverViewVO getDishOverView() {
        List<HashMap<String, Object>> list = dishMapper.getDishOverView();
        Map<Integer, Integer> resultMap = getResultMap(list);
        return DishOverViewVO.builder()
                .discontinued(resultMap.get(StatusConstant.DISABLE))
                .sold(resultMap.get(StatusConstant.ENABLE))
                .build();
    }

    /**
     * 获取套餐总览
     * @return
     */
    @Override
    public SetmealOverViewVO getSetmealOverView() {
        List<HashMap<String, Object>> list = setmealMapper.getSetmealOverView();
        Map<Integer, Integer> resultMap = getResultMap(list);
        return SetmealOverViewVO.builder()
                .discontinued(resultMap.get(StatusConstant.DISABLE))
                .sold(resultMap.get(StatusConstant.ENABLE))
                .build();
    }

    /**
     * 将分组查询结果转变到map中
     * @param list
     * @return
     */
    private Map<Integer, Integer> getResultMap(List<HashMap<String, Object>> list) {
        Map<Integer, Integer> result = new HashMap<>();
        if (list != null && !list.isEmpty()) {
            for (HashMap<String, Object> mp : list) {
                Integer status = null, num = null;
                for (Map.Entry<String, Object> entry : mp.entrySet()) {
                    if ("status".equals(entry.getKey())) {
                        status = (Integer) entry .getValue();
                    } else if ("num".equals(entry.getKey())) {
                        num = ((Long) entry.getValue()).intValue();
                    }
                }
                result.put(status, num);
            }
        }
        return result;
    }
}
