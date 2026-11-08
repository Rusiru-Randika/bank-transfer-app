package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // default for every method: read-only transaction
public class BankService {

    private static final String BANK_FEE_ACCOUNT = "999999";
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.01"); // 1%

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public List<Customer> getAllCustomersOrdered() {
        return customerRepository.findAllOrdered();
    }

    public List<Customer> searchCustomers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAllOrdered();
        }
        return customerRepository.searchByName(keyword.trim());
    }

    public Optional<Customer> getCustomerByAccountNumber(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber);
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    public long getCustomerCount() {
        return customerRepository.countCustomersNative();
    }

    public List<BankTransaction> getAllTransactions() {
        return transactionRepository.findAllLatest();
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal min) {
        return transactionRepository.findLargeTransfers(min);
    }

    public long getSuccessTransactionCount() {
        return transactionRepository.countByStatus(TransactionStatus.SUCCESS);
    }

    public long getFailedTransactionCount() {
        return transactionRepository.countByStatus(TransactionStatus.FAILED);
    }

    @Transactional
    public void createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        }
        if (customer.getEmail() != null && !customer.getEmail().isBlank()
                && customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email " + customer.getEmail() + " is already in use");
        }
        customerRepository.save(customer);
    }

    @Transactional
    public void deposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Deposit amount must be greater than zero");
        }
        customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        customerRepository.credit(accountNumber, amount);

        BankTransaction tx = new BankTransaction(
                accountNumber,
                accountNumber,
                amount,
                TransactionStatus.SUCCESS,
                "Deposit to account " + accountNumber
        );
        transactionRepository.save(tx);
    }

    @Transactional
    public void withdraw(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Withdrawal amount must be greater than zero");
        }
        customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        int rows = customerRepository.debit(accountNumber, amount);
        if (rows == 0) {
            throw new InsufficientBalanceException(accountNumber);
        }

        BankTransaction tx = new BankTransaction(
                accountNumber,
                accountNumber,
                amount,
                TransactionStatus.SUCCESS,
                "Withdrawal from account " + accountNumber
        );
        transactionRepository.save(tx);
    }

    /**
     * Money transfer between two customers.
     * Rolls back on any exception (unchecked or checked).
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        if (fromAccount == null || toAccount == null || fromAccount.equalsIgnoreCase(toAccount)) {
            throw new BankException("Cannot transfer to the same account");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Transfer amount must be greater than zero");
        }

        // Verify receiver exists
        customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException(toAccount));

        // Calculate 1% fee
        BigDecimal fee = amount.multiply(FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDebit = amount.add(fee);

        // Atomic debit with balance check
        int rowsUpdated = customerRepository.debit(fromAccount, totalDebit);
        if (rowsUpdated == 0) {
            // Check if account exists or just had insufficient balance
            customerRepository.getByAccountNumber(fromAccount)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccount));
            throw new InsufficientBalanceException(fromAccount);
        }

        // Credit receiver
        customerRepository.credit(toAccount, amount);

        // Credit fee to bank account (999999) if fee exists
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            customerRepository.credit(BANK_FEE_ACCOUNT, fee);
        }

        // Check if rollback test was triggered
        if (simulateFailure) {
            throw new BankException("Simulated system failure occurred after debit - transaction rolled back!");
        }

        // Save SUCCESS transaction record
        BankTransaction tx = new BankTransaction(
                fromAccount,
                toAccount,
                amount,
                TransactionStatus.SUCCESS,
                "Transfer completed (fee: LKR " + fee + ")"
        );
        transactionRepository.save(tx);
    }
}
