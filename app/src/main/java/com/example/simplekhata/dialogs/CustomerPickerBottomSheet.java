package com.example.simplekhata.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.utils.MoneyUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

public final class CustomerPickerBottomSheet {

    public interface OnCustomerPickedListener {
        void onCustomerPicked(CustomerSummary summary);
    }

    private CustomerPickerBottomSheet() {
    }

    public static void show(
            @NonNull Context context,
            @NonNull List<CustomerSummary> customers,
            @NonNull OnCustomerPickedListener listener
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_customer_picker, null);
        RecyclerView rv = view.findViewById(R.id.rv_picker_customers);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(new PickerAdapter(customers, summary -> {
            dialog.dismiss();
            listener.onCustomerPicked(summary);
        }));
        dialog.setContentView(view);
        dialog.show();
    }

    private static class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.Holder> {
        private final List<CustomerSummary> items;
        private final OnCustomerPickedListener listener;

        PickerAdapter(List<CustomerSummary> items, OnCustomerPickedListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_picker_customer, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CustomerSummary cs = items.get(position);
            holder.tvInitials.setText(cs.getCustomer().getInitials());
            holder.tvName.setText(cs.getCustomer().getName());
            long bal = cs.getBalancePaise();
            holder.tvBalance.setText(MoneyUtils.formatAbsoluteRupees(bal));
            int colorRes = bal > 0L ? R.color.negative_amount : R.color.positive_amount;
            holder.tvBalance.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));
            holder.itemView.setOnClickListener(v -> listener.onCustomerPicked(cs));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvInitials;
            final TextView tvName;
            final TextView tvBalance;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvInitials = itemView.findViewById(R.id.tv_picker_initials);
                tvName = itemView.findViewById(R.id.tv_picker_name);
                tvBalance = itemView.findViewById(R.id.tv_picker_balance);
            }
        }
    }
}
