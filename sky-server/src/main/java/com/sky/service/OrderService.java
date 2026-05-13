package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {

    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderVO getById(Long id);

    void userCancelById(Long id);


    void repetition(Long id);

    PageResult pageQuery4Admin(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO getOrderStatistics();

    void completeOrder(Long id);

    void confirmOrder(OrdersConfirmDTO ordersConfirmDTO);

    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    void adminCancel(OrdersCancelDTO ordersCancelDTO);

    void delivery(Long id);

    void reminder(Long id);
}
