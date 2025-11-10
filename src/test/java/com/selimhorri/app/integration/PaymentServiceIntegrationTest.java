package com.selimhorri.app.integration;

import com.selimhorri.app.constant.AppConstant;
import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.domain.PaymentStatus;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.exception.custom.ResourceNotFoundException;
import com.selimhorri.app.repository.PaymentRepository;
import com.selimhorri.app.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for PaymentService verifying communication with Order Service.
 * Uses MockBean to simulate external Order Service responses via RestTemplate.
 * 
 * Tests follow Arrange-Act-Assert pattern and naming convention:
 * MethodName_WhenCondition_ExpectedBehavior
 * 
 * These tests validate the integration between Payment and Order services,
 * ensuring proper handling of successful responses, errors, and edge cases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private RestTemplate restTemplate;

    private static final String ORDER_API = AppConstant.DiscoveredDomainsApi.ORDER_SERVICE_API_URL;

    @BeforeEach
    void setup() {
        reset(restTemplate);
        paymentRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("save_WhenOrderServiceReturnsValidOrderedStatus_SavesPaymentAndUpdatesOrderStatus")
    void save_WhenOrderServiceReturnsValidOrderedStatus_SavesPaymentAndUpdatesOrderStatus() {
        // Arrange
        Integer orderId = 1000;
        OrderDto mockOrder = OrderDto.builder()
                .orderId(orderId)
                .orderStatus("ORDERED")
                .orderFee(299.99)
                .build();

        // Mock GET request to verify order
        when(restTemplate.getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class)))
                .thenReturn(mockOrder);

        // Mock PATCH request to update order status
        when(restTemplate.patchForObject(eq(ORDER_API + "/" + orderId + "/status"), isNull(), eq(Void.class)))
                .thenReturn(null);

        PaymentDto paymentDto = PaymentDto.builder()
                .orderDto(OrderDto.builder().orderId(orderId).build())
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .isPayed(false)
                .build();

        // Act
        PaymentDto savedPayment = paymentService.save(paymentDto);

        // Assert
        assertThat(savedPayment).isNotNull();
        assertThat(savedPayment.getPaymentId()).isNotNull();
        assertThat(savedPayment.getOrderDto()).isNotNull();
        assertThat(savedPayment.getOrderDto().getOrderId()).isEqualTo(orderId);
        assertThat(savedPayment.getOrderDto().getOrderStatus()).isEqualTo("ORDERED");

        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class));
        verify(restTemplate, times(1)).patchForObject(eq(ORDER_API + "/" + orderId + "/status"), isNull(), eq(Void.class));
    }

    @Test
    @Order(2)
    @DisplayName("save_WhenOrderServiceReturnsNonOrderedStatus_ThrowsInvalidInputException")
    void save_WhenOrderServiceReturnsNonOrderedStatus_ThrowsInvalidInputException() {
        // Arrange
        Integer orderId = 2000;
        OrderDto mockOrder = OrderDto.builder()
                .orderId(orderId)
                .orderStatus("COMPLETED")
                .orderFee(199.99)
                .build();

        when(restTemplate.getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class)))
                .thenReturn(mockOrder);

        PaymentDto paymentDto = PaymentDto.builder()
                .orderDto(OrderDto.builder().orderId(orderId).build())
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .isPayed(false)
                .build();

        // Act & Assert
        assertThatThrownBy(() -> paymentService.save(paymentDto))
                .hasMessageContaining("status");

        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class));
        verify(restTemplate, never()).patchForObject(anyString(), any(), eq(Void.class));
    }

    @Test
    @Order(3)
    @DisplayName("save_WhenOrderServiceReturns404_ThrowsResourceNotFoundException")
    void save_WhenOrderServiceReturns404_ThrowsResourceNotFoundException() {
        // Arrange
        Integer nonExistentOrderId = 9999;

        when(restTemplate.getForObject(eq(ORDER_API + "/" + nonExistentOrderId), eq(OrderDto.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Not Found",
                        null,
                        null,
                        null));

        PaymentDto paymentDto = PaymentDto.builder()
                .orderDto(OrderDto.builder().orderId(nonExistentOrderId).build())
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .isPayed(false)
                .build();

        // Act & Assert
        assertThatThrownBy(() -> paymentService.save(paymentDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + nonExistentOrderId), eq(OrderDto.class));
    }

    @Test
    @Order(4)
    @DisplayName("findById_WhenOrderServiceReturnsOrderData_EnrichesPaymentWithOrderDetails")
    void findById_WhenOrderServiceReturnsOrderData_EnrichesPaymentWithOrderDetails() {
        // Arrange
        Integer orderId = 3000;
        Payment savedPayment = paymentRepository.save(Payment.builder()
                .orderId(orderId)
                .paymentStatus(PaymentStatus.IN_PROGRESS)
                .isPayed(false)
                .build());

        OrderDto mockOrder = OrderDto.builder()
                .orderId(orderId)
                .orderStatus("IN_PAYMENT")
                .orderFee(599.99)
                .build();

        when(restTemplate.getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class)))
                .thenReturn(mockOrder);

        // Act
        PaymentDto foundPayment = paymentService.findById(savedPayment.getPaymentId());

        // Assert
        assertThat(foundPayment).isNotNull();
        assertThat(foundPayment.getPaymentId()).isEqualTo(savedPayment.getPaymentId());
        assertThat(foundPayment.getOrderDto()).isNotNull();
        assertThat(foundPayment.getOrderDto().getOrderId()).isEqualTo(orderId);
        assertThat(foundPayment.getOrderDto().getOrderStatus()).isEqualTo("IN_PAYMENT");
        assertThat(foundPayment.getOrderDto().getOrderFee()).isEqualTo(599.99);

        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class));
    }

    @Test
    @Order(5)
    @DisplayName("findById_WhenOrderServiceUnavailable_ThrowsExternalServiceException")
    void findById_WhenOrderServiceUnavailable_ThrowsExternalServiceException() {
        // Arrange
        Integer orderId = 4000;
        Payment savedPayment = paymentRepository.save(Payment.builder()
                .orderId(orderId)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .isPayed(false)
                .build());

        when(restTemplate.getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class)))
                .thenThrow(new RestClientException("Service Unavailable"));

        // Act & Assert
        assertThatThrownBy(() -> paymentService.findById(savedPayment.getPaymentId()))
                .hasMessageContaining("order");

        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + orderId), eq(OrderDto.class));
    }

    @Test
    @Order(6)
    @DisplayName("findAll_WhenOrderServiceReturnsMultipleOrders_EnrichesAllPaymentsSuccessfully")
    void findAll_WhenOrderServiceReturnsMultipleOrders_EnrichesAllPaymentsSuccessfully() {
        // Arrange
        Integer orderId1 = 5000;
        Integer orderId2 = 5001;

        paymentRepository.save(Payment.builder()
                .orderId(orderId1)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .isPayed(false)
                .build());

        paymentRepository.save(Payment.builder()
                .orderId(orderId2)
                .paymentStatus(PaymentStatus.IN_PROGRESS)
                .isPayed(false)
                .build());

        OrderDto mockOrder1 = OrderDto.builder()
                .orderId(orderId1)
                .orderStatus("IN_PAYMENT")
                .orderFee(199.99)
                .build();

        OrderDto mockOrder2 = OrderDto.builder()
                .orderId(orderId2)
                .orderStatus("IN_PAYMENT")
                .orderFee(299.99)
                .build();

        when(restTemplate.getForObject(eq(ORDER_API + "/" + orderId1), eq(OrderDto.class)))
                .thenReturn(mockOrder1);
        when(restTemplate.getForObject(eq(ORDER_API + "/" + orderId2), eq(OrderDto.class)))
                .thenReturn(mockOrder2);

        // Act
        var payments = paymentService.findAll();

        // Assert
        assertThat(payments).hasSize(2);
        assertThat(payments).allMatch(payment -> payment.getOrderDto() != null);
        assertThat(payments).extracting(p -> p.getOrderDto().getOrderFee())
                .containsExactlyInAnyOrder(199.99, 299.99);

        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + orderId1), eq(OrderDto.class));
        verify(restTemplate, times(1)).getForObject(eq(ORDER_API + "/" + orderId2), eq(OrderDto.class));
    }
}
