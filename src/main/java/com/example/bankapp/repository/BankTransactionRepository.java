package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // 1. findAllLatest: All transactions, newest first
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC")
    List<BankTransaction> findAllLatest();

    // 2. findHistory: Transactions where the account is the sender or the receiver (OR condition)
    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    // 3. findByStatusLatest: Filters by status; an enum can be passed as a parameter
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // 4. countByStatus: Number of SUCCESS or FAILED transactions for the dashboard
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    // Additional helper for large-transfers page
    @Query("SELECT t FROM BankTransaction t WHERE t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.amount >= :min ORDER BY t.createdAt DESC")
    List<BankTransaction> findLargeTransfers(@Param("min") BigDecimal min);

    // Total amount sent by an account (SUM of SUCCESS transfers where fromAccount = :account)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.fromAccount = :account AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS")
    BigDecimal getTotalSentByAccount(@Param("account") String account);
}
