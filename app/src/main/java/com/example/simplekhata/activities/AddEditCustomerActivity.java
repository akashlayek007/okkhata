package com.example.simplekhata.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.utils.InsetsUtils;
import com.example.simplekhata.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class AddEditCustomerActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_ID = "extra_customer_id";

    private KhataRepository repository;
    private String editingCustomerId;
    private CustomerSummary editingSummary;
    private boolean isSubmitting = false;

    private TextInputLayout tilName;
    private TextInputLayout tilPhone;
    private TextInputEditText etName;
    private TextInputEditText etPhone;
    private MaterialButton btnPickContact;
    private MaterialButton btnSave;
    private MaterialButton btnCancel;
    private CircularProgressIndicator progress;

    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    if (contactUri != null) {
                        populateFromContactUri(contactUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_customer);
        InsetsUtils.applyEdgeToEdgeWithPadding(this, findViewById(R.id.add_edit_customer_root));

        repository = KhataRepository.getInstance(this);
        editingCustomerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnDeleteTop = findViewById(R.id.btn_delete_customer_top);
        TextView tvTitle = findViewById(R.id.tv_toolbar_title);
        tilName = findViewById(R.id.til_customer_name);
        tilPhone = findViewById(R.id.til_customer_phone);
        etName = findViewById(R.id.et_customer_name);
        etPhone = findViewById(R.id.et_customer_phone);
        btnPickContact = findViewById(R.id.btn_pick_contact);
        btnSave = findViewById(R.id.btn_save_customer);
        btnCancel = findViewById(R.id.btn_cancel_customer);
        progress = findViewById(R.id.progress_customer);

        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
        btnPickContact.setOnClickListener(v -> launchContactPicker());

        if (editingCustomerId != null && !editingCustomerId.isEmpty()) {
            tvTitle.setText(R.string.title_edit_customer);
            btnSave.setText(R.string.btn_update_customer);
            btnDeleteTop.setVisibility(View.VISIBLE);
            editingSummary = repository.getCustomerSummarySnapshot(editingCustomerId);
            if (editingSummary != null) {
                Customer customer = editingSummary.getCustomer();
                etName.setText(customer.getName());
                etPhone.setText(customer.getPhone());
            }
            btnDeleteTop.setOnClickListener(v -> confirmDeleteCustomer());
        } else {
            tvTitle.setText(R.string.title_add_customer);
            btnSave.setText(R.string.btn_save_customer);
        }

        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void attemptSave() {
        if (isSubmitting) {
            return;
        }
        tilName.setError(null);
        tilPhone.setError(null);

        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        if (name.isEmpty()) {
            tilName.setError(getString(R.string.error_empty_name));
            etName.requestFocus();
            return;
        }
        if (name.length() > ValidationUtils.MAX_NAME_LENGTH) {
            tilName.setError(getString(R.string.error_name_too_long));
            etName.requestFocus();
            return;
        }
        if (!ValidationUtils.isValidOptionalPhone(phone)) {
            tilPhone.setError(getString(R.string.error_invalid_phone));
            etPhone.requestFocus();
            return;
        }

        setSubmitting(true);
        if (editingCustomerId != null && !editingCustomerId.isEmpty()) {
            repository.updateCustomer(editingCustomerId, name, phone, new KhataRepository.WriteCallback() {
                @Override
                public void onSuccess(String documentId) {
                    setSubmitting(false);
                    Toast.makeText(AddEditCustomerActivity.this, R.string.msg_customer_updated, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    setSubmitting(false);
                    Toast.makeText(AddEditCustomerActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            repository.addCustomer(name, phone, new KhataRepository.WriteCallback() {
                @Override
                public void onSuccess(String documentId) {
                    setSubmitting(false);
                    Toast.makeText(AddEditCustomerActivity.this, R.string.msg_customer_saved, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    setSubmitting(false);
                    Toast.makeText(AddEditCustomerActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void confirmDeleteCustomer() {
        if (isSubmitting || editingCustomerId == null) {
            return;
        }
        CustomerSummary current = repository.getCustomerSummarySnapshot(editingCustomerId);
        if (current == null) {
            current = editingSummary;
        }
        String name = current != null ? current.getCustomer().getName() : "";
        int txCount = current != null ? current.getTransactionCount() : 0;

        String msg = txCount > 0
                ? getString(R.string.dialog_delete_customer_msg_with_tx, name, txCount)
                : getString(R.string.dialog_delete_customer_msg_no_tx, name);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_customer_title)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteCustomer())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deleteCustomer() {
        setSubmitting(true);
        repository.deleteCustomerAndTransactions(editingCustomerId, new KhataRepository.WriteCallback() {
            @Override
            public void onSuccess(String documentId) {
                setSubmitting(false);
                Toast.makeText(AddEditCustomerActivity.this, R.string.msg_customer_deleted, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                setSubmitting(false);
                Toast.makeText(AddEditCustomerActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void launchContactPicker() {
        if (isSubmitting) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            pickContactLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_no_contacts_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void populateFromContactUri(Uri contactUri) {
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Toast.makeText(this, R.string.msg_contact_read_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

            String rawNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : "";
            String normalizedPhone = ValidationUtils.normalizePickedPhone(rawNumber);
            if (normalizedPhone.isEmpty()) {
                Toast.makeText(this, R.string.msg_contact_no_phone, Toast.LENGTH_SHORT).show();
                return;
            }

            tilPhone.setError(null);
            etPhone.setText(normalizedPhone);
            etPhone.setSelection(normalizedPhone.length());

            if (nameIndex >= 0) {
                String currentName = etName.getText() != null ? etName.getText().toString().trim() : "";
                String contactName = ValidationUtils.normalizeOptionalText(cursor.getString(nameIndex));
                if (currentName.isEmpty() && !contactName.isEmpty()) {
                    if (contactName.length() > ValidationUtils.MAX_NAME_LENGTH) {
                        contactName = contactName.substring(0, ValidationUtils.MAX_NAME_LENGTH);
                    }
                    tilName.setError(null);
                    etName.setText(contactName);
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.msg_contact_read_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;
        btnSave.setEnabled(!submitting);
        btnCancel.setEnabled(!submitting);
        if (btnPickContact != null) {
            btnPickContact.setEnabled(!submitting);
        }
        progress.setVisibility(submitting ? View.VISIBLE : View.GONE);
    }
}
