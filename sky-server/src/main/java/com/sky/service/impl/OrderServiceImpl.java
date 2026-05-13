package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.socket.WebSocketServer;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;
    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //1.处理各种业务异常(地址簿为空,购物车数据为空)
        AddressBook address = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (address == null) {
            //没有获取到用户地址,抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        
        //检查用户的收货地址是否超出配送范围
//        checkOutOfRange(address.getCityName() + address.getDistrictName() + address.getDetail());

        //获取当前用户id,构造查询参数shoppingCart,再查询购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            //购物车没有数据
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        //2.向订单表中插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis())); //订单号设置为当前时间
        orders.setPhone(address.getPhone());
        orders.setConsignee(address.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(address.getDetail());

        orderMapper.insert(orders);

        //3.向订单明细表批量插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); //设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);


        //4.清空当前用户购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //5.封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }


    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
//        // 当前登录用户id
//        Long userId = BaseContext.getCurrentId();
//        User user = userMapper.getById(userId);
//
//        //支付流程第5步:调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal("0.01"), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }
//
//        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
//        vo.setPackageStr(jsonObject.getString("package"));

        log.info("跳过微信支付成功...");
        paySuccess(ordersPaymentDTO.getOrderNumber());
        return new OrderPaymentVO();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        //通过websocket向客户端推送消息，type, orderId, content
        Map<String, Object> mp = new HashMap<>();
        mp.put("id", ordersDB.getId());
        mp.put("type", 1); //1表示来单提醒，2表示用户催单
        mp.put("content", "订单号：" + ordersDB.getNumber());
        JSONObject jsonObject = new JSONObject(mp);
        webSocketServer.sendToAllClient(jsonObject.toJSONString());
    }



    /**
     * 分页查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Long userId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(userId);
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> vos = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders order : page) {
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                orderVO.setOrderDetailList(orderDetails);
                vos.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), vos);
    }

    /**
     * 根据id查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO getById(Long id) {
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        if (orders != null) {
            BeanUtils.copyProperties(orders, orderVO);
            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);
            orderVO.setOrderDetailList(orderDetails);
        }
        return orderVO;
    }

    /**
     * - 待支付和待接单状态下，用户可直接取消订单
     * - 商家已接单状态下，用户取消订单需电话沟通商家
     * - 派送中状态下，用户取消订单需电话沟通商家
     * - 如果在待接单状态下取消订单，需要给用户退款
     * - 取消订单后需要将订单状态修改为“已取消”
     *
     * @param id
     */
    @Override
    //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
    public void userCancelById(Long id) {
        Orders order = orderMapper.getById(id);
        //订单不存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer orderStatus = order.getStatus();
        //订单不能立即取消
        if (orderStatus > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 订单处于待接单状态下取消，需要进行退款
//        if (orderStatus.equals(Orders.TO_BE_CONFIRMED)) {
//            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    order.getNumber(), //商户订单号
//                    order.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额
//
//            //支付状态修改为 退款
//            order.setPayStatus(Orders.REFUND);
//        }


        order.setStatus(Orders.CANCELLED);
        order.setCancelReason("用户取消");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 再来一单:错误的方法, 直接重新下单了,应该是将订单重新加入到购物车中
     *
     * @param id
     */
    @Transactional
    @Deprecated
    public void repeatOrder(Long id) {
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        //1.处理各种业务异常(订单不存在,地址簿为空)
        if (orders == null || orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Long userId = BaseContext.getCurrentId();
        //获取当前用户默认地址
        AddressBook addressQuery = AddressBook.builder()
                .isDefault(1)
                .userId(userId)
                .build();
        List<AddressBook> list = addressBookMapper.list(addressQuery);
        if (list == null || list.size() != 1) {
            //没有获取到用户地址,抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        AddressBook addressBook = list.get(0);

        orders.setNumber(String.valueOf(System.currentTimeMillis())); //订单号设置为当前时间
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setAddressBookId(addressBook.getId());
        orders.setOrderTime(LocalDateTime.now());
        orders.setCheckoutTime(null);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setAddress(addressBook.getDetail());
        orders.setConsignee(addressBook.getConsignee());
        orders.setRejectionReason(null);
        orders.setCancelTime(null);
        orders.setCancelReason(null);
        orders.setEstimatedDeliveryTime(null);
        orders.setDeliveryTime(LocalDateTime.now().plusHours(1)); //送达时间简单设定为下单时间后的1h

        //往订单表中插入数据
        orderMapper.insert(orders);
        //向订单明细表插入数据
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orders.getId());
        }
        orderDetailMapper.insertBatch(orderDetailList);
    }

    @Override
    @Transactional
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 商家后端进行订单查询
     * - 输入订单号/手机号进行搜索，支持模糊搜索
     * - 根据订单状态进行筛选
     * - 下单时间进行时间筛选
     * - 搜索内容为空，提示未找到相关订单
     * - 搜索结果页，展示包含搜索关键词的内容
     * - 分页展示搜索到的订单数据
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery4Admin(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //要包含订单菜品信息,将Orders转化为orderVo返回
        List<OrderVO> orderVOList = page.stream().map(order -> {
            String orderDishs = getOrderDishStr(order);
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            orderVO.setOrderDishes(orderDishs);
            return orderVO;
        }).toList();
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 统计订单各状态的数量
     *
     * @return
     */
    @Override
    public OrderStatisticsVO getOrderStatistics() {
        Integer tobeConfirmed = orderMapper.getOrderStatistics(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.getOrderStatistics(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.getOrderStatistics(Orders.DELIVERY_IN_PROGRESS);
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(tobeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 完成订单, 条件 : 订单存在且已经处于配送期
     * 更新订单状态,订单送达时间
     *
     * @param id
     */
    @Override
    public void completeOrder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null || !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orderDB = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orderDB);
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirmOrder(OrdersConfirmDTO ordersConfirmDTO) {
        Long id = ordersConfirmDTO.getId();
        Orders orders = orderMapper.getById(id);
        if(orders == null || !orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orderDB = Orders.builder()
                .id(id)
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orderDB);
    }

    /**
     * 商家拒单
     * - 商家拒单其实就是将订单状态修改为“已取消”
     * - 只有订单处于“待接单”状态时可以执行拒单操作
     * - 商家拒单时需要指定拒单原因
     * - 商家拒单时，如果用户已经完成了支付，需要为用户退款
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());
        if(orders == null || !orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //支付状态
//        Integer payStatus = ordersDB.getPayStatus();
//        if (payStatus == Orders.PAID) {
//            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
//        }
        Orders orderDB = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orderDB);
    }

    /**
     * - 取消订单其实就是将订单状态修改为“已取消”
     * - 商家取消订单时需要指定取消原因
     * - 商家取消订单时，如果用户已经完成了支付，需要为用户退款
     * @param ordersCancelDTO
     */
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        Long id = ordersCancelDTO.getId();
        Orders orders = orderMapper.getById(id);

        //支付状态
//        Integer payStatus = ordersDB.getPayStatus();
//        if (payStatus == 1) {
//            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
//        }
        Orders orderDB = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orderDB);
    }

    @Override
    public void delivery(Long id) {
        Orders orders = orderMapper.getById(id);

        if(orders == null || !orders.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orderDB = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orderDB);
    }

    /**
     * 客户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        if(ordersDB == null ){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //通过websocket向客户端推送消息，type, orderId, content
        Map<String, Object> mp = new HashMap<>();
        mp.put("id", ordersDB.getId());
        mp.put("type", 2); //1表示来单提醒，2表示用户催单
        mp.put("content", "订单号：" + ordersDB.getNumber());
        JSONObject jsonObject = new JSONObject(mp);
        webSocketServer.sendToAllClient(jsonObject.toJSONString());
    }

    /**
     * 根据订单得到订单包含的菜品及其数量
     *
     * @param order
     * @return
     */
    private String getOrderDishStr(Orders order) {
        Long orderId = order.getId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
        List<String> orderDishList = orderDetailList.stream().map(orderDetail -> {
            String name = orderDetail.getName();
            Integer number = orderDetail.getNumber();
            return name + " * " + number + ";";
        }).collect(Collectors.toList());
        return String.join("", orderDishList);
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map<String, String> map = new HashMap<>();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }
}
