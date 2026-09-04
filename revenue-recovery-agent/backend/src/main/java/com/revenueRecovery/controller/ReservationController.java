package com.revenueRecovery.controller;
import com.revenueRecovery.model.PaymentReservation;
import com.revenueRecovery.service.ReservationService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/transactions") @CrossOrigin(origins="*")
public class ReservationController{
    private final ReservationService service;public ReservationController(ReservationService service){this.service=service;}
    @PostMapping("/{eventId}/reservation") public PaymentReservation create(@PathVariable String eventId){return service.create(eventId);}
    @PostMapping("/{eventId}/reservation/reconnect") public PaymentReservation reconnect(@PathVariable String eventId){return service.reconnect(eventId);}
    @PostMapping("/{eventId}/reservation/expire") public PaymentReservation expire(@PathVariable String eventId){return service.expire(eventId);}
    @PostMapping("/{eventId}/reservation/escalate") public PaymentReservation escalate(@PathVariable String eventId){return service.escalateFallback(eventId);}
}
