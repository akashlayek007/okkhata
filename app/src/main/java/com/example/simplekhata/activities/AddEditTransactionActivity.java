package com.example.simplekhata.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.LedgerTransaction;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.DateTimeUtils;
import com.example.simplekhata.utils.InsetsUtils;
import com.example.simplekhata.utils.MoneyUtils;
import com.example.simplekhata.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;

import java.util.Calendar;

public class AddEditTransactionActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_ID = "extra_customer_id";
    public static final String EXTRA_TRANSACTION_ID = "extra_transaction_id";
    public static final String EXTRA_DEFAULT_TYPE = "extra_default_type";
    public static final String EXTRA_ALLOW_TYPE_SWITCH = "extra_allow_type_switch";

    private KhataRepository repository;
    private String customerId;
    private String editingTransactionId;
    private LedgerTransaction editingTransaction;
    private CustomerSummary customerSummary;
    private TransactionType selectedType = TransactionType.CREDIT;
    private final Calendar selectedCalendar = Calendar.getInstance();
    private boolean isEditMode = false;
    private boolean isSubmitting = false;

    private TextView tvToolbarTitle;
    private TextInputLayout tilAmount;
    private TextInputLayout tilNote;
    private TextInputEditText etAmount;
    private TextInputEditText etNote;
    private TextInputEditText etDateTime;
    private MaterialButton btnSave;
    private MaterialButton btnDeleteBottom;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_transaction);
        InsetsUtils.applyEdgeToEdgeWithPadding(this, findViewById(R.id.add_edit_tx_root));

        repository = KhataRepository.getInstance(this);
        customerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
        editingTransactionId = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        String defaultTypeStr = getIntent().getStringExtra(EXTRA_DEFAULT_TYPE);
        boolean allowTypeSwitch = getIntent().getBooleanExtra(EXTRA_ALLOW_TYPE_SWITCH, false);

        if (customerId == null || customerId.isEmpty()) {
            finish();
            return;
        }

        customerSummary = repository.getCustomerSummarySnapshot(customerId);
        if (editingTransactionId != null && !editingTransactionId.isEmpty()) {
            isEditMode = true;
            editingTransaction = repository.getTransactionSnapshot(editingTransactionId);
            if (editingTransaction == null) {
                finish();
                return;
            }
            selectedType = editingTransaction.getTransactionType();
            if (editingTransaction.getTransactionDate() != null) {
                selectedCalendar.setTime(editingTransaction.getTransactionDate().toDate());
            }
        } else if (defaultTypeStr != null) {
            selectedType = TransactionType.fromFirestoreValue(defaultTypeStr);
        }

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnDeleteTop = findViewById(R.id.btn_delete_tx_top);
        tvToolbarTitle = findViewById(R.id.tv_toolbar_title);
        TextView tvCustomerName = findViewById(R.id.tv_tx_customer_name);
        TextView tvCustomerBalance = findViewById(R.id.tv_tx_customer_balance);
        View layoutTypeToggle = findViewById(R.id.layout_type_toggle);
        MaterialButtonToggleGroup toggleType = findViewById(R.id.toggle_tx_type);
        tilAmount = findViewById(R.id.til_amount);
        tilNote = findViewById(R.id.til_note);
        etAmount = findViewById(R.id.et_amount);
        etNote = findViewById(R.id.et_note);
        etDateTime = findViewById(R.id.et_date_time);
        btnSave = findViewById(R.id.btn_save_transaction);
        btnDeleteBottom = findViewById(R.id.btn_delete_transaction);
        progress = findViewById(R.id.progress_transaction);

        btnBack.setOnClickListener(v -> finish());

        if (customerSummary != null) {
            tvCustomerName.setText(customerSummary.getCustomer().getName());
            long balance = customerSummary.getBalancePaise();
            tvCustomerBalance.setText(getString(R.string.label_current_balance) + ": " + MoneyUtils.formatRupees(balance));
            int balColor = balance > 0L ? R.color.negative_amount : R.color.positive_amount;
            tvCustomerBalance.setTextColor(ContextCompat.getColor(this, balColor));
        }

        if (isEditMode || allowTypeSwitch) {
            layoutTypeToggle.setVisibility(View.VISIBLE);
            toggleType.check(selectedType == TransactionType.PAYMENT ? R.id.btn_type_payment : R.id.btn_type_credit);
            toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    selectedType = (checkedId == R.id.btn_type_payment)
                            ? TransactionType.PAYMENT
                            : TransactionType.CREDIT;
                    updateActionStyling();
                }
            });
        }

        if (isEditMode && editingTransaction != null) {
            etAmount.setText(MoneyUtils.formatEditableAmount(editingTransaction.getAmount()));
            etNote.setText(editingTransaction.getNote());
            btnDeleteTop.setVisibility(View.VISIBLE);
            btnDeleteBottom.setVisibility(View.VISIBLE);
            btnDeleteTop.setOnClickListener(v -> confirmDeleteTransaction());
            btnDeleteBottom.setOnClickListener(v -> confirmDeleteTransaction());
        }

        updateDateTimeField();
        etDateTime.setOnClickListener(v -> openDatePicker());

        updateActionStyling();
        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void updateActionStyling() {
        if (isEditMode) {
            tvToolbarTitle.setText(R.string.title_edit_transaction);
            btnSave.setText(R.string.btn_save_changes);
            int color = selectedType == TransactionType.PAYMENT
                    ? ContextCompat.getColor(this, R.color.primary_green)
                    : ContextCompat.getColor(this, R.color.negative_amount);
            btnSave.setBackgroundTintList(ColorStateList.valueOf(color));
        } else if (selectedType == TransactionType.PAYMENT) {
            tvToolbarTitle.setText(R.string.title_add_payment);
            btnSave.setText(R.string.btn_save_payment);
            btnSave.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.primary_green)));
        } else {
            tvToolbarTitle.setText(R.string.title_add_credit);
            btnSave.setText(R.string.btn_save_credit);
            btnSave.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.negative_amount)));
        }
    }

    private void updateDateTimeField() {
        etDateTime.setText(DateTimeUtils.formatDateTimeMillis(selectedCalendar.getTimeInMillis()));
    }

    private void openDatePicker() {
        int year = selectedCalendar.get(Calendar.YEAR);
        int month = selectedCalendar.get(Calendar.MONTH);
        int day = selectedCalendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, y, m, d) -> {
            selectedCalendar.set(Calendar.YEAR, y);
            selectedCalendar.set(Calendar.MONTH, m);
            selectedCalendar.set(Calendar.DAY_OF_MONTH, d);
            openTimePicker();
        }, year, month, day).show();
    }

    private void openTimePicker() {
        int hour = selectedCalendar.get(Calendar.HOUR_OF_DAY);
        int minute = selectedCalendar.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, h, min) -> {
            selectedCalendar.set(Calendar.HOUR_OF_DAY, h);
            selectedCalendar.set(Calendar.MINUTE, min);
            selectedCalendar.set(Calendar.SECOND, 0);
            updateDateTimeField();
        }, hour, minute, false).show();
    }

    private void attemptSave() {
        if (isSubmitting) {
            return;
        }
        tilAmount.setError(null);
        tilNote.setError(null);

        String rawAmount = etAmount.getText() != null ? etAmount.getText().toString() : "";
        long amountPaise = MoneyUtils.parseToPaise(rawAmount);
        if (amountPaise <= 0L) {
            tilAmount.setError(getString(R.string.error_invalid_amount));
            etAmount.requestFocus();
            return;
        }

        String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";
        if (note.length() > ValidationUtils.MAX_NOTE_LENGTH) {
            tilNote.setError(getString(R.string.error_note_too_long));
            etNote.requestFocus();
            return;
        }

        if (selectedType == TransactionType.PAYMENT) {
            long outstandingBeforeThis = computeOutstandingBeforeCurrentTransaction();
            if (amountPaise > outstandingBeforeThis) {
                long advancePaise = amountPaise - Math.max(0L, outstandingBeforeThis);
                String paymentStr = MoneyUtils.formatAbsoluteRupees(amountPaise);
                String outstandingStr = MoneyUtils.formatRupees(Math.max(0L, outstandingBeforeThis));
                String advanceStr = MoneyUtils.formatAbsoluteRupees(advancePaise);

                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_advance_title)
                        .setMessage(getString(R.string.dialog_advance_msg, paymentStr, outstandingStr, advanceStr))
                        .setPositiveButton(R.string.btn_confirm_advance, (dialog, which) ->
                                commitTransaction(amountPaise, note))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show();
                return;
            }
        }

        commitTransaction(amountPaise, note);
    }

    private long computeOutstandingBeforeCurrentTransaction() {
        CustomerSummary latest = repository.getCustomerSummarySnapshot(customerId);
        long currentBalance = latest != null ? latest.getBalancePaise() : 0L;
        if (!isEditMode || editingTransaction == null) {
            return currentBalance;
        }
        if (editingTransaction.getTransactionType() == TransactionType.PAYMENT) {
            return currentBalance + editingTransaction.getAmount();
        } else {
            return currentBalance - editingTransaction.getAmount();
        }
    }

    private void commitTransaction(long amountPaise, String note) {
        if (isSubmitting) {
            return;
        }
        setSubmitting(true);
        Timestamp txDate = new Timestamp(selectedCalendar.getTime());

        if (isEditMode) {
            repository.updateTransaction(
                    editingTransactionId,
                    selectedType,
                    amountPaise,
                    note,
                    txDate,
                    new KhataRepository.WriteCallback() {
                        @Override
                        public void onSuccess(String documentId) {
                            setSubmitting(false);
                            Toast.makeText(AddEditTransactionActivity.this,
                                    R.string.msg_transaction_updated, Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            setSubmitting(false);
                            Toast.makeText(AddEditTransactionActivity.this,
                                    message, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        } else {
            repository.addTransaction(
                    customerId,
                    selectedType,
                    amountPaise,
                    note,
                    txDate,
                    new KhataRepository.WriteCallback() {
                        @Override
                        public void onSuccess(String documentId) {
                            setSubmitting(false);
                            int msgRes = selectedType == TransactionType.PAYMENT
                                    ? R.string.msg_payment_saved
                                    : R.string.msg_credit_saved;
                            Toast.makeText(AddEditTransactionActivity.this, msgRes, Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            setSubmitting(false);
                            Toast.makeText(AddEditTransactionActivity.this,
                                    message, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        }
    }

    private void confirmDeleteTransaction() {
        if (isSubmitting || editingTransaction == null) {
            return;
        }
        String typeLabel = editingTransaction.getTransactionType() == TransactionType.PAYMENT
                ? getString(R.string.type_payment_badge)
                : getString(R.string.type_credit_badge);
        String amountStr = MoneyUtils.formatAbsoluteRupees(editingTransaction.getAmount());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_tx_title)
                .setMessage(getString(R.string.dialog_delete_tx_msg, typeLabel, amountStr))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteTransaction())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deleteTransaction() {
        setSubmitting(true);
        repository.deleteTransaction(editingTransactionId, new KhataRepository.WriteCallback() {
            @Override
            public void onSuccess(String documentId) {
                setSubmitting(false);
                Toast.makeText(AddEditTransactionActivity.this,
                        R.string.msg_transaction_deleted, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                setSubmitting(false);
                Toast.makeText(AddEditTransactionActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;
        btnSave.setEnabled(!submitting);
        btnDeleteBottom.setEnabled(!submitting);
        progress.setVisibility(submitting ? View.VISIBLE : View.GONE);
    }
}
