package edu.harvard.iq.dataverse.metrics;

import edu.harvard.iq.dataverse.common.BundleUtil;
import org.primefaces.model.chart.Axis;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.BarChartModel;
import org.primefaces.model.chart.ChartSeries;

import javax.ejb.Stateless;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Stateless
public class ChartCreator {

    public BarChartModel createYearlyChart(List<ChartMetrics> metrics, String chartType, String chartMode) {
        List<ChartMetrics> yearlyMetrics =
                MetricsUtil.countMetricsPerYearAndFillMissingYears(metrics);

        if (yearlyMetrics.isEmpty()) {
            yearlyMetrics.add(new ChartMetrics((double) LocalDateTime.now().getYear(), 0L));
        }

        String xLabel = BundleUtil.getStringFromBundle("metrics.year");
        String yLabel = BundleUtil.getStringFromBundle("metrics.chart.legend.label." + chartType);
        String title = BundleUtil.getStringFromBundle("metrics.chart.title." + chartType);

        return createBarModel(yearlyMetrics, title, xLabel, yLabel, chartMode);
    }

    public BarChartModel createMonthlyChart(List<ChartMetrics> metrics, int year, String chartType, String chartMode) {
        List<ChartMetrics> monthlyChartStats =
                MetricsUtil.fillMissingMonthsForMetrics(metrics, year);

        String xLabel = BundleUtil.getStringFromBundle("metrics.month");
        String yLabel = BundleUtil.getStringFromBundle("metrics.chart.legend.label." + chartType);
        String title = BundleUtil.getStringFromBundle("metrics.chart.title." + chartType);

        return createBarModel(monthlyChartStats, title, xLabel, yLabel, chartMode);
    }

    BarChartModel initBarModel(List<ChartMetrics> metrics, String columnLabel, String chartMode) {
        BarChartModel model = new BarChartModel();
        ChartSeries chartSeries = new ChartSeries();
        chartSeries.setLabel(columnLabel);

        if (chartMode.equals("YEAR")) {
            metrics.forEach(metric ->
                    chartSeries.set(metric.getYear(), metric.getCount()));
        } else if (chartMode.equals("MONTH")) {
            metrics.forEach(metric ->
                    chartSeries.set(BundleUtil.getStringFromBundle("metrics.month-" + metric.getMonth()),
                            metric.getCount()));
        }
        model.addSeries(chartSeries);

        return model;
    }

    public BarChartModel createBarModel(List<ChartMetrics> metrics,
                                        String title,
                                        String xAxisLabel,
                                        String yAxisLabel,
                                        String chartMode) {

        BarChartModel model = initBarModel(metrics, yAxisLabel, chartMode);

        model.setTitle(title);
        model.setLegendPosition("ne");

        Axis xAxis = model.getAxis(AxisType.X);
        xAxis.setLabel(xAxisLabel);

        Axis yAxis = model.getAxis(AxisType.Y);
        yAxis.setLabel(yAxisLabel);
        yAxis.setMin(0);
        yAxis.setTickFormat("%d");

        Long maxCountMetric = calculateMaxCountMetric(metrics);

        yAxis.setTickCount(Math.toIntExact(retrieveTickForMaxDatasetCountValue(maxCountMetric)));
        yAxis.setMax(maxCountMetric);
        return model;
    }

    private Long calculateMaxCountMetric(List<ChartMetrics> metrics) {
        return metrics.stream()
                    .max(Comparator.comparingLong(ChartMetrics::getCount))
                    .map(ChartMetrics::getCount)
                    .orElse(0L);
    }

    private long retrieveTickForMaxDatasetCountValue(Long maxCountValue) {
        return maxCountValue > 0 && maxCountValue < 4 ?
                maxCountValue + 1 : 5;
    }
}