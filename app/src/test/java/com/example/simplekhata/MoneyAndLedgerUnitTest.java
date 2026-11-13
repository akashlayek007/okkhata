package com.example.simplekhata;

import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.DashboardSummary;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.LedgerCalculator;
import com.example.simplekhata.utils.MoneyUtils;
import com.example.simplekhata.utils.ValidationUtils;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MoneyAndLedgerUnitTest {

    @Test
    public void parseToPaise_validAndInvalidInputs() {
        assertEquals(100000L, MoneyUtils.parseToPaise("1000"));
        assertEquals(125050L, MoneyUtils.parseToPaise("1,250.5"));
        assertEquals(10005L, MoneyUtils.parseToPaise("100.05"));
        assertEquals(-1L, MoneyUtils.parseToPaise("0"));
        assertEquals(-1L, MoneyUtils.parseToPaise("0.00"));
        assertEquals(-1L, MoneyUtils.parseToPaise("-50"));
        assertEquals(-1L, MoneyUtils.parseToPaise("12.345"));
        assertEquals(-1L, MoneyUtils.parseToPaise("abc"));
        assertEquals(-1L, MoneyUtils.parseToPaise(""));
    }

    @Test
    public void formatRupees_indianGroupingAndDecimals() {
        assertEquals("₹600", MoneyUtils.formatRupees(60000L));
        assertEquals("₹1,25,000", MoneyUtils.formatRupees(12500000L));
        assertEquals("₹1,250.50", MoneyUtils.formatRupees(125050L));
        assertEquals("-₹200", MoneyUtils.formatRupees(-20000L));
    }

    @Test
    public void ledgerCalculator_accurateBalancesAndDashboardTotals() {
        Timestamp t1 = new Timestamp(new Date(1000000L));
        Timestamp t2 = new Timestamp(new Date(2000000L));
        Timestamp t3 = new Timestamp(new Date(3000000L));

        Customer c1 = new Customer("c1", "Asha", "9876543210", t1, t1);
        Customer c2 = new Customer("c2", "Vikram", "", t1, t1);

        // c1: Credit 1000, Payment 400 -> Balance 600
        // c2: Credit 500, Payment 700 -> Balance -200 (Advance)
        List<LedgerTransaction> txs = Arrays.asList(
                new LedgerTransaction("tx1", "c1", TransactionType.CREDIT.getFirestoreValue(), 100000L, "Groceries", t1, t1, t1),
                new LedgerTransaction("tx2", "c1", TransactionType.PAYMENT.getFirestoreValue(), 40000L, "Cash", t2, t2, t2),
                new LedgerTransaction("tx3", "c2", TransactionType.CREDIT.getFirestoreValue(), 50000L, "", t1, t1, t1),
                new LedgerTransaction("tx4", "c2", TransactionType.PAYMENT.getFirestoreValue(), 70000L, "UPI", t3, t3, t3)
        );

        List<CustomerSummary> summaries = LedgerCalculator.buildCustomerSummaries(Arrays.asList(c1, c2), txs);
        assertEquals(2, summaries.size());

        // c2 has latest activity t3, so c2 is first
        assertEquals("c2", summaries.get(0).getCustomer().getId());
        assertEquals(-20000L, summaries.get(0).getBalancePaise());

        assertEquals("c1", summaries.get(1).getCustomer().getId());
        assertEquals(60000L, summaries.get(1).getBalancePaise());
        assertEquals(100000L, summaries.get(1).getTotalCreditPaise());
        assertEquals(40000L, summaries.get(1).getTotalPaymentPaise());

        DashboardSummary dash = LedgerCalculator.buildDashboardSummary(Arrays.asList(c1, c2), txs);
        // Total Receivable sums positive customer balances (600)
        assertEquals(60000L, dash.getTotalReceivablePaise());
        // Total Received sums all payments (400 + 700 = 1100)
        assertEquals(110000L, dash.getTotalReceivedPaise());
        // Net Balance sums all customer balances (+600 - 200 = +400)
        assertEquals(40000L, dash.getNetBalancePaise());
        assertEquals(2, dash.getTotalCustomers());
        assertEquals(4, dash.getRecentActivities().size());
        assertEquals("tx4", dash.getRecentActivities().get(0).getTransaction().getId());
    }

    @Test
    public void validationUtils_customerAndPhone() {
        assertTrue(ValidationUtils.isValidCustomerName("Ramesh"));
        assertFalse(ValidationUtils.isValidCustomerName("   "));
        assertTrue(ValidationUtils.isValidOptionalPhone(""));
        assertTrue(ValidationUtils.isValidOptionalPhone("+91 98765-43210"));
        assertFalse(ValidationUtils.isValidOptionalPhone("123"));
        assertFalse(ValidationUtils.isValidOptionalPhone("abc12345"));

        assertEquals("+919876543210", ValidationUtils.normalizePickedPhone("+91 98765-43210"));
        assertEquals("5551234567", ValidationUtils.normalizePickedPhone("(555) 123-4567"));
        assertEquals("", ValidationUtils.normalizePickedPhone("   "));
        assertTrue(ValidationUtils.isValidOptionalPhone(
                ValidationUtils.normalizePickedPhone("+91\u00A098765 43210")
        ));
    }
}
