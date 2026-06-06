package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangesListener {
    private final ILearningLessonService lessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order) {
        //1.健壮性处理
        if (order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            //数据有误,无需处理
            log.error("接收到目前消息有误,订单为空");
            return;
        }
        //2.添加课程
        log.debug("监听到用户{}的订单{},需要添加课程{}到课表中", order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.addUserLessons(order.getUserId(), order.getCourseIds());
    }

    //监听到课程退款信息,实现删除课程
    /*rabbitMqHelper.send(
    MqConstants.Exchange.ORDER_EXCHANGE,
    MqConstants.Key.ORDER_REFUND_KEY,
            OrderBasicDTO.builder()
            .orderId(refundApply.getOrderId())
            .userId(refundApply.getUserId())
            .courseIds(CollUtils.singletonList(detail.getCourseId())).build());*/
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void refundToDelete(OrderBasicDTO order){
        //1.健壮性处理
        if(order == null || CollUtils.isEmpty(order.getCourseIds()) || order.getUserId() == null){
            //数据有误,无需处理
            log.info("接收到目前消息有误,订单为空:{}", order);
            return;
        }
        //2.移除课程
        log.debug("监听到用户{}的订单{},需要从课程表中移除课程{}",order.getUserId(), order.getOrderId(),order.getCourseIds());
        lessonService.removeLesson(order.getUserId(), order.getCourseIds().get(0));
    }
}

