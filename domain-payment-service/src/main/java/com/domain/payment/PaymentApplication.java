package com.domain.payment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
/** Starts the independently stored payment bounded context. */
@SpringBootApplication @EnableScheduling
public class PaymentApplication {
    /** Boots the payment HTTP server and durable delivery worker. */
    public static void main(String[] args){SpringApplication.run(PaymentApplication.class,args);}
}
