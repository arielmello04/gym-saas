// src/main/java/com/gymsystem/payments/PaymentController.java
package com.gymsystem.payments;

import com.gymsystem.payments.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** User endpoints for subscriptions and invoices. */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Subscribe, view and cancel your subscription; list invoices")
public class PaymentController {

    private final SubscriptionService service;

    @Operation(summary = "Subscribe to a plan")
    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        return ResponseEntity.ok(service.subscribe(request));
    }

    @Operation(summary = "Get my current subscription")
    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> mySubscription() {
        return ResponseEntity.ok(service.getMySubscription());
    }

    @Operation(summary = "List my payment invoices")
    @GetMapping("/invoices")
    public ResponseEntity<List<PaymentItem>> myInvoices() {
        return ResponseEntity.ok(service.listMyInvoices());
    }

    @Operation(summary = "Cancel my subscription")
    @DeleteMapping("/subscription")
    public ResponseEntity<Void> cancel() {
        service.cancelMySubscription();
        return ResponseEntity.noContent().build();
    }
}
