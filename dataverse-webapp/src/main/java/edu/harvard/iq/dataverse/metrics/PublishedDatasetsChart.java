package edu.harvard.iq.dataverse.metrics;

import org.omnifaces.cdi.ViewScoped;
import org.primefaces.model.chart.BarChartModel;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ViewScoped
@Named("PublishedDatasetsChart")
public class PublishedDatasetsChart implements Serializable {

    private ChartCreator chartCreator;
    private ChartTableCreator chartTableCreator;
    private MetricsServiceBean metricsService;

    private static final String CHART_TYPE = "datasets";

    private BarChartModel chartModel;
    private ChartTableModel tableModel;
    private List<ChartMetrics> yearlyStats = new ArrayList<>();
    private List<ChartMetrics> chartMetrics = new ArrayList<>();

    private ChartMode mode;
    private int selectedYear;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public PublishedDatasetsChart() {
    }

    @Inject
    public PublishedDatasetsChart(ChartCreator chartCreator, MetricsServiceBean metricsService, ChartTableCreator chartTableCreator) {
        this.chartCreator = chartCreator;
        this.metricsService = metricsService;
        this.chartTableCreator = chartTableCreator;
    }

    // -------------------- GETTERS --------------------
    public BarChartModel getChartModel() {
        return chartModel;
    }

    public ChartTableModel getTableModel() {
        return tableModel;
    }

    public List<ChartMetrics> getYearlyStats() {
        return yearlyStats;
    }

    public String getMode() {
        return mode.toString();
    }

    public int getSelectedYear() {
        return selectedYear;
    }


    // -------------------- LOGIC --------------------
    public void init() {
        chartMetrics = metricsService.countPublishedDatasets();
        mode = ChartMode.CUMULATIVE;

        if (chartMetrics.isEmpty()) {
            yearlyStats.add(new ChartMetrics(LocalDateTime.now().getYear(), 0L));
            selectedYear = LocalDate.now().getYear();
        } else {
            yearlyStats = MetricsUtil.countMetricsPerYearAndFillMissingYearsDescending(chartMetrics);
            selectedYear = yearlyStats.get(0).getYear();
        }

        chartModel = chartCreator.createYearlyCumulativeChart(metricsService.countPublishedDatasets(), CHART_TYPE);
        tableModel = chartTableCreator.createChartTable(chartModel);
    }

    public void changeDatasetMetricsModel() {
        if (isYearlyChartSelected()) {
            chartModel = chartCreator.createYearlyChart(chartMetrics, CHART_TYPE);
            tableModel = chartTableCreator.createChartTable(chartModel);
        } else if (isYearlyCumulativeChartSelected()) {
            chartModel = chartCreator.createYearlyCumulativeChart(chartMetrics, CHART_TYPE);
            tableModel = chartTableCreator.createChartTable(chartModel);
        } else if (isMonthlyChartSelected()) {
            chartModel = chartCreator.createMonthlyChart(chartMetrics, selectedYear, CHART_TYPE);
            tableModel = chartTableCreator.createMonthlyChartTable(chartModel, selectedYear);
        }
    }

    // -------------------- PRIVATE ---------------------
    private boolean isMonthlyChartSelected() {
        return selectedYear != 0;
    }

    private boolean isYearlyChartSelected() {
        return mode == ChartMode.YEARLY;
    }

    private boolean isYearlyCumulativeChartSelected() {
        return mode == ChartMode.CUMULATIVE;
    }

    // -------------------- SETTERS --------------------
    public void setMode(String mode) {
        this.mode = ChartMode.of(mode);
    }

    public void setSelectedYear(int selectedYear) {
        this.selectedYear = selectedYear;
    }

    public void setYearlyStats(List<ChartMetrics> yearlyStats) {
        this.yearlyStats = yearlyStats;
    }
}
