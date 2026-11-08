package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    @GetMapping("/")
    public String dashboard(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
        populateDashboardModel(model, keyword);
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }
        return "bank";
    }

    private void populateDashboardModel(Model model, String keyword) {
        List<Customer> allCustomers = bankService.getAllCustomersOrdered();
        List<Customer> filteredCustomers = (keyword != null && !keyword.isBlank())
                ? bankService.searchCustomers(keyword)
                : allCustomers;

        model.addAttribute("allCustomers", allCustomers);
        model.addAttribute("customers", filteredCustomers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalCustomers", bankService.getCustomerCount());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.getSuccessTransactionCount());
        model.addAttribute("failedCount", bankService.getFailedTransactionCount());
        model.addAttribute("transactions", bankService.getAllTransactions());
    }

    @GetMapping("/transfer")
    public String transferGet() {
        return "redirect:/";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateDashboardModel(model, null);
            if (!model.containsAttribute("customer")) {
                model.addAttribute("customer", new Customer());
            }
            return "bank";
        }

        try {
            bankService.transfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.isSimulateFailure()
            );
            redirectAttributes.addFlashAttribute("success",
                    "Transfer of LKR " + request.getAmount() + " from " + request.getFromAccount() +
                            " to " + request.getToAccount() + " was successful!");
        } catch (Exception e) {
            // Because transfer() rolled back, record the failure using the audit service (REQUIRES_NEW)
            auditService.recordFailedTransaction(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO,
                    e.getMessage()
            );
            redirectAttributes.addFlashAttribute("error", "Transfer failed: " + e.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateDashboardModel(model, null);
            if (!model.containsAttribute("transferRequest")) {
                model.addAttribute("transferRequest", new TransferRequest());
            }
            return "bank";
        }

        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success",
                    "Customer " + customer.getFullName() + " created successfully!");
        } catch (BankException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/";
    }

    @GetMapping("/accounts/{no}/history")
    public String accountHistory(@PathVariable("no") String accountNumber, Model model) {
        Customer customer = bankService.getCustomerByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankException("Account " + accountNumber + " not found"));

        List<BankTransaction> history = bankService.getAccountHistory(accountNumber);
        BigDecimal totalSent = bankService.getTotalSentByAccount(accountNumber);

        model.addAttribute("customer", customer);
        model.addAttribute("transactions", history);
        model.addAttribute("totalSent", totalSent);
        return "history";
    }

    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(value = "min", defaultValue = "10000") BigDecimal min, Model model) {
        List<BankTransaction> transfers = bankService.getLargeTransfers(min);
        model.addAttribute("min", min);
        model.addAttribute("transfers", transfers);
        return "large-transfers";
    }

    @GetMapping("/deposit")
    public String depositForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomersOrdered());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam("accountNumber") String accountNumber,
                          @RequestParam("amount") BigDecimal amount,
                          RedirectAttributes redirectAttributes) {
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success",
                    "Successfully deposited LKR " + amount + " to account " + accountNumber);
        } catch (BankException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/deposit";
    }

    @GetMapping("/withdraw")
    public String withdrawForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomersOrdered());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam("accountNumber") String accountNumber,
                           @RequestParam("amount") BigDecimal amount,
                           RedirectAttributes redirectAttributes) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success",
                    "Successfully withdrew LKR " + amount + " from account " + accountNumber);
        } catch (BankException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/withdraw";
    }
}
