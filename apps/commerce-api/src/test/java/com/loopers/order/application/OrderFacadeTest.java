package com.loopers.order.application;

import com.loopers.coupon.domain.CouponService;
import com.loopers.coupon.domain.vo.CouponDiscount;
import com.loopers.order.application.event.OrderEventPublisher;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItems;
import com.loopers.order.domain.OrderService;
import com.loopers.stock.domain.ProductStockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;

    @Mock
    private OrderItemFactory orderItemFactory;

    @Mock
    private ProductStockService productStockService;

    @Mock
    private CouponService couponService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderFacade orderFacade;

    @DisplayName("주문을 생성할 때")
    @Nested
    class CreateOrder {

        @DisplayName("주문 저장 후 주문 생성 이벤트를 발행한다.")
        @Test
        void publishesOrderCreatedEvent_afterOrderIsSaved() {
            // arrange
            CreateOrderCommand command = new CreateOrderCommand(
                USER_ID,
                List.of(new CreateOrderCommand.Item(PRODUCT_ID, 2))
            );
            OrderItems orderItems = OrderItems.of(List.of(
                OrderItem.create(1L, "Apple", PRODUCT_ID, "iPhone 16 Pro", 1_550_000L, 2)
            ));
            when(orderItemFactory.create(command.items())).thenReturn(orderItems);
            when(couponService.use(any())).thenReturn(CouponDiscount.none());
            when(orderService.saveOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderInfo info = orderFacade.createOrder(command);

            // assert
            ArgumentCaptor<OrderInfo> infoCaptor = ArgumentCaptor.forClass(OrderInfo.class);
            verify(orderEventPublisher).publishCreated(infoCaptor.capture(), any(ZonedDateTime.class));
            OrderInfo eventOrder = infoCaptor.getValue();

            assertAll(
                () -> assertThat(eventOrder.userId()).isEqualTo(USER_ID),
                () -> assertThat(eventOrder.paymentAmount()).isEqualTo(info.paymentAmount()),
                () -> assertThat(eventOrder.items()).hasSize(1)
            );
        }
    }
}
