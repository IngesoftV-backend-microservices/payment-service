package com.selimhorri.app.resource;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.dto.response.collection.DtoCollectionResponse;
import com.selimhorri.app.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentResource {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<DtoCollectionResponse<PaymentDto>> findAll() {
        log.info("Fetching all payments");
        return ResponseEntity.ok(
                new DtoCollectionResponse<>(this.paymentService.findAll()));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDto> findById(
            @PathVariable("paymentId") 
            @NotBlank(message = "Payment ID must not be blank") 
            @Valid final String paymentId) {
        
        log.info("Fetching payment with id: {}", paymentId);
        return ResponseEntity.ok(
                this.paymentService.findById(parsePaymentId(paymentId)));
    }

    @PostMapping
    public ResponseEntity<PaymentDto> save(
            @RequestBody 
            @NotNull(message = "Payment data must not be null") 
            @Valid final PaymentDto paymentDto) {
        
        log.info("Creating new payment for order: {}", 
                paymentDto.getOrderDto() != null ? paymentDto.getOrderDto().getOrderId() : "null");
        
        PaymentDto savedPayment = this.paymentService.save(paymentDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPayment);
    }

    @PatchMapping("/{paymentId}")
    public ResponseEntity<PaymentDto> updateStatus(
            @PathVariable("paymentId") 
            @NotBlank(message = "Payment ID must not be blank") 
            @Valid final String paymentId) {
        
        log.info("Updating payment status for id: {}", paymentId);
        return ResponseEntity.ok(
                this.paymentService.updateStatus(parsePaymentId(paymentId)));
    }

    @PutMapping("/{paymentId}")
    public ResponseEntity<PaymentDto> updateStatusPut(
            @PathVariable("paymentId") 
            @NotBlank(message = "Payment ID must not be blank") 
            @Valid final String paymentId) {
        
        log.info("Updating payment status (PUT) for id: {}", paymentId);
        return ResponseEntity.ok(
                this.paymentService.updateStatus(parsePaymentId(paymentId)));
    }

    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> deleteById(
            @PathVariable("paymentId") 
            @NotBlank(message = "Payment ID must not be blank") 
            @Valid final String paymentId) {
        
        log.info("Canceling payment with id: {}", paymentId);
        this.paymentService.deleteById(parsePaymentId(paymentId));
        return ResponseEntity.noContent().build();
    }

    private Integer parsePaymentId(String paymentId) {
        try {
            return Integer.parseInt(paymentId);
        } catch (NumberFormatException e) {
            log.warn("Invalid payment ID format: {}", paymentId);
            throw e;
        }
    }
}