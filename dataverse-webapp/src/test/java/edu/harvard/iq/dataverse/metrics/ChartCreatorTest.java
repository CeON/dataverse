package edu.harvard.iq.dataverse.metrics;

import org.junit.Test;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.BarChartModel;
import org.primefaces.model.chart.ChartSeries;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChartCreatorTest {

    @Test
    public void verifyIfChartBarModelCorrectlyDistributesDatasets() {
        //given
        ChartCreator chartCreator = new ChartCreator();
        List<ChartMetrics> chartMetrics = generateSampleDatasetsMetrics();
        //when
        BarChartModel barChartModel = chartCreator.initBarModel(chartMetrics, "yAxisLabel", "YEAR");
        BarChartModel createdModel = chartCreator.createBarModel(chartMetrics, "chartTitle","xAxisLabel", "yAxisLabel",  "YEAR");
        //then
        assertEquals(78L, getMaximumYaxisHeight(createdModel));
        assertEquals(7, getYearValueFromModel(createdModel, 2018));
        assertEquals(78, getYearValueFromModel(createdModel, 2019));
        assertEquals(8, getYearValueFromModel(createdModel, 2020));
    }

    private Object getMaximumYaxisHeight(BarChartModel createdModel) {
        return createdModel.getAxis(AxisType.Y).getMax();
    }

    private int getYearValueFromModel(BarChartModel createdModel, int year) {
        ChartSeries chartSeries = createdModel.getSeries().get(0);
        Number number = chartSeries.getData().get(year);
        return number.intValue();
    }

    private List<ChartMetrics> generateSampleDatasetsMetrics() {
        return Arrays.asList(
                new ChartMetrics(2018.0, (double) 4, 7L),
                new ChartMetrics(2019.0, (double) 1, 78L),
                new ChartMetrics(2020.0, (double) 12, 8L)
        );
    }
}
