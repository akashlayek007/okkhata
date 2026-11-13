package com.example.simplekhata.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.BuildConfig;
import com.example.R;
import com.example.simplekhata.activities.AuthActivity;
import com.example.simplekhata.firebase.AuthManager;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.SyncStatus;
import com.example.simplekhata.utils.DateTimeUtils;
import com.example.simplekhata.utils.SessionModeUtils;
import com.example.simplekhata.utils.ThemeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    private KhataRepository repository;
    private AuthManager authManager;

    private ImageView ivSyncIcon;
    private TextView tvSyncValue;
    private TextView tvSyncDetail;
    private MaterialButton btnSyncRetry;
    private TextView tvAccountEmail;
    private TextView tvAccountBackupNote;
    private MaterialButton btnSignInGoogle;
    private MaterialButton btnSignOut;
    private boolean isSigningIn = false;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = KhataRepository.getInstance(requireContext());
        authManager = new AuthManager();

        ivSyncIcon = view.findViewById(R.id.iv_sync_status_icon);
        tvSyncValue = view.findViewById(R.id.tv_sync_status_value);
        tvSyncDetail = view.findViewById(R.id.tv_sync_status_detail);
        btnSyncRetry = view.findViewById(R.id.btn_sync_retry);
        tvAccountEmail = view.findViewById(R.id.tv_account_email);
        tvAccountBackupNote = view.findViewById(R.id.tv_account_backup_note);
        btnSignInGoogle = view.findViewById(R.id.btn_settings_sign_in);
        btnSignOut = view.findViewById(R.id.btn_sign_out);

        MaterialSwitch switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        View cardPrivacy = view.findViewById(R.id.card_privacy_policy);
        TextView tvAppVersion = view.findViewById(R.id.tv_app_version);

        bindAccountSection();

        btnSyncRetry.setOnClickListener(v -> repository.retrySync());

        btnSignInGoogle.setOnClickListener(v -> triggerGuestUpgradeSignIn(view));

        btnSignOut.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_sign_out_title)
                        .setMessage(R.string.dialog_sign_out_msg)
                        .setPositiveButton(R.string.settings_sign_out, (dialog, which) -> performSignOut())
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
        );

        switchDarkMode.setChecked(ThemeUtils.isDarkModeEnabled(requireContext()));
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                ThemeUtils.setDarkModeEnabled(requireContext(), isChecked)
        );

        cardPrivacy.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_privacy_title)
                        .setMessage(R.string.privacy_policy_body)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
        );

        tvAppVersion.setText(getString(
                R.string.settings_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
        ));

        repository.getSyncStatus().observe(getViewLifecycleOwner(), this::renderSyncStatus);
    }

    private void bindAccountSection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String accountLabel = user.getEmail() != null && !user.getEmail().isEmpty()
                    ? user.getEmail()
                    : (user.getDisplayName() != null ? user.getDisplayName() : user.getUid());
            tvAccountEmail.setText(getString(R.string.settings_account_signed_in_as, accountLabel));
            tvAccountBackupNote.setText(R.string.settings_account_backup_note);
            btnSignInGoogle.setVisibility(View.GONE);
            btnSignOut.setVisibility(View.VISIBLE);
        } else {
            tvAccountEmail.setText(R.string.settings_account_guest_mode);
            tvAccountBackupNote.setText(R.string.settings_account_guest_note);
            btnSignInGoogle.setVisibility(View.VISIBLE);
            btnSignOut.setVisibility(View.GONE);
        }
    }

    private void triggerGuestUpgradeSignIn(View rootView) {
        if (isSigningIn || !isAdded()) {
            return;
        }
        isSigningIn = true;
        btnSignInGoogle.setEnabled(false);
        btnSignInGoogle.setText(R.string.auth_signing_in);

        authManager.startInteractiveGoogleSignIn(requireActivity(), new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                isSigningIn = false;
                if (!isAdded()) {
                    return;
                }
                btnSignInGoogle.setEnabled(true);
                btnSignInGoogle.setText(R.string.settings_sign_in_google);
                SessionModeUtils.setGuestMode(requireContext(), false);
                repository.refreshSessionState();
                bindAccountSection();
            }

            @Override
            public void onCancelled() {
                isSigningIn = false;
                if (!isAdded()) {
                    return;
                }
                btnSignInGoogle.setEnabled(true);
                btnSignInGoogle.setText(R.string.settings_sign_in_google);
            }

            @Override
            public void onError(String message) {
                isSigningIn = false;
                if (!isAdded()) {
                    return;
                }
                btnSignInGoogle.setEnabled(true);
                btnSignInGoogle.setText(R.string.settings_sign_in_google);
                Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void renderSyncStatus(SyncStatus status) {
        if (status == null || getContext() == null) {
            return;
        }
        switch (status.getState()) {
            case SYNCED:
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_done);
                int green = ContextCompat.getColor(requireContext(), R.color.primary_green);
                ivSyncIcon.setColorFilter(green);
                tvSyncValue.setText(R.string.sync_status_synced);
                tvSyncValue.setTextColor(green);
                btnSyncRetry.setVisibility(View.GONE);
                if (status.getLastSyncedAtMillis() > 0L) {
                    tvSyncDetail.setVisibility(View.VISIBLE);
                    tvSyncDetail.setText(DateTimeUtils.formatDateTimeMillis(status.getLastSyncedAtMillis()));
                } else {
                    tvSyncDetail.setVisibility(View.GONE);
                }
                break;
            case SYNCING:
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_done);
                int amber = ContextCompat.getColor(requireContext(), R.color.warning_amber);
                ivSyncIcon.setColorFilter(amber);
                tvSyncValue.setText(R.string.sync_status_syncing);
                tvSyncValue.setTextColor(amber);
                tvSyncDetail.setVisibility(View.GONE);
                btnSyncRetry.setVisibility(View.GONE);
                break;
            case OFFLINE:
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_off);
                int secondary = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                ivSyncIcon.setColorFilter(secondary);
                tvSyncValue.setText(R.string.sync_status_offline);
                tvSyncValue.setTextColor(secondary);
                tvSyncDetail.setVisibility(View.GONE);
                btnSyncRetry.setVisibility(View.GONE);
                break;
            case LOCAL_ONLY:
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_off);
                int neutral = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                ivSyncIcon.setColorFilter(neutral);
                tvSyncValue.setText(R.string.sync_status_local_only);
                tvSyncValue.setTextColor(neutral);
                tvSyncDetail.setVisibility(View.VISIBLE);
                tvSyncDetail.setText(R.string.sync_status_local_only_detail);
                btnSyncRetry.setVisibility(View.GONE);
                break;
            case FAILED:
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_off);
                int red = ContextCompat.getColor(requireContext(), R.color.negative_amount);
                ivSyncIcon.setColorFilter(red);
                tvSyncValue.setText(R.string.sync_status_failed);
                tvSyncValue.setTextColor(red);
                if (status.getErrorMessage() != null && !status.getErrorMessage().isEmpty()) {
                    tvSyncDetail.setVisibility(View.VISIBLE);
                    tvSyncDetail.setText(status.getErrorMessage());
                } else {
                    tvSyncDetail.setVisibility(View.GONE);
                }
                btnSyncRetry.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void performSignOut() {
        SessionModeUtils.setGuestMode(requireContext(), false);
        repository.stopListeners();
        authManager.signOut(requireContext(), () -> {
            if (!isAdded()) {
                return;
            }
            Intent intent = new Intent(requireContext(), AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
