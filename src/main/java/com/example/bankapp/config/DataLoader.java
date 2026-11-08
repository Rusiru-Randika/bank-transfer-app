package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inserts three sample customers the first time the application starts (empty
 * database).
 */
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) {
        // Only load data if the DB is empty
        if (customerRepository.count() == 0) {
            // Total money = LKR 85,000.00

            Customer c1 = new Customer();
            c1.setAccountNumber("100001");
            c1.setFullName("Alice Doe");
            c1.setEmail("alice@example.com");
            c1.setBalance(new BigDecimal("25000.00"));

            Customer c2 = new Customer();
            c2.setAccountNumber("100002");
            c2.setFullName("Bob Doe");
            c2.setEmail("bob@example.com");
            c2.setBalance(new BigDecimal("30000.00"));

            Customer c3 = new Customer();
            c3.setAccountNumber("100003");
            c3.setFullName("Charlie Doe");
            c3.setEmail("charlie@example.com");
            c3.setBalance(new BigDecimal("30000.00"));

            // Bank fee revenue account (account 999999) with 0 initial balance
            Customer bankFeeAccount = new Customer();
            bankFeeAccount.setAccountNumber("999999");
            bankFeeAccount.setFullName("Bank Fee Account");
            bankFeeAccount.setEmail("admin@bank.com");
            bankFeeAccount.setBalance(BigDecimal.ZERO);

            customerRepository.saveAll(
                    List.of(c1, c2, c3, bankFeeAccount));
        }
    }
}