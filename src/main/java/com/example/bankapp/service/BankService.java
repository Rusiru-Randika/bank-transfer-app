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
import org.springframework.transaction.annotation.Propagation;
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

    public BigDecimal getTotalSentByAccount(String accountNumber) {
        BigDecimal total = transactionRepository.getTotalSentByAccount(accountNumber);
        return total != null ? total : BigDecimal.ZERO;
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
                "Deposit to account " + accountNumber);
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
                "Withdrawal from account " + accountNumber);
        transactionRepository.save(tx);
    }

    /**
     * Money transfer between two customers with a 1% bank fee (Task 12).
     * All three database updates (debit sender, credit receiver, credit bank fee account 999999)
     * occur in ONE single @Transactional method that rolls back completely on any failure.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        if (fromAccount == null || toAccount == null || fromAccount.equalsIgnoreCase(toAccount)) {
            throw new BankException("Sender and receiver accounts must be different");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Transfer amount must be greater than zero");
        }

        // Verify receiver exists
        customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException(toAccount));

        // Verify bank fee account (999999) exists
        customerRepository.getByAccountNumber(BANK_FEE_ACCOUNT)
                .orElseThrow(() -> new AccountNotFoundException(BANK_FEE_ACCOUNT));

        // 1% fee calculation
        BigDecimal fee = amount.multiply(FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDebit = amount.add(fee);

        // Update 1: Debit sender with amount + 1% fee
        int rowsUpdated = customerRepository.debit(fromAccount, totalDebit);
        if (rowsUpdated == 0) {
            customerRepository.getByAccountNumber(fromAccount)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccount));
            throw new InsufficientBalanceException(fromAccount);
        }

        // Rollback test: simulate failure after withdrawal (before deposits)
        if (simulateFailure) {
            throw new BankException("Simulated system failure occurred after withdrawal: All changes were rolled back!");
        }

        // Update 2: Credit receiver with amount
        customerRepository.credit(toAccount, amount);

        // Update 3: Credit bank fee account 999999 with 1% fee
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            customerRepository.credit(BANK_FEE_ACCOUNT, fee);
        }

        // Save SUCCESS transaction record
        BankTransaction tx = new BankTransaction(
                fromAccount,
                toAccount,
                amount,
                TransactionStatus.SUCCESS,
                "Transfer completed (1% fee: LKR " + fee + " credited to " + BANK_FEE_ACCOUNT + ")"
        );
        return transactionRepository.save(tx);
    }
}
