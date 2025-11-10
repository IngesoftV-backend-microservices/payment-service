package com.selimhorri.app.service.impl;

import com.selimhorri.app.constant.AppConstant;
import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.domain.PaymentStatus;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.exception.custom.ExternalServiceException;
import com.selimhorri.app.exception.custom.InvalidInputException;
import com.selimhorri.app.exception.custom.InvalidPaymentStatusException;
import com.selimhorri.app.exception.custom.ResourceNotFoundException;
import com.selimhorri.app.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final String ORDER_API = AppConstant.DiscoveredDomainsApi.ORDER_SERVICE_API_URL;

    private Payment payment(Integer id, Integer orderId, PaymentStatus status, Boolean isPayed) {
        return Payment.builder()
                .paymentId(id)
                .orderId(orderId)
                .paymentStatus(status)
                .isPayed(isPayed)
                .build();
    }

    private OrderDto order(Integer id, String status) {
        return OrderDto.builder()
                .orderId(id)
                .orderStatus(status)
                .build();
    }

    private PaymentDto paymentDtoWithOrder(Integer orderId) {
        return PaymentDto.builder()
                .orderDto(OrderDto.builder().orderId(orderId).build())
                .build();
    }

    @BeforeEach
    void resetMocks() {
        clearInvocations(paymentRepository, restTemplate);
    }

    @Test
    @DisplayName("findAll_WhenOrdersFetchSuccessful_EnrichesAndReturnsList")
    void findAll_WhenOrdersFetchSuccessful_EnrichesAndReturnsList() {
        // Arrange
        when(paymentRepository.findAll()).thenReturn(Arrays.asList(
                payment(1, 10, PaymentStatus.NOT_STARTED, false),
                payment(2, 20, PaymentStatus.IN_PROGRESS, false)
        ));

        when(restTemplate.getForObject(ORDER_API + "/" + 10, OrderDto.class))
                .thenReturn(order(10, "IN_PAYMENT"));
        when(restTemplate.getForObject(ORDER_API + "/" + 20, OrderDto.class))
                .thenReturn(order(20, "IN_PAYMENT"));

        // Act
        List<PaymentDto> result = paymentService.findAll();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.getOrderDto()).isNotNull();
            assertThat(dto.getOrderDto().getOrderStatus()).isEqualTo("IN_PAYMENT");
        });
        verify(paymentRepository).findAll();
        verify(restTemplate, times(1)).getForObject(ORDER_API + "/" + 10, OrderDto.class);
        verify(restTemplate, times(1)).getForObject(ORDER_API + "/" + 20, OrderDto.class);
    }

    @Test
    @DisplayName("findAll_WhenOrderServiceFails_ReturnsListWithoutThrowing")
    void findAll_WhenOrderServiceFails_ReturnsListWithoutThrowing() {
        // Arrange
        when(paymentRepository.findAll()).thenReturn(List.of(
                payment(1, 30, PaymentStatus.NOT_STARTED, false)
        ));

        when(restTemplate.getForObject(ORDER_API + "/" + 30, OrderDto.class))
                .thenThrow(new RestClientException("boom"));

        // Act
        List<PaymentDto> result = paymentService.findAll();

        // Assert
        assertThat(result).hasSize(1);
        PaymentDto dto = result.get(0);
        assertThat(dto.getOrderDto()).isNotNull();
        assertThat(dto.getOrderDto().getOrderId()).isEqualTo(30);
        // enrichment failed safely, so status remains null
        assertThat(dto.getOrderDto().getOrderStatus()).isNull();
        verify(paymentRepository).findAll();
    }

    @Test
    @DisplayName("findById_WhenPaymentNotFound_ThrowsResourceNotFoundException")
    void findById_WhenPaymentNotFound_ThrowsResourceNotFoundException() {
        // Arrange
        when(paymentRepository.findById(99)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> paymentService.findById(99))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(paymentRepository).findById(99);
    }

    @Test
    @DisplayName("findById_WhenExternalErrorFetchingOrder_ThrowsExternalServiceException")
    void findById_WhenExternalErrorFetchingOrder_ThrowsExternalServiceException() {
        // Arrange
        when(paymentRepository.findById(5)).thenReturn(Optional.of(
                payment(5, 55, PaymentStatus.NOT_STARTED, false)
        ));
        when(restTemplate.getForObject(ORDER_API + "/" + 55, OrderDto.class))
                .thenThrow(new RestClientException("downstream error"));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.findById(5))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("save_WhenOrderIdMissing_ThrowsInvalidInputException")
    void save_WhenOrderIdMissing_ThrowsInvalidInputException() {
        // Arrange
        PaymentDto request = PaymentDto.builder()
                .orderDto(OrderDto.builder().orderId(null).build())
                .build();

        // Act + Assert
        assertThatThrownBy(() -> paymentService.save(request))
                .isInstanceOf(InvalidInputException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("save_WhenOrderStatusIsNotOrdered_ThrowsInvalidInputException")
    void save_WhenOrderStatusIsNotOrdered_ThrowsInvalidInputException() {
        // Arrange
        PaymentDto request = paymentDtoWithOrder(77);
        when(restTemplate.getForObject(ORDER_API + "/" + 77, OrderDto.class))
                .thenReturn(order(77, "CREATED"));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.save(request))
                .isInstanceOf(InvalidInputException.class);
        verify(restTemplate).getForObject(ORDER_API + "/" + 77, OrderDto.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("save_WhenOrderServiceUnavailable_ThrowsExternalServiceException")
    void save_WhenOrderServiceUnavailable_ThrowsExternalServiceException() {
        // Arrange
        PaymentDto request = paymentDtoWithOrder(88);
        when(restTemplate.getForObject(ORDER_API + "/" + 88, OrderDto.class))
                .thenThrow(new RestClientException("unavailable"));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.save(request))
                .isInstanceOf(ExternalServiceException.class);
        verify(restTemplate).getForObject(ORDER_API + "/" + 88, OrderDto.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("save_WhenSuccess_SetsDefaultsAndUpdatesOrderStatusAndReturnsEnrichedDto")
    void save_WhenSuccess_SetsDefaultsAndUpdatesOrderStatusAndReturnsEnrichedDto() {
        // Arrange
        PaymentDto request = paymentDtoWithOrder(55);
        when(restTemplate.getForObject(ORDER_API + "/" + 55, OrderDto.class))
                .thenReturn(order(55, "ORDERED"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            return payment(1, p.getOrderId(), p.getPaymentStatus(), p.getIsPayed());
        });

        // Act
        PaymentDto result = paymentService.save(request);

        // Assert
        assertThat(result.getPaymentId()).isEqualTo(1);
        assertThat(result.getIsPayed()).isFalse();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(result.getOrderDto()).isNotNull();
        assertThat(result.getOrderDto().getOrderId()).isEqualTo(55);

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedEntity = paymentCaptor.getValue();
        assertThat(savedEntity.getOrderId()).isEqualTo(55);
        assertThat(savedEntity.getIsPayed()).isFalse();
        assertThat(savedEntity.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);

        verify(restTemplate).patchForObject(ORDER_API + "/" + 55 + "/status", null, Void.class);
    }

    @Test
    @DisplayName("save_WhenOrderStatusUpdateFails_ThrowsExternalServiceException")
    void save_WhenOrderStatusUpdateFails_ThrowsExternalServiceException() {
        // Arrange
        PaymentDto request = paymentDtoWithOrder(66);
        when(restTemplate.getForObject(ORDER_API + "/" + 66, OrderDto.class))
                .thenReturn(order(66, "ORDERED"));

        when(paymentRepository.save(any(Payment.class))).thenReturn(
                payment(10, 66, PaymentStatus.NOT_STARTED, false)
        );

        doThrow(new RestClientException("patch failed"))
                .when(restTemplate)
                .patchForObject(ORDER_API + "/" + 66 + "/status", null, Void.class);

        // Act + Assert
        assertThatThrownBy(() -> paymentService.save(request))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("updateStatus_WhenPaymentNotFound_ThrowsResourceNotFoundException")
    void updateStatus_WhenPaymentNotFound_ThrowsResourceNotFoundException() {
        // Arrange
        when(paymentRepository.findById(999)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> paymentService.updateStatus(999))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateStatus_WhenNotStarted_GoesToInProgress")
    void updateStatus_WhenNotStarted_GoesToInProgress() {
        // Arrange
        when(paymentRepository.findById(1)).thenReturn(Optional.of(
                payment(1, 10, PaymentStatus.NOT_STARTED, false)
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentDto result = paymentService.updateStatus(1);

        // Assert
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("updateStatus_WhenInProgress_GoesToCompleted")
    void updateStatus_WhenInProgress_GoesToCompleted() {
        // Arrange
        when(paymentRepository.findById(2)).thenReturn(Optional.of(
                payment(2, 20, PaymentStatus.IN_PROGRESS, false)
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentDto result = paymentService.updateStatus(2);

        // Assert
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("updateStatus_WhenCompleted_ThrowsInvalidPaymentStatusException")
    void updateStatus_WhenCompleted_ThrowsInvalidPaymentStatusException() {
        // Arrange
        when(paymentRepository.findById(3)).thenReturn(Optional.of(
                payment(3, 30, PaymentStatus.COMPLETED, true)
        ));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.updateStatus(3))
                .isInstanceOf(InvalidPaymentStatusException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteById_WhenNotFound_ThrowsResourceNotFoundException")
    void deleteById_WhenNotFound_ThrowsResourceNotFoundException() {
        // Arrange
        when(paymentRepository.findById(404)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> paymentService.deleteById(404))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteById_WhenCompleted_ThrowsInvalidPaymentStatusException")
    void deleteById_WhenCompleted_ThrowsInvalidPaymentStatusException() {
        // Arrange
        when(paymentRepository.findById(7)).thenReturn(Optional.of(
                payment(7, 70, PaymentStatus.COMPLETED, true)
        ));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.deleteById(7))
                .isInstanceOf(InvalidPaymentStatusException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteById_WhenCancelable_SetsCanceledAndSaves")
    void deleteById_WhenCancelable_SetsCanceledAndSaves() {
        // Arrange
        when(paymentRepository.findById(8)).thenReturn(Optional.of(
                payment(8, 80, PaymentStatus.IN_PROGRESS, false)
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        paymentService.deleteById(8);

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
    }
}
