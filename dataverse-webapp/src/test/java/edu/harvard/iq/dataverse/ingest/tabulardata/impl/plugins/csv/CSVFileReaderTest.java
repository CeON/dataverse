package edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.csv;

import edu.harvard.iq.dataverse.dataaccess.TabularSubsetGenerator;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataIngest;
import edu.harvard.iq.dataverse.persistence.datafile.DataTable;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable.VariableInterval;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable.VariableType;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestError;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestException;
import io.vavr.Tuple;
import org.dataverse.unf.UNFUtil;
import org.dataverse.unf.UnfException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


/** @author oscardssmith */
public class CSVFileReaderTest {

    /**
     * Test CSVFileReader with a hellish CSV containing everything nasty I could
     * think of to throw at it.
     */
    @Test
    void testRead() throws IOException, URISyntaxException {
        final String[] expectedResult = {
                "-199	\"hello\"	2013-04-08 13:14:23	2013-04-08 13:14:23	2017-06-20	\"2017/06/20\"	0.0	1	\"2\"	\"823478788778713\"",
                "2	\"Sdfwer\"	2013-04-08 13:14:23	2013-04-08 13:14:23	2017-06-20	\"1100/06/20\"	Inf	2	\"NaN\"	\",1,2,3\"",
                "0	\"cjlajfo.\"	2013-04-08 13:14:23	2013-04-08 13:14:23	2017-06-20	\"3000/06/20\"	-Inf	3	\"inf\"	\"\\casdf\"",
                "-1	\"Mywer\"	2013-04-08 13:14:23	2013-04-08 13:14:23	2017-06-20	\"06-20-2011\"	3.141592653	4	\"4.8\"	\"　 \\\"  \"",
                "266128	\"Sf\"	2013-04-08 13:14:23	2013-04-08 13:14:23	2017-06-20	\"06-20-1917\"	0	5	\"Inf+11\"	\"\"",
                "0	\"null\"	2013-04-08 13:14:23	2013-04-08 13:14:23	2017-06-20	\"03/03/1817\"	123	6.000001	\"11-2\"	\"\\\"adf\\0\\na\\td\\nsf\\\"\"",
                "-2389	\"\"	2013-04-08 13:14:23	2013-04-08 13:14:72	2017-06-20	\"2017-03-12\"	NaN	2	\"nap\"	\"💩⌛👩🏻■\""};
        BufferedReader result;
        File file = getFile("csv/ingest/IngestCSV.csv");
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream stream = new BufferedInputStream(fileInputStream)) {
            CSVFileReader instance = createInstance();
            File outFile = instance.read(Tuple.of(stream, file), null).getTabDelimitedFile();
            result = new BufferedReader(new FileReader(outFile));
        }

