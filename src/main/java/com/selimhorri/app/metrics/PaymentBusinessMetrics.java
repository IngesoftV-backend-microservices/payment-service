package com.selimhorri.app.metrics;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.selimhorri.app.domain.PaymentStatus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentBusinessMetrics {

    private final MeterRegistry meterRegistry;
    
    // Contadores
    private Counter paymentsTotalCounter;
    private Counter paymentsSuccessfulCounter;
    private Counter paymentsFailedCounter;
    
    // Timer para medir duración de procesamiento
    private Timer paymentProcessingTimer;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
    }

    public void initializeMetrics() {
        // Contador total de pagos
        this.paymentsTotalCounter = Counter.builder("ecommerce.payments.total")
                .description("Total number of payment attempts")
                .tag("service", "payment-service")
                .register(meterRegistry);
        
        // Contador de pagos exitosos
        this.paymentsSuccessfulCounter = Counter.builder("ecommerce.payments.successful.total")
                .description("Total number of successful payments")
                .tag("service", "payment-service")
                .register(meterRegistry);
        
        // Contador de pagos fallidos
        this.paymentsFailedCounter = Counter.builder("ecommerce.payments.failed.total")
                .description("Total number of failed payments")
                .tag("service", "payment-service")
                .register(meterRegistry);
        
        // Timer para duración de procesamiento
        this.paymentProcessingTimer = Timer.builder("ecommerce.payments.processing.duration.seconds")
                .description("Time taken to process payments")
                .tag("service", "payment-service")
                .register(meterRegistry);
        
        log.info("Payment business metrics initialized");
    }

    /**
     * Registra un intento de pago
     */
    public void recordPaymentAttempt(PaymentStatus status) {
        // Incrementar contador total directamente usando el builder
        // Micrometer devuelve la misma instancia si ya existe
        Counter.builder("ecommerce.payments.total")
                .description("Total number of payment attempts")
                .tag("service", "payment-service")
                .register(meterRegistry)
                .increment();
        
        // Registrar por estado
        Counter.builder("ecommerce.payments.by.status")
                .description("Payments by status")
                .tag("status", status.name())
                .tag("service", "payment-service")
                .register(meterRegistry)
                .increment();
        
        log.info("Recorded payment attempt: status={}", status);
    }

    /**
     * Registra un pago exitoso
     */
    public void recordPaymentSuccess() {
        if (paymentsSuccessfulCounter == null) {
            initializeMetrics();
        }
        
        paymentsSuccessfulCounter.increment();
        log.debug("Recorded successful payment");
    }

    /**
     * Registra un pago fallido
     */
    public void recordPaymentFailure() {
        if (paymentsFailedCounter == null) {
            initializeMetrics();
        }
        
        paymentsFailedCounter.increment();
        log.debug("Recorded failed payment");
    }

    /**
     * Registra un cambio de estado de pago
     */
    public void recordPaymentStatusChange(PaymentStatus oldStatus, PaymentStatus newStatus) {
        // Decrementar el estado anterior
        Counter.builder("ecommerce.payments.by.status")
                .tag("status", oldStatus.name())
                .tag("service", "payment-service")
                .register(meterRegistry)
                .increment(-1);
        
        // Incrementar el nuevo estado
        Counter.builder("ecommerce.payments.by.status")
                .tag("status", newStatus.name())
                .tag("service", "payment-service")
                .register(meterRegistry)
                .increment();
        
        // Si el nuevo estado es COMPLETED y el anterior NO era COMPLETED, registrar como exitoso
        // (evita duplicar el conteo si ya estaba en COMPLETED)
        if (newStatus == PaymentStatus.COMPLETED && oldStatus != PaymentStatus.COMPLETED) {
            recordPaymentSuccess();
        }
        
        // Si el nuevo estado es CANCELED y el anterior NO era CANCELED, registrar como fallido
        // (evita duplicar el conteo si ya estaba en CANCELED)
        if (newStatus == PaymentStatus.CANCELED && oldStatus != PaymentStatus.CANCELED) {
            recordPaymentFailure();
        }
        
        log.debug("Recorded payment status change: {} -> {}", oldStatus, newStatus);
    }

    /**
     * Registra el tiempo de procesamiento de un pago
     */
    public Timer.Sample startPaymentProcessingTimer() {
        if (paymentProcessingTimer == null) {
            initializeMetrics();
        }
        return Timer.start(meterRegistry);
    }

    /**
     * Detiene el timer y registra la duración
     */
    public void stopPaymentProcessingTimer(Timer.Sample sample) {
        if (sample != null && paymentProcessingTimer != null) {
            sample.stop(paymentProcessingTimer);
        }
    }
}

