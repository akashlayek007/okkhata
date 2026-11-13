package com.example.simplekhata;

import com.example.simplekhata.base.FirestoreEmulatorTestBase;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.TransactionType;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KhataRepositoryRuleTest extends FirestoreEmulatorTestBase {

    private static final String ALICE_EMAIL = "alice_khata@test.com";
    private static final String BOB_EMAIL = "bob_khata@test.com";
    private static final long TIMEOUT_SECONDS = 8L;

    @Test
    public void owner_canCreateUpdateAndDeleteCustomerAndTransactions() throws Exception {
        String aliceUid = signInTestUser(ALICE_EMAIL);
        KhataRepository repo = new KhataRepository(firestore, auth, null);

        DocumentReference custRef = Tasks.await(
                repo.createCustomerTask("Suresh Verma", "9876543210"),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );
        assertNotNull(custRef);
        String customerId = custRef.getId();
        assertFalse(customerId.isEmpty());

        Tasks.await(
                repo.updateCustomerTask(customerId, "Suresh V", ""),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );

        DocumentSnapshot custSnap = Tasks.await(custRef.get(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(custSnap.exists());
        assertEquals("Suresh V", custSnap.getString("name"));
        assertFalse(custSnap.contains("phone"));

        DocumentReference txRef = Tasks.await(
                repo.createTransactionTask(
                        customerId,
                        TransactionType.CREDIT,
                        50000L,
                        "Rice bag",
                        new Timestamp(new Date())
                ),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );
        String txId = txRef.getId();

        Tasks.await(
                repo.updateTransactionTask(
                        txId,
                        TransactionType.PAYMENT,
                        25000L,
                        "Partial cash",
                        new Timestamp(new Date())
                ),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );

        DocumentSnapshot txSnap = Tasks.await(txRef.get(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(txSnap.exists());
        assertEquals(25000L, txSnap.getLong("amount").longValue());
        assertEquals("PAYMENT", txSnap.getString("type"));

        Tasks.await(
                repo.deleteCustomerAndTransactionsTask(customerId),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );

        DocumentSnapshot deletedCust = Tasks.await(
                firestore.collection("users").document(aliceUid)
                        .collection("customers").document(customerId).get(),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );
        assertFalse(deletedCust.exists());

        DocumentSnapshot deletedTx = Tasks.await(
                firestore.collection("users").document(aliceUid)
                        .collection("transactions").document(txId).get(),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );
        assertFalse(deletedTx.exists());
    }

    @Test
    public void crossUserAccess_failsWithPermissionDenied() throws Exception {
        String aliceUid = signInTestUser(ALICE_EMAIL);
        KhataRepository aliceRepo = new KhataRepository(firestore, auth, null);
        DocumentReference aliceCust = Tasks.await(
                aliceRepo.createCustomerTask("Alice Client", ""),
                TIMEOUT_SECONDS, TimeUnit.SECONDS
        );

        signInTestUser(BOB_EMAIL);
        try {
            Tasks.await(
                    firestore.collection("users").document(aliceUid)
                            .collection("customers").document(aliceCust.getId()).get(),
                    TIMEOUT_SECONDS, TimeUnit.SECONDS
            );
            fail("Expected PERMISSION_DENIED when Bob reads Alice's customer");
        } catch (ExecutionException e) {
            FirebaseFirestoreException ffe = unwrapFirestoreException(e);
            assertNotNull("Expected FirebaseFirestoreException, got " + e, ffe);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.getCode());
        }
    }

    @Test
    public void unauthenticatedCaller_failsWithPermissionDenied() throws Exception {
        auth.signOut();
        try {
            Tasks.await(
                    firestore.collection("users").document("some_uid").collection("customers").get(),
                    TIMEOUT_SECONDS, TimeUnit.SECONDS
            );
            fail("Expected PERMISSION_DENIED when unauthenticated user lists customers");
        } catch (ExecutionException e) {
            FirebaseFirestoreException ffe = unwrapFirestoreException(e);
            assertNotNull("Expected FirebaseFirestoreException, got " + e, ffe);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.getCode());
        }
    }
}
