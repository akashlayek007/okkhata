package com.example.simplekhata;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.DashboardSummary;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.storage.LocalGuestStore;
import com.example.simplekhata.utils.LedgerCalculator;
import com.example.simplekhata.utils.SessionModeUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {35})
public class GuestModeLocalStoreTest {

    private Context context;
    private LocalGuestStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SessionModeUtils.setGuestMode(context, false);
        store = new LocalGuestStore(context);
        store.clearAll();
    }

    @After
    public void tearDown() {
        store.clearAll();
        store.close();
        SessionModeUtils.setGuestMode(context, false);
    }

    @Test
    public void sessionModeUtils_persistsGuestFlag() {
        assertFalse(SessionModeUtils.isGuestMode(context));
        SessionModeUtils.setGuestMode(context, true);
        assertTrue(SessionModeUtils.isGuestMode(context));
        SessionModeUtils.setGuestMode(context, false);
        assertFalse(SessionModeUtils.isGuestMode(context));
    }

    @Test
    public void localGuestStore_crudAndAtomicCascadeDelete() {
        assertFalse(store.hasData());

        String custId = store.createCustomer("Mohan Lal", "9876543210");
        assertTrue(store.hasData());

        store.updateCustomer(custId, "Mohan Lal Ji", "");
        List<Customer> customers = store.getAllCustomers();
        assertEquals(1, customers.size());
        assertEquals("Mohan Lal Ji", customers.get(0).getName());
        assertEquals("", customers.get(0).getPhone());

        long now = System.currentTimeMillis();
        String tx1 = store.createTransaction(custId, TransactionType.CREDIT, 50000L, "Flour", now);
        String tx2 = store.createTransaction(custId, TransactionType.PAYMENT, 20000L, "Cash", now + 1000L);
        store.updateTransaction(tx2, TransactionType.PAYMENT, 15000L, "UPI", now + 2000L);

        List<LedgerTransaction> txs = store.getAllTransactions();
        assertEquals(2, txs.size());

        List<CustomerSummary> summaries = LedgerCalculator.buildCustomerSummaries(customers, txs);
        assertEquals(1, summaries.size());
        assertEquals(35000L, summaries.get(0).getBalancePaise());

        DashboardSummary dashboard = LedgerCalculator.buildDashboardSummary(customers, txs);
        assertEquals(35000L, dashboard.getTotalReceivablePaise());
        assertEquals(15000L, dashboard.getTotalReceivedPaise());
        assertEquals(35000L, dashboard.getNetBalancePaise());
        assertEquals(1, dashboard.getTotalCustomers());

        store.deleteTransaction(tx1);
        assertEquals(1, store.getAllTransactions().size());

        store.deleteCustomerAndTransactions(custId);
        assertFalse(store.hasData());
        assertEquals(0, store.getAllCustomers().size());
        assertEquals(0, store.getAllTransactions().size());
    }
}
