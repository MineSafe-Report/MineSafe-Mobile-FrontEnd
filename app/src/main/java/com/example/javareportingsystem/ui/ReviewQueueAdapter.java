package com.example.javareportingsystem.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class ReviewQueueAdapter extends RecyclerView.Adapter<ReviewQueueAdapter.ReportViewHolder> {
    interface OnReportSelectedListener {
        void onReportSelected(SyncedReportEntity report);
    }

    private final List<SyncedReportEntity> reports = new ArrayList<>();
    private final OnReportSelectedListener listener;

    ReviewQueueAdapter(OnReportSelectedListener listener) {
        this.listener = listener;
    }

    void setReports(List<SyncedReportEntity> newReports) {
        reports.clear();
        if (newReports != null) reports.addAll(newReports);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review_queue, parent, false);
        return new ReportViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        SyncedReportEntity report = reports.get(position);
        String type = typeLabel(holder.itemView, report.typeCode);
        String title = report.referenceNumber == null || report.referenceNumber.trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.report_list_name,
                        type, report.sourceLocalReportId)
                : report.referenceNumber.trim();
        String location = report.locationName == null || report.locationName.trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.report_value_unavailable)
                : report.locationName.trim();
        String created = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(report.submittedAtEpochMillis));

        holder.typeIcon.setImageResource(typeIcon(report.typeCode));
        holder.title.setText(title);
        holder.location.setText(holder.itemView.getContext().getString(
                R.string.review_queue_location_value, location));
        holder.created.setText(created);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.review_queue_item_accessibility, type, location, created));
        holder.itemView.setOnClickListener(v -> listener.onReportSelected(report));
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    private String typeLabel(View view, String typeCode) {
        if (ReportValues.HAZARD.equals(typeCode)) return view.getContext().getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equals(typeCode)) return view.getContext().getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equals(typeCode)) return view.getContext().getString(R.string.report_type_near_miss);
        return view.getContext().getString(R.string.report_type_not_selected);
    }

    private int typeIcon(String typeCode) {
        if (ReportValues.HAZARD.equals(typeCode)) return R.drawable.ic_ms_warning;
        if (ReportValues.INCIDENT.equals(typeCode)) return R.drawable.ic_ms_personal_injury;
        if (ReportValues.NEAR_MISS.equals(typeCode)) return R.drawable.ic_ms_visibility;
        return R.drawable.ic_ms_description;
    }

    static final class ReportViewHolder extends RecyclerView.ViewHolder {
        final ImageView typeIcon;
        final TextView title;
        final TextView location;
        final TextView created;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.imgReportType);
            title = itemView.findViewById(R.id.tvReportName);
            location = itemView.findViewById(R.id.tvReportLocation);
            created = itemView.findViewById(R.id.tvReportDate);
        }
    }
}