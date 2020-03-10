package edu.harvard.iq.dataverse.ingest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.junit.Test;

import edu.harvard.iq.dataverse.UnitTestUtils;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataIngest;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataTable;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.VariableCategory;

public class IngestFrequencyTest {

    private IngestServiceBean ingestService = new IngestServiceBean();

    @Test
    /**
     * Test calculation of frequencies during ingest
     */

    public void testFrequency()  {
        DataFile dataFile = readFileCalcFreq("sav/frequency-test.sav" , "application/x-spss-sav" );

        assertNotNull(dataFile);

        long varQuant = dataFile.getDataTable().getVarQuantity();
        assertEquals(varQuant, 3);

        Collection<VariableCategory> cats1 = dataFile.getDataTable().getDataVariables().get(0).getCategories();
        assertEquals(cats1.size(),2);
        firstVariableTest(cats1);

        Collection<VariableCategory> cats2 = dataFile.getDataTable().getDataVariables().get(1).getCategories();
        assertEquals(cats2.size(),4);
        secondVariableTest(cats2);

        Collection<VariableCategory> cats3 = dataFile.getDataTable().getDataVariables().get(2).getCategories();
        assertEquals(cats3.size(),2);
        thirdVariableTest(cats3);

        DataFile dataFileDta = readFileCalcFreq("dta/test_cat_values.dta" , "application/x-stata-14");
        assertNotNull(dataFileDta);

        long varQuantDta = dataFileDta.getDataTable().getVarQuantity();
        assertEquals(varQuantDta, 1);

        Collection<VariableCategory> cats = dataFileDta.getDataTable().getDataVariables().get(0).getCategories();
        assertEquals(cats.size(),2);
        dtaVariableTest(cats);

        return;
    }

    private void dtaVariableTest(Collection<VariableCategory> cats) {
        for (VariableCategory cat : cats) {
            double freq = cat.getFrequency();
            switch (cat.getLabel()) {
                case "Urban":
                    assertEquals((int) 6, (int) freq);
                    break;
                case "Rural":
                    assertEquals((int) 4, (int)freq);
                    break;
                default:
                    System.out.println("Thire is no such category label " + cat.getLabel());
                    assertEquals(0,1);
            }
        }
    }

    private DataFile readFileCalcFreq(String fileName, String type ) {

        BufferedInputStream fileInputStream = null;

        try {
            InputStream inputStream = new ByteArrayInputStream(UnitTestUtils.readFileToByteArray(fileName));
            fileInputStream = new BufferedInputStream(inputStream);
        } catch (IOException notfoundEx) {
            System.out.println("Cannot find file " + fileName);
            fileInputStream = null;
            assertNotNull(fileInputStream);
        }

        TabularDataFileReader ingestPlugin = ingestService.getTabDataReaderByMimeType(type);
        assertNotNull(ingestPlugin);

        TabularDataIngest tabDataIngest = null;

        try {
            tabDataIngest = ingestPlugin.read(fileInputStream, null);
        } catch (IOException ingestEx) {
            tabDataIngest = null;
            System.out.println("Caught an exception trying to ingest file " + fileName + ": " + ingestEx.getLocalizedMessage());
            assertNotNull(tabDataIngest);
        }

        File tabFile = tabDataIngest.getTabDelimitedFile();

        assertNotNull(tabDataIngest.getDataTable());
        assertNotNull(tabFile);
        assertNotNull(tabFile.exists());


        DataTable dataTable = tabDataIngest.getDataTable();
        DataFile dataFile = new DataFile();

        dataFile.setDataTable(dataTable);
        dataTable.setDataFile(dataFile);

        try {
            ingestService.produceFrequencyStatistics(dataFile, tabFile);
            return dataFile;
        } catch (IOException ioex) {
            System.out.println("Caught exception during  produceFrequencyStatistics with " + ioex.getMessage());
            assertEquals(0, 1);
            return null;
        }
    }



    private void firstVariableTest(Collection<VariableCategory> cats) {
        for (VariableCategory cat : cats) {
            double freq = cat.getFrequency();
            switch (cat.getLabel()) {
                case "Male":
                    assertEquals((int) 1537, (int) freq);
                    break;
                case "Female":
                    assertEquals((int) 1508, (int)freq);
                    break;
                default:
                    System.out.println("Thire is no such category label " + cat.getLabel());
                    assertEquals(0,1);
            }
        }
    }

    private void secondVariableTest(Collection<VariableCategory> cats) {
        for (VariableCategory cat : cats) {
            double freq = cat.getFrequency();
            switch (cat.getValue()) {
                case "1":
                    assertEquals((int) 0, (int) freq);
                    break;
                case "2":
                    assertEquals((int) 691, (int)freq);
                    break;
                case "3":
                    assertEquals((int) 1262, (int)freq);
                    break;
                case "4":
                    assertEquals((int) 1092, (int)freq);
                    break;
                default:
                    System.out.println("There is no such category value " + cat.getValue());
                    assertEquals(0,1);

            }
        }
    }

    private void thirdVariableTest(Collection<VariableCategory> cats) {
        for (VariableCategory cat : cats) {
            String c = cat.getValue();

            double freq = cat.getFrequency();
            switch (c) {
                case "1":
                    assertEquals((int) 2497, (int) freq);
                    break;
                case "2":
                    assertEquals((int) 548, (int)freq);
                    break;
                default:
                    System.out.println("There is no such category value " + cat.getValue());
                    assertEquals(0,1);

            }
        }
    }

}
