package edu.harvard.iq.dataverse.metrics;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.primefaces.model.chart.BarChartModel;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ViewScoped
@Named("PublishedDatasetsChart")
public class PublishedDatasetsChart implements Serializable {

    private ChartCreator chartCreator;
    private MetricsServiceBean metricsService;

    private final String CHART_TYPE = "datasets";

    private BarChartModel chartModel;
    private List<ChartMetrics> yearlyStats = new ArrayList<>();
    private List<ChartMetrics> chartMetrics = new ArrayList<>();

    private List<Pair<String, String>> tableData = new ArrayList<>();

    private String mode = "YEAR_CUMULATIVE";
    private int selectedYear;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public PublishedDatasetsChart() {
    }

    @Inject
    public PublishedDatasetsChart(ChartCreator chartCreator, MetricsServiceBean metricsService) {
        this.chartCreator = chartCreator;
        this.metricsService = metricsService;
    }

    // -------------------- GETTERS --------------------
    public BarChartModel getChartModel() {
        return chartModel;
    }

    public List<ChartMetrics> getYearlyStats() {
        return yearlyStats;
    }

    public String getMode() {
        return mode;
    }

    public int getSelectedYear() {
        return selectedYear;
    }
    public List<Pair<String, String>> getTableData() {
        return tableData;
    }


    // -------------------- LOGIC --------------------
    public void init() {
        chartMetrics = metricsService.countPublishedDatasets();

        if (chartMetrics.isEmpty()) {
            yearlyStats.add(new ChartMetrics((double) LocalDateTime.now().getYear(), 0L));
            selectedYear = LocalDate.now().getYear();
        } else {
            yearlyStats = MetricsUtil.countMetricsPerYearAndFillMissingYearsDescending(chartMetrics);
            selectedYear = yearlyStats.get(0).getYear();
        }

        chartModel = chartCreator.createYearlyCumulativeChart(metricsService.countPublishedDatasets(), CHART_TYPE);
        tableData = loadDataForChartTable(chartModel);
    }

    public void changeDatasetMetricsModel() {
        if (isYearlyChartSelected()) {
            chartModel = chartCreator.createYearlyChart(chartMetrics, CHART_TYPE);
        } else if (isYearlyCumulativeChartSelected()) {
            chartModel = chartCreator.createYearlyCumulativeChart(chartMetrics, CHART_TYPE);
        } else if (isMonthlyChartSelected()) {
            chartModel = chartCreator.createMonthlyChart(chartMetrics, selectedYear, CHART_TYPE);
        }
        tableData = loadDataForChartTable(chartModel);
    }

    // -------------------- PRIVATE ---------------------
    private boolean isMonthlyChartSelected() {
        return selectedYear != 0;
    }

    private boolean isYearlyChartSelected() {
        return mode.equals("YEAR");
    }

    private boolean isYearlyCumulativeChartSelected() {
        return mode.equals("YEAR_CUMULATIVE");
    }

    private List<Pair<String, String>> loadDataForChartTable(BarChartModel chartModel) {
//        List<Pair<String, String>> tableData = new ArrayList<>();
//        for(Map.Entry<Object, Number> entry : chartModel.getSeries().get(0).getData().entrySet()) {
//            Pair<String, String> row = new ImmutablePair<>(entry.getKey().toString(), entry.getValue().toString());
//            tableData.add(row);
//        }
        return chartModel.getSeries().get(0).getData().entrySet().stream()
                .map(entry -> new ImmutablePair<>(entry.getKey().toString(), entry.getValue().toString()))
                .collect(Collectors.toList());

    }

    // -------------------- SETTERS --------------------
    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setSelectedYear(int selectedYear) {
        this.selectedYear = selectedYear;
    }

    public void setYearlyStats(List<ChartMetrics> yearlyStats) {
        this.yearlyStats = yearlyStats;
    }

    public void setTableData(List<Pair<String, String>> tableData) {
        this.tableData = tableData;
    }
}
