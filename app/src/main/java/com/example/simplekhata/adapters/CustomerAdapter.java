package com.example.simplekhata.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.simplekhata.models.Customer;
import com.example.simplekhata.models.CustomerSummary;
import com.example.simplekhata.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder> {

    public interface OnCustomerClickListener {
        void onCustomerClick(CustomerSummary summary);
    }

    private final List<CustomerSummary> items = new ArrayList<>();
    private final OnCustomerClickListener listener;

    public CustomerAdapter(@NonNull OnCustomerClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CustomerSummary> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvInitials;
        private final TextView tvName;
        private final TextView tvPhone;
        private final TextView tvBalance;
        private final TextView tvBalanceLabel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitials = itemView.findViewById(R.id.tv_customer_initials);
            tvName = itemView.findViewById(R.id.tv_customer_name);
            tvPhone = itemView.findViewById(R.id.tv_customer_phone);
            tvBalance = itemView.findViewById(R.id.tv_customer_balance);
            tvBalanceLabel = itemView.findViewById(R.id.tv_customer_balance_label);
        }

        void bind(CustomerSummary summary, OnCustomerClickListener listener) {
            Context context = itemView.getContext();
            Customer customer = summary.getCustomer();
            tvInitials.setText(customer.getInitials());
            tvName.setText(customer.getName());

            String phone = customer.getPhone();
            if (phone.isEmpty()) {
                tvPhone.setText(R.string.no_phone_number);
            } else {
                tvPhone.setText(phone);
            }

            long balance = summary.getBalancePaise();
            if (balance > 0L) {
                tvBalance.setText(MoneyUtils.formatAbsoluteRupees(balance));
                tvBalance.setTextColor(ContextCompat.getColor(context, R.color.negative_amount));
                tvBalanceLabel.setText(R.string.balance_due_label);
                tvBalanceLabel.setTextColor(ContextCompat.getColor(context, R.color.negative_amount));
            } else if (balance < 0L) {
                tvBalance.setText(MoneyUtils.formatAbsoluteRupees(balance));
                tvBalance.setTextColor(ContextCompat.getColor(context, R.color.positive_amount));
                tvBalanceLabel.setText(R.string.balance_advance_label);
                tvBalanceLabel.setTextColor(ContextCompat.getColor(context, R.color.positive_amount));
            } else {
                tvBalance.setText(MoneyUtils.formatAbsoluteRupees(0L));
                tvBalance.setTextColor(ContextCompat.getColor(context, R.color.positive_amount));
                tvBalanceLabel.setText(R.string.balance_settled_label);
                tvBalanceLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }

            itemView.setOnClickListener(v -> listener.onCustomerClick(summary));
        }
    }
}
