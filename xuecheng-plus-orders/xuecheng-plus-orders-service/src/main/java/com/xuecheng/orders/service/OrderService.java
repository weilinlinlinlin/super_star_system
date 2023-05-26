package com.xuecheng.orders.service;

import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcPayRecord;

/**
 * @author Mr.M
 * @version 1.0
 * @description 订单相关的service接口
 * @date 2023/2/26 10:11
 */
public interface OrderService {

 /**
  * @description 创建商品订单
  * @param addOrderDto 订单信息
  * @return PayRecordDto 支付记录(包括二维码)
  * @author Mr.M
  * @date 2022/10/4 11:02
  */
 public PayRecordDto createOrder(String userId, AddOrderDto addOrderDto);

 /**
  * @description 查询支付记录
  * @param payNo  交易记录号
  * @return com.xuecheng.orders.model.po.XcPayRecord
  * @author Mr.M
  * @date 2022/10/20 23:38
  */
 public XcPayRecord getPayRecordByPayno(String payNo);

 /**
  * 请求支付宝查询支付结果
  * @param payNo 支付记录id
  * @return 支付记录信息
  */
 public PayRecordDto queryPayResult(String payNo);

 /**
  * 保存支付状态
  * @param payStatusDto
  */
 public void saveAliPayStatus(PayStatusDto payStatusDto);

 /**
  * 发送通知结果
  * @param message
  */
 public void notifyPayResult(MqMessage message);
}
