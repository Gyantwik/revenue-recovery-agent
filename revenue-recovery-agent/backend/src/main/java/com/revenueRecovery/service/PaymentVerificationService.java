package com.revenueRecovery.service;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.model.enums.Outcome;
import org.springframework.stereotype.Service;
@Service
public class PaymentVerificationService {
    private final RazorpayTestCheckoutAttemptRepository attempts;
    private final AuditRecordRepository records;
    public PaymentVerificationService(RazorpayTestCheckoutAttemptRepository attempts, AuditRecordRepository records){this.attempts=attempts;this.records=records;}
    public boolean hasVerifiedPayment(String orderId){
        return attempts.findFirstByRazorpayOrderIdAndStatus(orderId, RazorpaySignatureVerificationService.VERIFIED).isPresent()
                || records.findByGatewayOrderId(orderId).map(record -> record.getOutcome()== Outcome.RECOVERED).orElse(false);
    }
}
