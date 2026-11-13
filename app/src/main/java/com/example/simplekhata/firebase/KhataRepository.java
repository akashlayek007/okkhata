package com.example.simplekhata.firebase;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.R;
import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.DashboardSummary;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.SyncStatus;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.storage.LocalGuestStore;
import com.example.simplekhata.utils.LedgerCalculator;
import com.example.simplekhata.utils.SessionModeUtils;
import com.example.simplekhata.utils.ValidationUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KhataRepository {
    private static volatile KhataRepository instance;

    public interface WriteCallback {
        void onSuccess(String documentId);
        void onError(String message);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    @Nullable
    private final Context appContext;
    @Nullable
    private final LocalGuestStore localGuestStore;
    private final ConnectivityManager connectivityManager;

    private final MutableLiveData<List<CustomerSummary>> customerSummariesLiveData =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DashboardSummary> dashboardSummaryLiveData =
            new MutableLiveData<>(new DashboardSummary(0L, 0L, 0, Collections.emptyList()));
    private final MutableLiveData<List<LedgerTransaction>> allTransactionsLiveData =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<SyncStatus> syncStatusLiveData =
            new MutableLiveData<>(new SyncStatus(SyncStatus.State.SYNCING, null, 0L));

    private final List<Customer> currentCustomers = new ArrayList<>();
    private final List<LedgerTransaction> currentTransactions = new ArrayList<>();

    private ListenerRegistration customersRegistration;
    private ListenerRegistration transactionsRegistration;
    private String activeUid;
    private boolean customersPendingWrites = false;
    private boolean transactionsPendingWrites = false;
    private boolean isOnline = true;
    private boolean migratingGuestData = false;
    private String lastSyncError = null;
    private long lastSyncedAtMillis = 0L;

    private final FirebaseAuth.AuthStateListener authStateListener = new FirebaseAuth.AuthStateListener() {
        @Override
        public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
            refreshSessionState();
        }
    };

    public static KhataRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (KhataRepository.class) {
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    String databaseId = appContext.getString(R.string.firestore_database_id);
                    FirebaseFirestore firestore = FirebaseFirestore.getInstance(databaseId);
                    FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
                    instance = new KhataRepository(firestore, firebaseAuth, appContext);
                }
            }
        }
        return instance;
    }

    public KhataRepository(
            @NonNull FirebaseFirestore db,
            @NonNull FirebaseAuth auth,
            @Nullable Context context
    ) {
        this.db = db;
        this.auth = auth;
        if (context != null) {
            this.appContext = context.getApplicationContext();
            this.localGuestStore = new LocalGuestStore(this.appContext);
            this.connectivityManager = (ConnectivityManager) this.appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            registerNetworkCallback();
            this.isOnline = checkCurrentConnectivity();
        } else {
            this.appContext = null;
            this.localGuestStore = null;
            this.connectivityManager = null;
            this.isOnline = true;
        }
    }

    public boolean isGuestMode() {
        return auth.getCurrentUser() == null
                && appContext != null
                && SessionModeUtils.isGuestMode(appContext);
    }

    public void startObservingAuth() {
        auth.removeAuthStateListener(authStateListener);
        auth.addAuthStateListener(authStateListener);
        refreshSessionState();
    }

    public void refreshSessionState() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            if (appContext != null) {
                SessionModeUtils.setGuestMode(appContext, false);
            }
            migrateGuestDataIfNeeded(user.getUid());
            startListenersIfNeeded(user.getUid());
        } else if (isGuestMode()) {
            stopListeners();
            loadGuestData();
        } else {
            stopListeners();
            clearCachedData();
        }
    }

    private synchronized void loadGuestData() {
        if (localGuestStore == null) {
            return;
        }
        currentCustomers.clear();
        currentCustomers.addAll(localGuestStore.getAllCustomers());
        currentTransactions.clear();
        currentTransactions.addAll(localGuestStore.getAllTransactions());
        customersPendingWrites = false;
        transactionsPendingWrites = false;
        lastSyncError = null;
        recomputeAllSummaries();
        updateSyncStatus();
    }

    private synchronized void migrateGuestDataIfNeeded(@NonNull String uid) {
        if (migratingGuestData || localGuestStore == null || !localGuestStore.hasData()) {
            return;
        }
        List<Customer> guestCustomers = localGuestStore.getAllCustomers();
        List<LedgerTransaction> guestTransactions = localGuestStore.getAllTransactions();
        if (guestCustomers.isEmpty() && guestTransactions.isEmpty()) {
            localGuestStore.clearAll();
            return;
        }

        migratingGuestData = true;
        Map<String, String> customerIdMap = new HashMap<>();
        List<Task<Void>> customerBatchTasks = new ArrayList<>();
        int batchLimit = 450;

        for (int i = 0; i < guestCustomers.size(); i += batchLimit) {
            WriteBatch batch = db.batch();
            int end = Math.min(i + batchLimit, guestCustomers.size());
            for (int j = i; j < end; j++) {
                Customer c = guestCustomers.get(j);
                DocumentReference newRef = customersCollection(uid).document();
                customerIdMap.put(c.getId(), newRef.getId());

                Map<String, Object> payload = new HashMap<>();
                String trimmedName = ValidationUtils.normalizeOptionalText(c.getName());
                if (trimmedName.isEmpty()) {
                    trimmedName = "Customer";
                }
                payload.put("name", trimmedName);
                String trimmedPhone = ValidationUtils.normalizeOptionalText(c.getPhone());
                if (!trimmedPhone.isEmpty()) {
                    payload.put("phone", trimmedPhone);
                }
                payload.put("createdAt", FieldValue.serverTimestamp());
                payload.put("updatedAt", FieldValue.serverTimestamp());
                batch.set(newRef, payload);
            }
            customerBatchTasks.add(batch.commit());
        }

        Tasks.whenAll(customerBatchTasks)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new RuntimeException("Failed to migrate guest customers"));
                    }
                    List<Task<Void>> txBatchTasks = new ArrayList<>();
                    List<LedgerTransaction> validTxs = new ArrayList<>();
                    for (LedgerTransaction tx : guestTransactions) {
                        if (customerIdMap.containsKey(tx.getCustomerId()) && tx.getAmount() > 0L) {
                            validTxs.add(tx);
                        }
                    }
                    for (int i = 0; i < validTxs.size(); i += batchLimit) {
                        WriteBatch batch = db.batch();
                        int end = Math.min(i + batchLimit, validTxs.size());
                        for (int j = i; j < end; j++) {
                            LedgerTransaction tx = validTxs.get(j);
                            String mappedCustomerId = customerIdMap.get(tx.getCustomerId());
                            if (mappedCustomerId == null) {
                                continue;
                            }
                            DocumentReference newTxRef = transactionsCollection(uid).document();
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("customerId", mappedCustomerId);
                            payload.put("type", tx.getTransactionType().getFirestoreValue());
                            payload.put("amount", tx.getAmount());
                            String trimmedNote = ValidationUtils.normalizeOptionalText(tx.getNote());
                            if (!trimmedNote.isEmpty()) {
                                payload.put("note", trimmedNote);
                            }
                            Timestamp txDate = tx.getTransactionDate() != null
                                    ? tx.getTransactionDate()
                                    : Timestamp.now();
                            payload.put("transactionDate", txDate);
                            payload.put("createdAt", FieldValue.serverTimestamp());
                            payload.put("updatedAt", FieldValue.serverTimestamp());
                            batch.set(newTxRef, payload);
                        }
                        txBatchTasks.add(batch.commit());
                    }
                    return Tasks.whenAll(txBatchTasks);
                })
                .addOnSuccessListener(unused -> {
                    synchronized (KhataRepository.this) {
                        localGuestStore.clearAll();
                        migratingGuestData = false;
                        lastSyncError = null;
                        lastSyncedAtMillis = System.currentTimeMillis();
                        updateSyncStatus();
                    }
                })
                .addOnFailureListener(e -> {
                    synchronized (KhataRepository.this) {
                        migratingGuestData = false;
                        lastSyncError = e.getLocalizedMessage();
                        updateSyncStatus();
                    }
                });
    }

    public void retrySync() {
        if (isGuestMode()) {
            loadGuestData();
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }
        lastSyncError = null;
        isOnline = checkCurrentConnectivity();
        updateSyncStatus();
        stopListeners();
        startListenersIfNeeded(user.getUid());
        db.waitForPendingWrites()
                .addOnSuccessListener(unused -> {
                    lastSyncError = null;
                    lastSyncedAtMillis = System.currentTimeMillis();
                    updateSyncStatus();
                })
                .addOnFailureListener(e -> {
                    lastSyncError = e.getLocalizedMessage();
                    updateSyncStatus();
                });
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null) {
            return;
        }
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    isOnline = true;
                    updateSyncStatus();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    isOnline = checkCurrentConnectivity();
                    updateSyncStatus();
                }
            });
        } catch (Exception ignored) {
        }
    }

    private boolean checkCurrentConnectivity() {
        if (connectivityManager == null) {
            return true;
        }
        try {
            Network active = connectivityManager.getActiveNetwork();
            if (active == null) {
                return false;
            }
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private synchronized void startListenersIfNeeded(String uid) {
        if (uid.equals(activeUid) && customersRegistration != null && transactionsRegistration != null) {
            return;
        }
        stopListeners();
        activeUid = uid;
        lastSyncError = null;
        updateSyncStatus();

        final String customersPath = "users/" + uid + "/customers";
        customersRegistration = customersCollection(uid).addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    if (auth.getCurrentUser() != null && uid.equals(auth.getCurrentUser().getUid())) {
                        FirestoreErrorHandler.handleFirestoreError(
                                error, FirestoreErrorHandler.OperationType.LIST, customersPath, auth);
                        lastSyncError = error.getLocalizedMessage();
                        updateSyncStatus();
                    }
                    return;
                }
                if (snapshots != null) {
                    List<Customer> parsed = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Customer c = doc.toObject(Customer.class, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                        if (c != null) {
                            parsed.add(c);
                        }
                    }
                    synchronized (KhataRepository.this) {
                        currentCustomers.clear();
                        currentCustomers.addAll(parsed);
                        customersPendingWrites = snapshots.getMetadata().hasPendingWrites();
                        if (!snapshots.getMetadata().isFromCache() && !customersPendingWrites) {
                            lastSyncError = null;
                            lastSyncedAtMillis = System.currentTimeMillis();
                        }
                        recomputeAllSummaries();
                        updateSyncStatus();
                    }
                }
            }
        });

        final String transactionsPath = "users/" + uid + "/transactions";
        transactionsRegistration = transactionsCollection(uid).addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    if (auth.getCurrentUser() != null && uid.equals(auth.getCurrentUser().getUid())) {
                        FirestoreErrorHandler.handleFirestoreError(
                                error, FirestoreErrorHandler.OperationType.LIST, transactionsPath, auth);
                        lastSyncError = error.getLocalizedMessage();
                        updateSyncStatus();
                    }
                    return;
                }
                if (snapshots != null) {
                    List<LedgerTransaction> parsed = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        LedgerTransaction tx = doc.toObject(
                                LedgerTransaction.class,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        );
                        if (tx != null) {
                            parsed.add(tx);
                        }
                    }
                    synchronized (KhataRepository.this) {
                        currentTransactions.clear();
                        currentTransactions.addAll(parsed);
                        transactionsPendingWrites = snapshots.getMetadata().hasPendingWrites();
                        if (!snapshots.getMetadata().isFromCache() && !transactionsPendingWrites) {
                            lastSyncError = null;
                            lastSyncedAtMillis = System.currentTimeMillis();
                        }
                        recomputeAllSummaries();
                        updateSyncStatus();
                    }
                }
            }
        });
    }

    public synchronized void stopListeners() {
        if (customersRegistration != null) {
            customersRegistration.remove();
            customersRegistration = null;
        }
        if (transactionsRegistration != null) {
            transactionsRegistration.remove();
            transactionsRegistration = null;
        }
        activeUid = null;
    }

    private synchronized void clearCachedData() {
        currentCustomers.clear();
        currentTransactions.clear();
        customersPendingWrites = false;
        transactionsPendingWrites = false;
        lastSyncError = null;
        recomputeAllSummaries();
    }

    private synchronized void recomputeAllSummaries() {
        List<Customer> customersCopy = new ArrayList<>(currentCustomers);
        List<LedgerTransaction> transactionsCopy = new ArrayList<>(currentTransactions);
        List<CustomerSummary> summaries = LedgerCalculator.buildCustomerSummaries(customersCopy, transactionsCopy);
        DashboardSummary dashboard = LedgerCalculator.buildDashboardSummary(customersCopy, transactionsCopy);
        customerSummariesLiveData.postValue(summaries);
        dashboardSummaryLiveData.postValue(dashboard);
        allTransactionsLiveData.postValue(transactionsCopy);
    }

    private void updateSyncStatus() {
        SyncStatus status;
        if (isGuestMode()) {
            status = new SyncStatus(SyncStatus.State.LOCAL_ONLY, null, 0L);
        } else if (!isOnline) {
            status = new SyncStatus(SyncStatus.State.OFFLINE, null, lastSyncedAtMillis);
        } else if (lastSyncError != null && !lastSyncError.isEmpty()) {
            status = new SyncStatus(SyncStatus.State.FAILED, lastSyncError, lastSyncedAtMillis);
        } else if (customersPendingWrites || transactionsPendingWrites) {
            status = new SyncStatus(SyncStatus.State.SYNCING, null, lastSyncedAtMillis);
        } else {
            status = new SyncStatus(SyncStatus.State.SYNCED, null, lastSyncedAtMillis);
        }
        syncStatusLiveData.postValue(status);
    }

    public LiveData<List<CustomerSummary>> getCustomerSummaries() {
        return customerSummariesLiveData;
    }

    public LiveData<DashboardSummary> getDashboardSummary() {
        return dashboardSummaryLiveData;
    }

    public LiveData<List<LedgerTransaction>> getAllTransactions() {
        return allTransactionsLiveData;
    }

    public LiveData<SyncStatus> getSyncStatus() {
        return syncStatusLiveData;
    }

    public synchronized CustomerSummary getCustomerSummarySnapshot(String customerId) {
        if (customerId == null) {
            return null;
        }
        Customer found = null;
        for (Customer c : currentCustomers) {
            if (customerId.equals(c.getId())) {
                found = c;
                break;
            }
        }
        if (found == null) {
            return null;
        }
        List<LedgerTransaction> txs = LedgerCalculator.filterAndSortCustomerTransactions(customerId, currentTransactions);
        return LedgerCalculator.buildSingleCustomerSummary(found, txs);
    }

    public synchronized List<LedgerTransaction> getCustomerTransactionsSnapshot(String customerId) {
        return LedgerCalculator.filterAndSortCustomerTransactions(customerId, currentTransactions);
    }

    public synchronized LedgerTransaction getTransactionSnapshot(String transactionId) {
        if (transactionId == null) {
            return null;
        }
        for (LedgerTransaction tx : currentTransactions) {
            if (transactionId.equals(tx.getId())) {
                return tx;
            }
        }
        return null;
    }

    private String requireUserId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("User must be signed in with Google before accessing Firestore.");
        }
        return user.getUid();
    }

    private CollectionReference customersCollection(String uid) {
        return db.collection("users").document(uid).collection("customers");
    }

    private CollectionReference transactionsCollection(String uid) {
        return db.collection("users").document(uid).collection("transactions");
    }

    // --- Direct Task methods (used by both UI and Robolectric rule tests) ---

    public Task<DocumentReference> createCustomerTask(@NonNull String name, @Nullable String phone) {
        String uid = requireUserId();
        String trimmedName = ValidationUtils.normalizeOptionalText(name);
        String trimmedPhone = ValidationUtils.normalizeOptionalText(phone);
        DocumentReference docRef = customersCollection(uid).document();

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", trimmedName);
        if (!trimmedPhone.isEmpty()) {
            payload.put("phone", trimmedPhone);
        }
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        return docRef.set(payload)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.CREATE, docRef.getPath(), auth);
                        throw ex != null ? ex : new RuntimeException("Failed to create customer");
                    }
                    return docRef;
                });
    }

    public Task<Void> updateCustomerTask(
            @NonNull String customerId,
            @NonNull String name,
            @Nullable String phone
    ) {
        String uid = requireUserId();
        String trimmedName = ValidationUtils.normalizeOptionalText(name);
        String trimmedPhone = ValidationUtils.normalizeOptionalText(phone);
        DocumentReference docRef = customersCollection(uid).document(customerId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", trimmedName);
        if (trimmedPhone.isEmpty()) {
            updates.put("phone", FieldValue.delete());
        } else {
            updates.put("phone", trimmedPhone);
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        return docRef.update(updates)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.UPDATE, docRef.getPath(), auth);
                        return Tasks.forException(ex != null ? ex : new RuntimeException("Failed to update customer"));
                    }
                    return Tasks.forResult(null);
                });
    }

    public Task<Void> deleteCustomerAndTransactionsTask(@NonNull String customerId) {
        String uid = requireUserId();
        DocumentReference customerRef = customersCollection(uid).document(customerId);

        return transactionsCollection(uid)
                .whereEqualTo("customerId", customerId)
                .get()
                .continueWithTask(queryTask -> {
                    if (!queryTask.isSuccessful()) {
                        Exception ex = queryTask.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.LIST,
                                transactionsCollection(uid).getPath(), auth);
                        return Tasks.forException(ex != null ? ex : new RuntimeException("Failed to load customer transactions"));
                    }
                    List<DocumentReference> refsToDelete = new ArrayList<>();
                    QuerySnapshot snapshot = queryTask.getResult();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            refsToDelete.add(doc.getReference());
                        }
                    }
                    refsToDelete.add(customerRef);

                    List<Task<Void>> batchTasks = new ArrayList<>();
                    int batchLimit = 450;
                    for (int i = 0; i < refsToDelete.size(); i += batchLimit) {
                        WriteBatch batch = db.batch();
                        int end = Math.min(i + batchLimit, refsToDelete.size());
                        for (int j = i; j < end; j++) {
                            batch.delete(refsToDelete.get(j));
                        }
                        batchTasks.add(batch.commit());
                    }
                    return Tasks.whenAll(batchTasks);
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.DELETE, customerRef.getPath(), auth);
                        return Tasks.forException(ex != null ? ex : new RuntimeException("Failed to delete customer"));
                    }
                    return Tasks.forResult(null);
                });
    }

    public Task<DocumentReference> createTransactionTask(
            @NonNull String customerId,
            @NonNull TransactionType type,
            long amountPaise,
            @Nullable String note,
            @NonNull Timestamp transactionDate
    ) {
        if (amountPaise <= 0L) {
            return Tasks.forException(new IllegalArgumentException("Amount must be greater than zero"));
        }
        String uid = requireUserId();
        String trimmedNote = ValidationUtils.normalizeOptionalText(note);
        DocumentReference docRef = transactionsCollection(uid).document();

        Map<String, Object> payload = new HashMap<>();
        payload.put("customerId", customerId);
        payload.put("type", type.getFirestoreValue());
        payload.put("amount", amountPaise);
        if (!trimmedNote.isEmpty()) {
            payload.put("note", trimmedNote);
        }
        payload.put("transactionDate", transactionDate);
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        return docRef.set(payload)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.CREATE, docRef.getPath(), auth);
                        throw ex != null ? ex : new RuntimeException("Failed to create transaction");
                    }
                    return docRef;
                });
    }

    public Task<Void> updateTransactionTask(
            @NonNull String transactionId,
            @NonNull TransactionType type,
            long amountPaise,
            @Nullable String note,
            @NonNull Timestamp transactionDate
    ) {
        if (amountPaise <= 0L) {
            return Tasks.forException(new IllegalArgumentException("Amount must be greater than zero"));
        }
        String uid = requireUserId();
        String trimmedNote = ValidationUtils.normalizeOptionalText(note);
        DocumentReference docRef = transactionsCollection(uid).document(transactionId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("type", type.getFirestoreValue());
        updates.put("amount", amountPaise);
        if (trimmedNote.isEmpty()) {
            updates.put("note", FieldValue.delete());
        } else {
            updates.put("note", trimmedNote);
        }
        updates.put("transactionDate", transactionDate);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        return docRef.update(updates)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.UPDATE, docRef.getPath(), auth);
                        return Tasks.forException(ex != null ? ex : new RuntimeException("Failed to update transaction"));
                    }
                    return Tasks.forResult(null);
                });
    }

    public Task<Void> deleteTransactionTask(@NonNull String transactionId) {
        String uid = requireUserId();
        DocumentReference docRef = transactionsCollection(uid).document(transactionId);
        return docRef.delete()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        FirestoreErrorHandler.handleFirestoreError(
                                ex, FirestoreErrorHandler.OperationType.DELETE, docRef.getPath(), auth);
                        return Tasks.forException(ex != null ? ex : new RuntimeException("Failed to delete transaction"));
                    }
                    return Tasks.forResult(null);
                });
    }

    // --- UI-friendly async wrappers supporting offline instant local commit ---

    public void addCustomer(@NonNull String name, @Nullable String phone, @NonNull WriteCallback callback) {
        if (isGuestMode() && localGuestStore != null) {
            try {
                String id = localGuestStore.createCustomer(name, phone);
                loadGuestData();
                callback.onSuccess(id);
            } catch (Exception e) {
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to save customer");
            }
            return;
        }
        try {
            boolean onlineAtStart = checkCurrentConnectivity();
            Task<DocumentReference> task = createCustomerTask(name, phone);
            if (!onlineAtStart) {
                isOnline = false;
                updateSyncStatus();
                callback.onSuccess("");
                attachBackgroundErrorTracker(task);
                return;
            }
            task.addOnSuccessListener(ref -> {
                lastSyncError = null;
                lastSyncedAtMillis = System.currentTimeMillis();
                updateSyncStatus();
                callback.onSuccess(ref.getId());
            }).addOnFailureListener(e -> {
                lastSyncError = e.getLocalizedMessage();
                updateSyncStatus();
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to save customer");
            });
        } catch (Exception e) {
            callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to save customer");
        }
    }

    public void updateCustomer(
            @NonNull String customerId,
            @NonNull String name,
            @Nullable String phone,
            @NonNull WriteCallback callback
    ) {
        if (isGuestMode() && localGuestStore != null) {
            try {
                localGuestStore.updateCustomer(customerId, name, phone);
                loadGuestData();
                callback.onSuccess(customerId);
            } catch (Exception e) {
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to update customer");
            }
            return;
        }
        try {
            boolean onlineAtStart = checkCurrentConnectivity();
            Task<Void> task = updateCustomerTask(customerId, name, phone);
            if (!onlineAtStart) {
                isOnline = false;
                updateSyncStatus();
                callback.onSuccess(customerId);
                attachBackgroundErrorTracker(task);
                return;
            }
            task.addOnSuccessListener(unused -> {
                lastSyncError = null;
                lastSyncedAtMillis = System.currentTimeMillis();
                updateSyncStatus();
                callback.onSuccess(customerId);
            }).addOnFailureListener(e -> {
                lastSyncError = e.getLocalizedMessage();
                updateSyncStatus();
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to update customer");
            });
        } catch (Exception e) {
            callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to update customer");
        }
    }

    public void deleteCustomerAndTransactions(@NonNull String customerId, @NonNull WriteCallback callback) {
        if (isGuestMode() && localGuestStore != null) {
            try {
                localGuestStore.deleteCustomerAndTransactions(customerId);
                loadGuestData();
                callback.onSuccess(customerId);
            } catch (Exception e) {
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to delete customer");
            }
            return;
        }
        try {
            String uid = requireUserId();
            boolean onlineAtStart = checkCurrentConnectivity();
            if (!onlineAtStart) {
                // Offline fast path using local snapshot transactions
                List<LedgerTransaction> localTxs = getCustomerTransactionsSnapshot(customerId);
                List<DocumentReference> refsToDelete = new ArrayList<>();
                for (LedgerTransaction tx : localTxs) {
                    refsToDelete.add(transactionsCollection(uid).document(tx.getId()));
                }
                refsToDelete.add(customersCollection(uid).document(customerId));
                int batchLimit = 450;
                for (int i = 0; i < refsToDelete.size(); i += batchLimit) {
                    WriteBatch batch = db.batch();
                    int end = Math.min(i + batchLimit, refsToDelete.size());
                    for (int j = i; j < end; j++) {
                        batch.delete(refsToDelete.get(j));
                    }
                    attachBackgroundErrorTracker(batch.commit());
                }
                isOnline = false;
                updateSyncStatus();
                callback.onSuccess(customerId);
                return;
            }

            deleteCustomerAndTransactionsTask(customerId)
                    .addOnSuccessListener(unused -> {
                        lastSyncError = null;
                        lastSyncedAtMillis = System.currentTimeMillis();
                        updateSyncStatus();
                        callback.onSuccess(customerId);
                    })
                    .addOnFailureListener(e -> {
                        lastSyncError = e.getLocalizedMessage();
                        updateSyncStatus();
                        callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to delete customer");
                    });
        } catch (Exception e) {
            callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to delete customer");
        }
    }

    public void addTransaction(
            @NonNull String customerId,
            @NonNull TransactionType type,
            long amountPaise,
            @Nullable String note,
            @NonNull Timestamp transactionDate,
            @NonNull WriteCallback callback
    ) {
        if (isGuestMode() && localGuestStore != null) {
            try {
                long txMillis = transactionDate.toDate().getTime();
                String id = localGuestStore.createTransaction(customerId, type, amountPaise, note, txMillis);
                loadGuestData();
                callback.onSuccess(id);
            } catch (Exception e) {
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to save transaction");
            }
            return;
        }
        try {
            boolean onlineAtStart = checkCurrentConnectivity();
            Task<DocumentReference> task = createTransactionTask(customerId, type, amountPaise, note, transactionDate);
            if (!onlineAtStart) {
                isOnline = false;
                updateSyncStatus();
                callback.onSuccess("");
                attachBackgroundErrorTracker(task);
                return;
            }
            task.addOnSuccessListener(ref -> {
                lastSyncError = null;
                lastSyncedAtMillis = System.currentTimeMillis();
                updateSyncStatus();
                callback.onSuccess(ref.getId());
            }).addOnFailureListener(e -> {
                lastSyncError = e.getLocalizedMessage();
                updateSyncStatus();
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to save transaction");
            });
        } catch (Exception e) {
            callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to save transaction");
        }
    }

    public void updateTransaction(
            @NonNull String transactionId,
            @NonNull TransactionType type,
            long amountPaise,
            @Nullable String note,
            @NonNull Timestamp transactionDate,
            @NonNull WriteCallback callback
    ) {
        if (isGuestMode() && localGuestStore != null) {
            try {
                long txMillis = transactionDate.toDate().getTime();
                localGuestStore.updateTransaction(transactionId, type, amountPaise, note, txMillis);
                loadGuestData();
                callback.onSuccess(transactionId);
            } catch (Exception e) {
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to update transaction");
            }
            return;
        }
        try {
            boolean onlineAtStart = checkCurrentConnectivity();
            Task<Void> task = updateTransactionTask(transactionId, type, amountPaise, note, transactionDate);
            if (!onlineAtStart) {
                isOnline = false;
                updateSyncStatus();
                callback.onSuccess(transactionId);
                attachBackgroundErrorTracker(task);
                return;
            }
            task.addOnSuccessListener(unused -> {
                lastSyncError = null;
                lastSyncedAtMillis = System.currentTimeMillis();
                updateSyncStatus();
                callback.onSuccess(transactionId);
            }).addOnFailureListener(e -> {
                lastSyncError = e.getLocalizedMessage();
                updateSyncStatus();
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to update transaction");
            });
        } catch (Exception e) {
            callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to update transaction");
        }
    }

    public void deleteTransaction(@NonNull String transactionId, @NonNull WriteCallback callback) {
        if (isGuestMode() && localGuestStore != null) {
            try {
                localGuestStore.deleteTransaction(transactionId);
                loadGuestData();
                callback.onSuccess(transactionId);
            } catch (Exception e) {
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to delete transaction");
            }
            return;
        }
        try {
            boolean onlineAtStart = checkCurrentConnectivity();
            Task<Void> task = deleteTransactionTask(transactionId);
            if (!onlineAtStart) {
                isOnline = false;
                updateSyncStatus();
                callback.onSuccess(transactionId);
                attachBackgroundErrorTracker(task);
                return;
            }
            task.addOnSuccessListener(unused -> {
                lastSyncError = null;
                lastSyncedAtMillis = System.currentTimeMillis();
                updateSyncStatus();
                callback.onSuccess(transactionId);
            }).addOnFailureListener(e -> {
                lastSyncError = e.getLocalizedMessage();
                updateSyncStatus();
                callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to delete transaction");
            });
        } catch (Exception e) {
            callback.onError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Failed to delete transaction");
        }
    }

    private void attachBackgroundErrorTracker(Task<?> task) {
        task.addOnSuccessListener(unused -> {
            lastSyncError = null;
            lastSyncedAtMillis = System.currentTimeMillis();
            updateSyncStatus();
        }).addOnFailureListener(e -> {
            lastSyncError = e.getLocalizedMessage();
            updateSyncStatus();
        });
    }
}
