package com.selimhorri.app.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.constant.AppConstant;
import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.domain.PaymentStatus;
import com.selimhorri.app.domain.enums.OrderStatus;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.exception.ErrorCode;
import com.selimhorri.app.exception.custom.ExternalServiceException;
import com.selimhorri.app.exception.custom.InvalidInputException;
import com.selimhorri.app.exception.custom.InvalidPaymentStatusException;
import com.selimhorri.app.exception.custom.ResourceNotFoundException;
import com.selimhorri.app.helper.PaymentMappingHelper;
import com.selimhorri.app.metrics.PaymentBusinessMetrics;
import com.selimhorri.app.repository.PaymentRepository;
import com.selimhorri.app.service.PaymentService;

import io.micrometer.core.instrument.Timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final PaymentBusinessMetrics businessMetrics;

    @Override
    public List<PaymentDto> findAll() {
        log.info("Fetching all payments");

        return this.paymentRepository.findAll()
                .stream()
                .map(PaymentMappingHelper::map)
                .map(this::enrichWithOrderDataSafely)
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public PaymentDto findById(final Integer paymentId) {
        log.info("Fetching payment with id: {}", paymentId);
        
        PaymentDto paymentDto = this.paymentRepository.findById(paymentId)
                .map(PaymentMappingHelper::map)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PAYMENT_NOT_FOUND, paymentId));

        enrichWithOrderData(paymentDto);
        return paymentDto;
    }

    @Override
    @Transactional
    public PaymentDto save(final PaymentDto paymentDto) {
        log.info("Saving payment for order: {}", paymentDto.getOrderDto().getOrderId());

        Timer.Sample timer = businessMetrics.startPaymentProcessingTimer();
        
        try {
            validateOrderId(paymentDto);
            OrderDto orderDto = verifyOrderEligibility(paymentDto.getOrderDto().getOrderId());
            
            PaymentDto savedPayment = PaymentMappingHelper.map(
                    this.paymentRepository.save(PaymentMappingHelper.mapForPayment(paymentDto)));
            
            updateOrderStatus(paymentDto.getOrderDto().getOrderId());
            
            savedPayment.setOrderDto(orderDto);
            
            // Registrar métricas de negocio
            businessMetrics.recordPaymentAttempt(savedPayment.getPaymentStatus());
            
            // Si el pago se crea directamente con estado COMPLETED o CANCELED,
            // registrar éxito/fallo inmediatamente (solo una vez al crear)
            if (savedPayment.getPaymentStatus() == PaymentStatus.COMPLETED) {
                businessMetrics.recordPaymentSuccess();
            } else if (savedPayment.getPaymentStatus() == PaymentStatus.CANCELED) {
                businessMetrics.recordPaymentFailure();
            }
            
            return savedPayment;
        } finally {
            businessMetrics.stopPaymentProcessingTimer(timer);
        }
    }

    @Override
    public PaymentDto updateStatus(final int paymentId) {
        log.info("Updating payment status for id: {}", paymentId);

        Payment payment = this.paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PAYMENT_NOT_FOUND, paymentId));

        PaymentStatus oldStatus = payment.getPaymentStatus();
        PaymentStatus newStatus = determineNextStatus(oldStatus);
        payment.setPaymentStatus(newStatus);
        
        PaymentDto updatedPayment = PaymentMappingHelper.map(this.paymentRepository.save(payment));
        
        // Registrar métricas de negocio
        businessMetrics.recordPaymentStatusChange(oldStatus, newStatus);
        
        return updatedPayment;
    }

    @Override
    @Transactional
    public void deleteById(final Integer paymentId) {
        log.info("Canceling payment with id: {}", paymentId);

        Payment payment = this.paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PAYMENT_NOT_FOUND, paymentId));

        validateCancellationEligibility(payment);

        PaymentStatus oldStatus = payment.getPaymentStatus();
        payment.setPaymentStatus(PaymentStatus.CANCELED);
        this.paymentRepository.save(payment);
        
        // Registrar métricas de negocio
        businessMetrics.recordPaymentStatusChange(oldStatus, PaymentStatus.CANCELED);
        
        log.info("Payment with id {} has been canceled", paymentId);
    }

    private boolean filterByOrderStatus(PaymentDto paymentDto) {
        try {
            OrderDto orderDto = fetchOrderById(paymentDto.getOrderDto().getOrderId());
            boolean isInPayment = "IN_PAYMENT".equalsIgnoreCase(orderDto.getOrderStatus());
            
            if (isInPayment) {
                paymentDto.setOrderDto(orderDto);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error fetching order for payment ID {}: {}", 
                    paymentDto.getPaymentId(), e.getMessage());
            return false;
        }
    }

    private void enrichWithOrderData(PaymentDto paymentDto) {
        try {
            OrderDto orderDto = fetchOrderById(paymentDto.getOrderDto().getOrderId());
            paymentDto.setOrderDto(orderDto);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(
                    ErrorCode.ORDER_NOT_FOUND, paymentDto.getOrderDto().getOrderId());
        } catch (RestClientException e) {
            log.error("Failed to fetch order for payment {}: {}", 
                    paymentDto.getPaymentId(), e.getMessage());
            throw new ExternalServiceException(
                    "Failed to fetch order information for payment", e);
        }
    }

    private PaymentDto enrichWithOrderDataSafely(PaymentDto paymentDto) {
        try {
            OrderDto orderDto = fetchOrderById(paymentDto.getOrderDto().getOrderId());
            paymentDto.setOrderDto(orderDto);
        } catch (Exception e) {
            log.warn("Could not fetch order {} for payment {}: {}", 
                    paymentDto.getOrderDto().getOrderId(), 
                    paymentDto.getPaymentId(),
                    e.getMessage());
        }
        return paymentDto;
    }

    private void validateOrderId(PaymentDto paymentDto) {
        if (paymentDto.getOrderDto() == null || paymentDto.getOrderDto().getOrderId() == null) {
            throw new InvalidInputException(
                    ErrorCode.MISSING_REQUIRED_FIELD, "Order ID is required");
        }
    }

    private OrderDto verifyOrderEligibility(Integer orderId) {
        try {
            OrderDto orderDto = fetchOrderById(orderId);

            if (orderDto == null) {
                throw new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, orderId);
            }

            if (!OrderStatus.ORDERED.name().equals(orderDto.getOrderStatus())) {
                throw new InvalidInputException(
                        ErrorCode.INVALID_ORDER_STATUS,
                        "Cannot process payment for order with status: " + orderDto.getOrderStatus());
            }

            return orderDto;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, orderId);
        } catch (RestClientException e) {
            log.error("Error communicating with order service for order {}: {}", 
                    orderId, e.getMessage());
            throw new ExternalServiceException(
                    "Error communicating with order service", e);
        }
    }

    private void updateOrderStatus(Integer orderId) {
        String patchUrl = AppConstant.DiscoveredDomainsApi.ORDER_SERVICE_API_URL 
                + "/" + orderId + "/status";
        
        try {
            this.restTemplate.patchForObject(patchUrl, null, Void.class);
            log.info("Order status updated successfully for order ID: {}", orderId);
        } catch (RestClientException e) {
            log.error("Failed to update order status for order ID: {}", orderId, e);
            throw new ExternalServiceException(
                    "Payment saved but failed to update order status", e);
        }
    }

    private void validateCancellationEligibility(Payment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new InvalidPaymentStatusException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
        }

        if (payment.getPaymentStatus() == PaymentStatus.CANCELED) {
            throw new InvalidPaymentStatusException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }
    }

    private PaymentStatus determineNextStatus(PaymentStatus currentStatus) {
        switch (currentStatus) {
            case NOT_STARTED:
                return PaymentStatus.IN_PROGRESS;
            case IN_PROGRESS:
                return PaymentStatus.COMPLETED;
            case COMPLETED:
                throw new InvalidPaymentStatusException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
            case CANCELED:
                throw new InvalidPaymentStatusException(ErrorCode.PAYMENT_ALREADY_CANCELED);
            default:
                throw new InvalidPaymentStatusException(
                        ErrorCode.INVALID_PAYMENT_STATUS,
                        "Unknown payment status: " + currentStatus);
        }
    }

    private OrderDto fetchOrderById(Integer orderId) {
        String url = AppConstant.DiscoveredDomainsApi.ORDER_SERVICE_API_URL + "/" + orderId;
        return this.restTemplate.getForObject(url, OrderDto.class);
    }
}