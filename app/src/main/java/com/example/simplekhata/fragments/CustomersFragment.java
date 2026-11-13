package com.example.simplekhata.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.simplekhata.activities.AddEditCustomerActivity;
import com.example.simplekhata.activities.CustomerLedgerActivity;
import com.example.simplekhata.adapters.CustomerAdapter;
import com.example.simplekhata.firebase.KhataRepository;
import com.example.simplekhata.models.CustomerSummary;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CustomersFragment extends Fragment {

    private KhataRepository repository;
    private CustomerAdapter adapter;
    private List<CustomerSummary> allCustomers = Collections.emptyList();
    private String currentQuery = "";

    private RecyclerView rvCustomers;
    private View layoutEmptyCustomers;
    private TextView tvEmptySearch;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_customers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = KhataRepository.getInstance(requireContext());

        rvCustomers = view.findViewById(R.id.rv_customers);
        layoutEmptyCustomers = view.findViewById(R.id.layout_empty_customers);
        tvEmptySearch = view.findViewById(R.id.tv_empty_search);
        TextInputEditText etSearch = view.findViewById(R.id.et_search_customers);
        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add_customer);
        MaterialButton btnEmptyAdd = view.findViewById(R.id.btn_empty_add_customer);

        adapter = new CustomerAdapter(summary -> {
            Intent intent = new Intent(requireContext(), CustomerLedgerActivity.class);
            intent.putExtra(CustomerLedgerActivity.EXTRA_CUSTOMER_ID, summary.getCustomer().getId());
            startActivity(intent);
        });

        rvCustomers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCustomers.setAdapter(adapter);

        View.OnClickListener openAddCustomer = v ->
                startActivity(new Intent(requireContext(), AddEditCustomerActivity.class));
        fabAdd.setOnClickListener(openAddCustomer);
        btnEmptyAdd.setOnClickListener(openAddCustomer);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s != null ? s.toString().trim() : "";
                applyFilterAndRender();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        repository.getCustomerSummaries().observe(getViewLifecycleOwner(), summaries -> {
            allCustomers = summaries != null ? summaries : Collections.emptyList();
            applyFilterAndRender();
        });
    }

    private void applyFilterAndRender() {
        if (allCustomers.isEmpty()) {
            rvCustomers.setVisibility(View.GONE);
            tvEmptySearch.setVisibility(View.GONE);
            layoutEmptyCustomers.setVisibility(View.VISIBLE);
            return;
        }

        layoutEmptyCustomers.setVisibility(View.GONE);
        if (currentQuery.isEmpty()) {
            rvCustomers.setVisibility(View.VISIBLE);
            tvEmptySearch.setVisibility(View.GONE);
            adapter.submitList(allCustomers);
            return;
        }

        String lowerQuery = currentQuery.toLowerCase(Locale.getDefault());
        String digitsQuery = currentQuery.replaceAll("[^0-9]", "");

        List<CustomerSummary> filtered = new ArrayList<>();
        for (CustomerSummary cs : allCustomers) {
            String name = cs.getCustomer().getName().toLowerCase(Locale.getDefault());
            String phone = cs.getCustomer().getPhone();
            String phoneDigits = phone.replaceAll("[^0-9]", "");
            boolean matchesName = name.contains(lowerQuery);
            boolean matchesPhone = phone.toLowerCase(Locale.getDefault()).contains(lowerQuery)
                    || (!digitsQuery.isEmpty() && phoneDigits.contains(digitsQuery));
            if (matchesName || matchesPhone) {
                filtered.add(cs);
            }
        }

        if (filtered.isEmpty()) {
            rvCustomers.setVisibility(View.GONE);
            tvEmptySearch.setVisibility(View.VISIBLE);
        } else {
            rvCustomers.setVisibility(View.VISIBLE);
            tvEmptySearch.setVisibility(View.GONE);
            adapter.submitList(filtered);
        }
    }
}
