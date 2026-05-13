package com.sky.task;


import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/*
定时任务类,定时处理订单状态
 */
@Component //定时任务类需要交给Spring容器管理
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 每分钟触发一次, 处理超时订单
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeOutOrder() {
        log.info("定时处理超时订单 : {}", LocalDateTime.now());
        //订单时间 < 当前时间 - 15分钟就是超时订单
        LocalDateTime orderTime = LocalDateTime.now().minusMinutes(15);
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, orderTime);

        if(orderList != null && !orderList.isEmpty()){
            for (Orders order : orderList) {
                order.setStatus(Orders.CANCELLED);
                order.setCancelTime(LocalDateTime.now());
                order.setCancelReason("订单超时,自动取消");
                orderMapper.update(order);
            }
        }
    }

    /**
     * 处理一直处于派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?") //每天凌晨一点触发
    public void processDeliveryOrder(){
        log.info("定时处理派送中的订单 : {}", LocalDateTime.now());
        //1点减去1个小时就是昨天的订单
        LocalDateTime time = LocalDateTime.now().minusHours(1);
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        if(orderList != null && !orderList.isEmpty()){
            for (Orders order : orderList) {
                order.setStatus(Orders.COMPLETED);
                orderMapper.update(order);
            }
        }
    }
}
