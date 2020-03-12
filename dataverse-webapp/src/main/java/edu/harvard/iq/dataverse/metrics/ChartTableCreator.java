package edu.harvard.iq.dataverse.metrics;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.primefaces.model.chart.BarChartModel;

import javax.ejb.Stateless;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class ChartTableCreator {

    public ChartTableModel createChartTable(BarChartModel barChartModel, ChartMode chartMode) {
        ChartTableModel tableModel = new ChartTableModel();
        tableModel.setData(loadDataForChartTable(barChartModel));
        tableModel.setTitle(getChartTableTitle(barChartModel));
        tableModel.setLeftColumnName(getLeftColumnTitle(chartMode));
        tableModel.setRightColumnName("values");

        return tableModel;
    }

    private List<Pair<String, String>> loadDataForChartTable(BarChartModel chartModel) {
        return chartModel.getSeries().get(0).getData().entrySet().stream()
                .map(entry -> new ImmutablePair<>(entry.getKey().toString(), entry.getValue().toString()))
                .collect(Collectors.toList());

    }

    private String getChartTableTitle(BarChartModel barChartModel) {
        return barChartModel.getTitle();
    }

    private String getLeftColumnTitle(ChartMode chartMode) {
        return chartMode.getMode();
    }
}