        assertThat(result).isNotNull();
        assertThat(result.lines().collect(Collectors.toList())).isEqualTo(Arrays.asList(expectedResult));
    }

    /*
     * This test will read the CSV File From Hell, above, then will inspect
     * the DataTable object produced by the plugin, and verify that the
     * individual DataVariables have been properly typed.
     */
    @Test
    void testVariables() throws IOException {
        String[] expectedVariableNames = {"ints", "Strings", "Times", "Not quite Times", "Dates", "Not quite Dates",
                "Numbers", "Not quite Ints", "Not quite Numbers", "Column that hates you, contains many comas, and is verbose and long enough that it would cause ingest to fail if ingest failed when a header was more than 256 characters long. Really, it's just sadistic.　Also to make matters worse, the space at the begining of this sentance was a special unicode space designed to make you angry."};

        VariableType[] expectedVariableTypes = {
                VariableType.NUMERIC, VariableType.CHARACTER,
                VariableType.CHARACTER, VariableType.CHARACTER, VariableType.CHARACTER, VariableType.CHARACTER,
                VariableType.NUMERIC, VariableType.NUMERIC, VariableType.CHARACTER, VariableType.CHARACTER
        };

        VariableInterval[] expectedVariableIntervals = {
                VariableInterval.DISCRETE, VariableInterval.DISCRETE,
                VariableInterval.DISCRETE, VariableInterval.DISCRETE, VariableInterval.DISCRETE, VariableInterval.DISCRETE,
                VariableInterval.CONTINUOUS, VariableInterval.CONTINUOUS, VariableInterval.DISCRETE, VariableInterval.DISCRETE
        };

        String[] expectedVariableFormatCategories = { null, null, "time", "time", "date", null, null, null, null, null };

        String[] expectedVariableFormats = { null, null, "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", null, null, null, null, null };

        Long expectedNumberOfCases = 7L; // aka the number of lines in the TAB file produced by the ingest plugin

        File file = getFile("csv/ingest/IngestCSV.csv");
        DataTable result;
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            CSVFileReader instance = createInstance();
            result = instance.read(Tuple.of(stream, file), null).getDataTable();
        }

        assertThat(result).isNotNull();
        assertThat(result.getDataVariables()).isNotNull();
        assertThat(result.getVarQuantity()).isEqualTo((long) result.getDataVariables().size());
        assertThat(result.getVarQuantity()).isEqualTo((long) expectedVariableTypes.length);
        assertThat(result.getCaseQuantity()).isEqualTo(expectedNumberOfCases);

        assertThat(result.getDataVariables()).extracting(DataVariable::getName).contains(expectedVariableNames);
        assertThat(result.getDataVariables()).extracting(DataVariable::getType).contains(expectedVariableTypes);
        assertThat(result.getDataVariables()).extracting(DataVariable::getInterval).contains(expectedVariableIntervals);
        assertThat(result.getDataVariables()).extracting(DataVariable::getFormatCategory).contains(expectedVariableFormatCategories);
        assertThat(result.getDataVariables()).extracting(DataVariable::getFormat).contains(expectedVariableFormats);
    }

    /*
     * This test will read a CSV file, then attempt to subset
     * the resulting tab-delimited file and verify that the individual variable vectors
     * are legit.
     */
    @Test
    void testSubset() throws IOException {
        Long expectedNumberOfVariables = 13L;
        Long expectedNumberOfCases = 24L; // aka the number of lines in the TAB file produced by the ingest plugin

        TabularDataIngest ingestResult;
        File file = getFile("csv/ingest/election_precincts.csv");
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            CSVFileReader instance = createInstance();
            ingestResult = instance.read(Tuple.of(stream, file), null);
        }

        File generatedTabFile = ingestResult.getTabDelimitedFile();
        DataTable generatedDataTable = ingestResult.getDataTable();

        assertThat(generatedDataTable).isNotNull();
        assertThat(generatedDataTable.getDataVariables()).isNotNull();
        assertThat(generatedDataTable.getVarQuantity()).isEqualTo((long) generatedDataTable.getDataVariables().size());
        assertThat(generatedDataTable.getVarQuantity()).isEqualTo(expectedNumberOfVariables);
        assertThat(generatedDataTable.getCaseQuantity()).isEqualTo(expectedNumberOfCases);

        // And now let's try and subset the individual vectors
        // First, the "continuous" vectors (we should be able to read these as Double[]):
        int[] floatColumns = {2};

        Double[][] floatVectors = {
                { 1.0, 3.0, 4.0, 6.0, 7.0, 8.0, 11.0, 12.0, 76.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0, 77.0 },
        };

        int vectorCount = 0;
        for (int i : floatColumns) {
            // We'll be subsetting the column vectors one by one, re-opening the
            // file each time. Inefficient - but we don't care here.

            if (!generatedDataTable.getDataVariables().get(i).isIntervalContinuous()) {
                fail("Column " + i + " was not properly processed as \"continuous\"");
            }
            String[][] table = TabularSubsetGenerator.readFileIntoTable(generatedDataTable, generatedTabFile);

            Double[] columnVector =
                    TabularSubsetGenerator.subsetDoubleVector(table, i, generatedDataTable.getCaseQuantity().intValue());

            assertThat(columnVector).isEqualTo(floatVectors[vectorCount++]);
        }

        // Discrete Numerics (aka, integers):
        int[] integerColumns = { 1, 4, 6, 7, 8, 9, 10, 11, 12 };

        Long[][] longVectors = {
                { 1L, 3L, 4L, 6L, 7L, 8L, 11L, 12L, 76L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L, 77L },
                { 1L, 2L, 3L, 4L, 5L, 11L, 13L, 15L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L, 19L },
                { 85729227L, 85699791L, 640323976L, 85695847L, 637089796L, 637089973L, 85695001L, 85695077L, 1111111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L },
                { 205871733L, 205871735L, 205871283L, 258627915L, 257444575L, 205871930L, 260047422L, 262439738L, 1111111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L },
                { 205871673L, 205871730L, 205871733L, 205872857L, 258627915L, 257444584L, 205873413L, 262439738L, 1111111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L },
                { 25025000201L, 25025081001L, 25025000701L, 25025050901L, 25025040600L, 25025000502L, 25025040401L, 25025100900L, 1111111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L },
                { 250250502002L, 250250502003L, 250250501013L, 250250408011L, 250250503001L, 250250103001L, 250250406002L, 250250406001L, 1111111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L },
                { 250251011024001L, 250251011013003L, 250251304041007L, 250251011013006L, 250251010016000L, 250251011024002L, 250251001005004L, 250251002003002L, 1111111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L },
                { 2109L, 2110L, 2111L, 2120L, 2121L, 2115L, 2116L, 2122L, 11111L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L, 4444444L }
        };

        vectorCount = 0;

        for (int i : integerColumns) {
            if (!generatedDataTable.getDataVariables().get(i).isIntervalDiscrete()
                    || !generatedDataTable.getDataVariables().get(i).isTypeNumeric()) {
                fail("Column " + i + " was not properly processed as \"discrete numeric\"");
            }
            String[][] table = TabularSubsetGenerator.readFileIntoTable(generatedDataTable, generatedTabFile);
            Long[] columnVector =
                    TabularSubsetGenerator.subsetLongVector(table, i, generatedDataTable.getCaseQuantity().intValue());

            assertThat(columnVector).isEqualTo(longVectors[vectorCount++]);
        }

        // And finally, Strings:
        int[] stringColumns = {0, 3, 5};

        String[][] stringVectors = {
                { "Dog", "Squirrel", "Antelope", "Zebra", "Lion", "Gazelle", "Cat", "Giraffe", "Cat", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey", "Donkey" },
                { "East Boston", "Charlestown", "South Boston", "Bronx", "Roslindale", "Mission Hill", "Jamaica Plain", "Hyde Park", "Fenway/Kenmore", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens", "Queens" },
                { "2-06", "1-09", "1-1A", "1-1B", "2-04", "3-05", "1-1C", "1-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", "41-10A", }
        };

        vectorCount = 0;

        for (int i : stringColumns) {
            if (!generatedDataTable.getDataVariables().get(i).isTypeCharacter()) {
                fail("Column " + i + " was not properly processed as a character vector");
            }
            String[][] table = TabularSubsetGenerator.readFileIntoTable(generatedDataTable, generatedTabFile);
            String[] columnVector =
                    TabularSubsetGenerator.subsetStringVector(table, i, generatedDataTable.getCaseQuantity().intValue());

            assertThat(columnVector).isEqualTo(stringVectors[vectorCount++]);
        }
    }

    /*
     * UNF test;
     * I'd like to use a file with more interesting values - "special" numbers, freaky dates, accents, etc.
     * for this. But checking it in with this simple file, for now.
     * (thinking about it, the "csv file from hell" may be a better test case for the UNF test)
     */
    @Test
    void testVariableUNFs() throws IOException, UnfException {
        long expectedNumberOfVariables = 13L;
        long expectedNumberOfCases = 24L; // aka the number of lines in the TAB file produced by the ingest plugin

        String[] expectedUNFs = {
                "UNF:6:wb7OATtNC/leh1sOP5IGDQ==",
                "UNF:6:0V3xQ3ea56rzKwvGt9KBCA==",
                "UNF:6:0V3xQ3ea56rzKwvGt9KBCA==",
                "UNF:6:H9inAvq5eiIHW6lpqjjKhQ==",
                "UNF:6:Bh0M6QvunZwW1VoTyioRCQ==",
                "UNF:6:o5VTaEYz+0Kudf6hQEEupQ==",
                "UNF:6:eJRvbDJkIeDPrfN2dYpRfA==",
                "UNF:6:JD1wrtM12E7evrJJ3bRFGA==",
                "UNF:6:xUKbK9hb5o0nL5/mYiy7Bw==",
                "UNF:6:Mvq3BrdzoNhjndMiVr92Ww==",
                "UNF:6:KkHM6Qlyv3QlUd+BKqqB3Q==",
                "UNF:6:EWUVuyXKSpyllsrjHnheig==",
                "UNF:6:ri9JsRJxM2xpWSIq17oWNw=="
        };

        TabularDataIngest ingestResult;
        File file = getFile("csv/ingest/election_precincts.csv");

        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            CSVFileReader instance = createInstance();
            ingestResult = instance.read(Tuple.of(stream, file), null);
        }

        File generatedTabFile = ingestResult.getTabDelimitedFile();
        DataTable generatedDataTable = ingestResult.getDataTable();

        assertThat(generatedDataTable).isNotNull();
        assertThat(generatedDataTable.getDataVariables()).isNotNull();
        assertThat(generatedDataTable.getVarQuantity()).isEqualTo((long) generatedDataTable.getDataVariables().size());
        assertThat(generatedDataTable.getVarQuantity()).isEqualTo(expectedNumberOfVariables);
        assertThat(generatedDataTable.getCaseQuantity()).isEqualTo(expectedNumberOfCases);
        String[][] table = TabularSubsetGenerator.readFileIntoTable(generatedDataTable, generatedTabFile);
        for (int i = 0; i < expectedNumberOfVariables; i++) {
            String unf = null;

            if (generatedDataTable.getDataVariables().get(i).isIntervalContinuous()) {
                Double[] columnVector =
                        TabularSubsetGenerator.subsetDoubleVector(table, i, generatedDataTable.getCaseQuantity().intValue());
                unf = UNFUtil.calculateUNF(columnVector);
            }
            if (generatedDataTable.getDataVariables().get(i).isIntervalDiscrete()
                    && generatedDataTable.getDataVariables().get(i).isTypeNumeric()) {
                Long[] columnVector =
                        TabularSubsetGenerator.subsetLongVector(table, i, generatedDataTable.getCaseQuantity().intValue());
                unf = UNFUtil.calculateUNF(columnVector);
            }
            if (generatedDataTable.getDataVariables().get(i).isTypeCharacter()) {
                String[] columnVector =
                        TabularSubsetGenerator.subsetStringVector(table, i, generatedDataTable.getCaseQuantity().intValue());
                String[] dateFormats = null;

                // Special handling for Character strings that encode dates and times:
                if ("time".equals(generatedDataTable.getDataVariables().get(i).getFormatCategory())
                        || "date".equals(generatedDataTable.getDataVariables().get(i).getFormatCategory())) {

                    dateFormats = new String[(int) expectedNumberOfCases];
                    for (int j = 0; j < expectedNumberOfCases; j++) {
                        dateFormats[j] = generatedDataTable.getDataVariables().get(i).getFormat();
                    }
                }
                unf = dateFormats == null ? UNFUtil.calculateUNF(columnVector) : UNFUtil.calculateUNF(columnVector, dateFormats);
            }

            assertThat(unf).isEqualTo(expectedUNFs[i]);
        }
    }

    @Test
    void selectEncoding() throws IOException {
        // given & when
        DataTable result;
        File file = getFile("csv/ingest/ISO8859-2.csv");
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            CSVFileReader instance = createInstance();
            instance.setDataLanguageEncoding("ISO-8859-2");
            result = instance.read(Tuple.of(stream, file), null).getDataTable();
        }

        // then
        assertThat(result.getDataVariables())
                .extracting(DataVariable::getName)
                .containsExactly("zażółć wiek", "gęślą płeć", "jaźń kolor oczu");
    }

    /**
     * Tests CSVFileReader with a CSV with one more column than header. Tests
     * CSVFileReader with a null CSV.
     */
    @Test
    void testBrokenCSV() throws IOException {
        try {
            createInstance().read(null, null);
            fail("IOException not thrown on null csv");
        } catch (NullPointerException ex) {
            assertThat(ex.getMessage()).isNull();
        } catch (IngestException ex) {
            assertThat(ex.getErrorKey()).isEqualTo(IngestError.UNKNOWN_ERROR);
        }
        File file = getFile("csv/ingest/BrokenCSV.csv");
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream stream = new BufferedInputStream(fileInputStream)) {
            createInstance().read(Tuple.of(stream, file), null);
            fail("IOException was not thrown when collumns do not align.");
        } catch (IngestException ex) {
            assertThat(ex.getErrorKey()).isEqualTo(IngestError.CSV_RECORD_MISMATCH);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            //                          File path | Expected column count | Expected cases
            "           csv/ingest/excel-type.csv |                    10 |              2",
            "csv/ingest/semicolons-one-column.csv |                     1 |              2"
    })
    void testCSVWithSemicolons(String filePath, long expectedColumnCount, long expectedCases) throws IOException {
        // given
        File file = getFile(filePath);
        TabularDataIngest ingestResult;
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            CSVFileReader instance = createInstance();

            // when
            ingestResult = instance.read(Tuple.of(stream, file), null);
        }

        // then
        DataTable dataTable = ingestResult.getDataTable();
        assertThat(dataTable.getVarQuantity()).isEqualTo(expectedColumnCount);
        assertThat(dataTable.getCaseQuantity()).isEqualTo(expectedCases);
    }

    // -------------------- PRIVATE --------------------

    private CSVFileReader createInstance() {
        return new CSVFileReader(new CSVFileReaderSpi(), ',');
    }

    private File getFile(String name) {
        try {
            return Paths.get(CSVFileReaderTest.class.getClassLoader().getResource(name).toURI()).toFile();
        } catch (URISyntaxException use) {
            throw new RuntimeException(use);
        }
    }
}
