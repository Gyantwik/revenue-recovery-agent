package com.revenueRecovery.repository;
import com.revenueRecovery.model.PaymentReservation;
import com.revenueRecovery.model.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface PaymentReservationRepository extends JpaRepository<PaymentReservation,Long>{
    Optional<PaymentReservation> findFirstByOrderIdAndStatus(String orderId, ReservationStatus status);
    Optional<PaymentReservation> findFirstByEventIdOrderByIdDesc(String eventId);
    List<PaymentReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);
}
