package com.example.simplekhata.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.simplekhata.adapters.LedgerTransactionAdapter;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.InsetsUtils;
import com.example.simplekhata.utils.MoneyUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class CustomerLedgerActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_ID = "extra_customer_id";

    private KhataRepository repository;
    private String customerId;
    private CustomerSummary latestSummary;
    private LedgerTransactionAdapter adapter;

    private TextView tvInitials;
    private TextView tvName;
    private TextView tvPhone;
    private View layoutPhone;
    private TextView tvBalanceMain;
    private TextView tvBalanceLabel;
    private TextView tvTotalCredit;
    private TextView tvTotalPayments;
    private TextView tvSummaryBalance;
    private RecyclerView rvTransactions;
    private TextView tvEmptyTransactions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_ledger);
        InsetsUtils.applyEdgeToEdgeWithPadding(this, findViewById(R.id.ledger_root));

        customerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
        if (customerId == null || customerId.isEmpty()) {
            finish();
            return;
        }

        repository = KhataRepository.getInstance(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnEdit = findViewById(R.id.btn_edit_customer);
        ImageButton btnDelete = findViewById(R.id.btn_delete_customer);
        tvInitials = findViewById(R.id.tv_ledger_initials);
        tvName = findViewById(R.id.tv_ledger_customer_name);
        tvPhone = findViewById(R.id.tv_ledger_customer_phone);
        layoutPhone = findViewById(R.id.layout_ledger_phone);
        tvBalanceMain = findViewById(R.id.tv_ledger_balance_main);
        tvBalanceLabel = findViewById(R.id.tv_ledger_balance_label);
        tvTotalCredit = findViewById(R.id.tv_summary_credit);
        tvTotalPayments = findViewById(R.id.tv_summary_payments);
        tvSummaryBalance = findViewById(R.id.tv_summary_balance);
        rvTransactions = findViewById(R.id.rv_ledger_transactions);
        tvEmptyTransactions = findViewById(R.id.tv_empty_transactions);
        MaterialButton btnYouGave = findViewById(R.id.btn_you_gave);
        MaterialButton btnYouGot = findViewById(R.id.btn_you_got);

        btnBack.setOnClickListener(v -> finish());

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditCustomerActivity.class);
            intent.putExtra(AddEditCustomerActivity.EXTRA_CUSTOMER_ID, customerId);
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> confirmDeleteCustomer());

        layoutPhone.setOnClickListener(v -> {
            if (latestSummary != null && !latestSummary.getCustomer().getPhone().isEmpty()) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse("tel:" + latestSummary.getCustomer().getPhone()));
                startActivity(dialIntent);
            }
        });

        adapter = new LedgerTransactionAdapter(transaction -> {
            Intent intent = new Intent(this, AddEditTransactionActivity.class);
            intent.putExtra(AddEditTransactionActivity.EXTRA_CUSTOMER_ID, customerId);
            intent.putExtra(AddEditTransactionActivity.EXTRA_TRANSACTION_ID, transaction.getId());
            startActivity(intent);
        });

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);

        btnYouGave.setOnClickListener(v -> openAddTransaction(TransactionType.CREDIT));
        btnYouGot.setOnClickListener(v -> openAddTransaction(TransactionType.PAYMENT));

        repository.getCustomerSummaries().observe(this, summaries -> refreshLedger());
        repository.getAllTransactions().observe(this, txs -> refreshLedger());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLedger();
    }

    private void refreshLedger() {
        CustomerSummary summary = repository.getCustomerSummarySnapshot(customerId);
        if (summary == null) {
            if (latestSummary != null) {
                // Customer was deleted
                finish();
            }
            return;
        }
        latestSummary = summary;
        Customer customer = summary.getCustomer();

        tvInitials.setText(customer.getInitials());
        tvName.setText(customer.getName());

        String phone = customer.getPhone();
        if (phone.isEmpty()) {
            tvPhone.setText(R.string.no_phone_number);
            layoutPhone.setClickable(false);
        } else {
            tvPhone.setText(phone);
            layoutPhone.setClickable(true);
        }

        long balance = summary.getBalancePaise();
        tvTotalCredit.setText(MoneyUtils.formatAbsoluteRupees(summary.getTotalCreditPaise()));
        tvTotalPayments.setText(MoneyUtils.formatAbsoluteRupees(summary.getTotalPaymentPaise()));
        tvSummaryBalance.setText(MoneyUtils.formatRupees(balance));

        if (balance > 0L) {
            int red = ContextCompat.getColor(this, R.color.negative_amount);
            tvBalanceMain.setText(MoneyUtils.formatAbsoluteRupees(balance));
            tvBalanceMain.setTextColor(red);
            tvBalanceLabel.setText(R.string.balance_due_label);
            tvBalanceLabel.setTextColor(red);
            tvSummaryBalance.setTextColor(red);
        } else if (balance < 0L) {
            int green = ContextCompat.getColor(this, R.color.positive_amount);
            tvBalanceMain.setText(MoneyUtils.formatAbsoluteRupees(balance));
            tvBalanceMain.setTextColor(green);
            tvBalanceLabel.setText(R.string.balance_advance_label);
            tvBalanceLabel.setTextColor(green);
            tvSummaryBalance.setTextColor(green);
        } else {
            int green = ContextCompat.getColor(this, R.color.positive_amount);
            tvBalanceMain.setText(MoneyUtils.formatAbsoluteRupees(0L));
            tvBalanceMain.setTextColor(green);
            tvBalanceLabel.setText(R.string.balance_settled_label);
            tvBalanceLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvSummaryBalance.setTextColor(green);
        }

        List<LedgerTransaction> transactions = repository.getCustomerTransactionsSnapshot(customerId);
        if (transactions.isEmpty()) {
            rvTransactions.setVisibility(View.GONE);
            tvEmptyTransactions.setVisibility(View.VISIBLE);
        } else {
            rvTransactions.setVisibility(View.VISIBLE);
            tvEmptyTransactions.setVisibility(View.GONE);
            adapter.submitList(transactions);
        }
    }

    private void openAddTransaction(TransactionType type) {
        Intent intent = new Intent(this, AddEditTransactionActivity.class);
        intent.putExtra(AddEditTransactionActivity.EXTRA_CUSTOMER_ID, customerId);
        intent.putExtra(AddEditTransactionActivity.EXTRA_DEFAULT_TYPE, type.getFirestoreValue());
        intent.putExtra(AddEditTransactionActivity.EXTRA_ALLOW_TYPE_SWITCH, false);
        startActivity(intent);
    }

    private void confirmDeleteCustomer() {
        if (latestSummary == null) {
            return;
        }
        String name = latestSummary.getCustomer().getName();
        int txCount = latestSummary.getTransactionCount();
        String msg = txCount > 0
                ? getString(R.string.dialog_delete_customer_msg_with_tx, name, txCount)
                : getString(R.string.dialog_delete_customer_msg_no_tx, name);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_customer_title)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_delete, (dialog, which) ->
                        repository.deleteCustomerAndTransactions(customerId, new KhataRepository.WriteCallback() {
                            @Override
                            public void onSuccess(String documentId) {
                                Toast.makeText(CustomerLedgerActivity.this,
                                        R.string.msg_customer_deleted, Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(CustomerLedgerActivity.this,
                                        message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
