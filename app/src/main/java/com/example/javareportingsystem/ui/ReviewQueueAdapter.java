package com.example.javareportingsystem.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.model.ReviewReport;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class ReviewQueueAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int REPORT_ROW = 0;
    private static final int SECTION_ROW = 1;
    interface OnReportSelectedListener {
        void onReportSelected(ReviewReport report);
    }

    private final List<Row> rows = new ArrayList<>();
    private final Context context;
    private final OnReportSelectedListener listener;
    private final boolean safetyOfficerMode;

    ReviewQueueAdapter(Context context, OnReportSelectedListener listener, boolean safetyOfficerMode) {
        this.context = context;
        this.listener = listener;
        this.safetyOfficerMode = safetyOfficerMode;
    }

    void setReports(List<ReviewReport> newReports) {
        rows.clear();
        if (newReports != null) {
            for (ReviewReport report : newReports) rows.add(new Row(report, false));
        }
        notifyDataSetChanged();
    }

    void setSafetyOfficerSections(List<ReviewReport> newIncidents,
            List<ReviewReport> openedRecently, List<ReviewReport> pastIncidents,
            int accountId) {
        rows.clear();
        addSection(R.string.so_section_new, newIncidents, accountId);
        addSection(R.string.so_section_opened, openedRecently, accountId);
        addSection(R.string.so_section_past, pastIncidents, accountId);
        notifyDataSetChanged();
    }

    private void addSection(int titleRes, List<ReviewReport> reports, int accountId) {
        if (reports.isEmpty()) return;
        rows.add(new Row(titleRes));
        for (ReviewReport report : reports) {
            rows.add(new Row(report, SafetyOfficerIncidentState.wasOpened(
                    context, accountId, report.getReportId())));
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SECTION_ROW) {
            View heading = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_incident_section, parent, false);
            return new SectionViewHolder(heading);
        }
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review_queue, parent, false);
        return new ReportViewHolder(row);
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).report == null ? SECTION_ROW : REPORT_ROW;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        Row row = rows.get(position);
        if (viewHolder instanceof SectionViewHolder) {
            ((SectionViewHolder) viewHolder).title.setText(row.sectionTitle);
            return;
        }
        ReportViewHolder holder = (ReportViewHolder) viewHolder;
        ReviewReport report = row.report;
        String type = typeLabel(holder.itemView, report.getType());
        String title = report.getReferenceNumber() == null || report.getReferenceNumber().trim().isEmpty()
                ? report.getReportId() : report.getReferenceNumber().trim();
        if (safetyOfficerMode && title.length() > 20) {
            title = title.substring(0, Math.min(4, title.length())) + "…"
                    + title.substring(title.length() - 8);
        }
        String location = report.getLocationName() == null || report.getLocationName().trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.report_value_unavailable)
                : report.getLocationName().trim();
        long displayTime = safetyOfficerMode && report.getOccurredAtEpochMillis() > 0
                ? report.getOccurredAtEpochMillis() : report.getSubmittedAtEpochMillis();
        String created = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(displayTime));

        holder.typeIcon.setImageResource(typeIcon(report.getType()));
        holder.title.setText(title);
        holder.location.setText(holder.itemView.getContext().getString(
                R.string.review_queue_location_value, location));
        holder.created.setText(created);
        boolean approvedIncident = safetyOfficerMode
                && "APPROVED".equalsIgnoreCase(report.getStatus());
        holder.status.setText(approvedIncident ? R.string.so_incident_approved
                : safetyOfficerMode ? R.string.so_incident_reported
                : R.string.status_pending_review);
        int color = approvedIncident ? R.color.safety_success : R.color.safety_status;
        holder.status.setTextColor(holder.itemView.getContext().getColor(color));
        holder.status.setCompoundDrawableTintList(ColorStateList.valueOf(
                holder.itemView.getContext().getColor(color)));
        holder.status.setCompoundDrawablesRelativeWithIntrinsicBounds(
                approvedIncident ? R.drawable.ic_ms_check_circle : R.drawable.ic_ms_schedule,
                0, 0, 0);
        holder.opened.setVisibility(safetyOfficerMode && row.opened ? View.VISIBLE : View.GONE);
        holder.itemView.setBackgroundResource(safetyOfficerMode && row.opened
                ? R.drawable.incident_opened_card : R.drawable.outline_button);
        holder.itemView.setContentDescription(safetyOfficerMode
                ? holder.itemView.getContext().getString(R.string.so_incident_item_accessibility,
                        type, location, created,
                        holder.status.getText() + (row.opened
                                ? ". " + holder.itemView.getContext().getString(R.string.so_opened_label) : ""))
                : holder.itemView.getContext().getString(R.string.review_queue_item_accessibility,
                        type, location, created));
        holder.itemView.setOnClickListener(v -> listener.onReportSelected(report));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String typeLabel(View view, String typeCode) {
        if (ReportValues.HAZARD.equalsIgnoreCase(typeCode)) return view.getContext().getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equalsIgnoreCase(typeCode)) return view.getContext().getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equalsIgnoreCase(typeCode)) return view.getContext().getString(R.string.report_type_near_miss);
        return view.getContext().getString(R.string.report_type_not_selected);
    }

    private int typeIcon(String typeCode) {
        if (ReportValues.HAZARD.equalsIgnoreCase(typeCode)) return R.drawable.ic_ms_warning;
        if (ReportValues.INCIDENT.equalsIgnoreCase(typeCode)) return R.drawable.ic_ms_personal_injury;
        if (ReportValues.NEAR_MISS.equalsIgnoreCase(typeCode)) return R.drawable.ic_ms_visibility;
        return R.drawable.ic_ms_description;
    }

    static final class ReportViewHolder extends RecyclerView.ViewHolder {
        final ImageView typeIcon;
        final TextView title;
        final TextView location;
        final TextView created;
        final TextView status;
        final TextView opened;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.imgReportType);
            title = itemView.findViewById(R.id.tvReportName);
            location = itemView.findViewById(R.id.tvReportLocation);
            created = itemView.findViewById(R.id.tvReportDate);
            status = itemView.findViewById(R.id.tvReportQueueStatus);
            opened = itemView.findViewById(R.id.tvIncidentOpened);
        }
    }

    static final class SectionViewHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvIncidentSection);
        }
    }

    private static final class Row {
        final ReviewReport report;
        final int sectionTitle;
        final boolean opened;

        Row(ReviewReport report, boolean opened) {
            this.report = report;
            this.sectionTitle = 0;
            this.opened = opened;
        }

        Row(int sectionTitle) {
            this.report = null;
            this.sectionTitle = sectionTitle;
            this.opened = false;
        }
    }
}
