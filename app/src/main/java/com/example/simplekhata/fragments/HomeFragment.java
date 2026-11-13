package com.example.simplekhata.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.simplekhata.activities.AddEditCustomerActivity;
import com.example.simplekhata.activities.AddEditTransactionActivity;
import com.example.simplekhata.activities.CustomerLedgerActivity;
import com.example.simplekhata.activities.MainActivity;
import com.example.simplekhata.adapters.RecentTransactionAdapter;
import com.example.simplekhata.dialogs.CustomerPickerBottomSheet;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.models.DashboardSummary;
import com.example.simplekhata.models.SyncStatus;
import com.example.simplekhata.models.TransactionType;
import com.example.simplekhata.utils.MoneyUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class HomeFragment extends Fragment {

    private KhataRepository repository;
    private RecentTransactionAdapter recentAdapter;

    private TextView tvWelcome;
    private TextView tvOfflineBanner;
    private View viewHeroAccent;
    private TextView tvNetBalance;
    private TextView tvNetBalanceStatus;
    private TextView tvCustomersChip;
    private RecyclerView rvRecentActivity;
    private View cardEmptyRecent;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = KhataRepository.getInstance(requireContext());

        tvWelcome = view.findViewById(R.id.tv_home_welcome);
        tvOfflineBanner = view.findViewById(R.id.tv_home_offline_banner);
        viewHeroAccent = view.findViewById(R.id.view_hero_accent);
        tvNetBalance = view.findViewById(R.id.tv_stat_net_balance);
        tvNetBalanceStatus = view.findViewById(R.id.tv_net_balance_status);
        tvCustomersChip = view.findViewById(R.id.tv_stat_customers_chip);
        rvRecentActivity = view.findViewById(R.id.rv_recent_activity);
        cardEmptyRecent = view.findViewById(R.id.card_empty_recent);

        ImageButton btnSettings = view.findViewById(R.id.btn_home_settings);
        View btnCustomersChip = view.findViewById(R.id.btn_customers_chip);
        View cardAddCustomer = view.findViewById(R.id.card_quick_add_customer);
        View cardAddTransaction = view.findViewById(R.id.card_quick_add_transaction);
        View tvViewCustomers = view.findViewById(R.id.tv_home_view_customers);

        updateWelcomeText();

        btnSettings.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSettingsTab();
            }
        });

        View.OnClickListener openCustomersListener = v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openCustomersTab();
            }
        };
        btnCustomersChip.setOnClickListener(openCustomersListener);
        tvViewCustomers.setOnClickListener(openCustomersListener);

        cardAddCustomer.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddEditCustomerActivity.class)));

        cardAddTransaction.setOnClickListener(v -> handleQuickAddTransaction());

        recentAdapter = new RecentTransactionAdapter(item -> {
            Intent intent = new Intent(requireContext(), CustomerLedgerActivity.class);
            intent.putExtra(CustomerLedgerActivity.EXTRA_CUSTOMER_ID, item.getTransaction().getCustomerId());
            startActivity(intent);
        });
        rvRecentActivity.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecentActivity.setAdapter(recentAdapter);

        repository.getDashboardSummary().observe(getViewLifecycleOwner(), this::renderDashboard);
        repository.getSyncStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null && status.getState() == SyncStatus.State.OFFLINE) {
                tvOfflineBanner.setVisibility(View.VISIBLE);
            } else {
                tvOfflineBanner.setVisibility(View.GONE);
            }
        });
    }

    private void updateWelcomeText() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            String firstName = user.getDisplayName().trim().split("\\s+")[0];
            tvWelcome.setText(getString(R.string.home_welcome_named, firstName));
        } else {
            tvWelcome.setText(R.string.home_welcome_default);
        }
    }

    private void renderDashboard(DashboardSummary summary) {
        if (summary == null || getContext() == null) {
            return;
        }
        long netPaise = summary.getNetBalancePaise();
        tvNetBalance.setText(MoneyUtils.formatRupees(netPaise));
        int totalCustomers = summary.getTotalCustomers();
        tvCustomersChip.setText(getResources().getQuantityString(
                R.plurals.home_customers_count, totalCustomers, totalCustomers));

        if (netPaise > 0L) {
            int green = ContextCompat.getColor(requireContext(), R.color.primary_green);
            tvNetBalance.setTextColor(green);
            viewHeroAccent.setBackgroundColor(green);
            tvNetBalanceStatus.setText(R.string.net_balance_receivable);
            tvNetBalanceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.positive_amount));
            tvNetBalanceStatus.setBackgroundResource(R.drawable.bg_badge_green);
        } else if (netPaise < 0L) {
            int red = ContextCompat.getColor(requireContext(), R.color.negative_amount);
            tvNetBalance.setTextColor(red);
            viewHeroAccent.setBackgroundColor(red);
            tvNetBalanceStatus.setText(R.string.net_balance_advance);
            tvNetBalanceStatus.setTextColor(red);
            tvNetBalanceStatus.setBackgroundResource(R.drawable.bg_badge_red);
        } else {
            int green = ContextCompat.getColor(requireContext(), R.color.primary_green);
            int neutral = ContextCompat.getColor(requireContext(), R.color.text_secondary);
            tvNetBalance.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            viewHeroAccent.setBackgroundColor(green);
            tvNetBalanceStatus.setText(R.string.net_balance_settled);
            tvNetBalanceStatus.setTextColor(neutral);
            tvNetBalanceStatus.setBackgroundResource(R.drawable.bg_badge_neutral);
        }

        if (summary.getRecentActivities().isEmpty()) {
            rvRecentActivity.setVisibility(View.GONE);
            cardEmptyRecent.setVisibility(View.VISIBLE);
        } else {
            rvRecentActivity.setVisibility(View.VISIBLE);
            cardEmptyRecent.setVisibility(View.GONE);
            recentAdapter.submitList(summary.getRecentActivities());
        }
    }

    private void handleQuickAddTransaction() {
        List<CustomerSummary> customers = repository.getCustomerSummaries().getValue();
        if (customers == null || customers.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.action_add_transaction)
                    .setMessage(R.string.picker_no_customers)
                    .setPositiveButton(R.string.action_add_customer, (d, w) ->
                            startActivity(new Intent(requireContext(), AddEditCustomerActivity.class)))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return;
        }

        CustomerPickerBottomSheet.show(requireContext(), customers, summary -> {
            Intent intent = new Intent(requireContext(), AddEditTransactionActivity.class);
            intent.putExtra(AddEditTransactionActivity.EXTRA_CUSTOMER_ID, summary.getCustomer().getId());
            intent.putExtra(AddEditTransactionActivity.EXTRA_DEFAULT_TYPE, TransactionType.CREDIT.getFirestoreValue());
            intent.putExtra(AddEditTransactionActivity.EXTRA_ALLOW_TYPE_SWITCH, true);
            startActivity(intent);
        });
    }
}
