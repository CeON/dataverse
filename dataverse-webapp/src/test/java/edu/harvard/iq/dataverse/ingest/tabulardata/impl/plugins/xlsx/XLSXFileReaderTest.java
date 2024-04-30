package edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.xlsx;

import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataIngest;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.csv.CSVFileReaderTest;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import io.vavr.Tuple;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class XLSXFileReaderTest {

    @ParameterizedTest
    @ValueSource(strings = { "xslx/table-google.xlsx", "xslx/table-libre.xlsx" })
    void read__various_sources(String xlsxFile) throws Exception {
        // when
        File file = getFile(xlsxFile);
        TabularDataIngest result;
        try (BufferedInputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            XLSXFileReader reader = new XLSXFileReader(new XLSXFileReaderSpi());
            result = reader.read(Tuple.of(is, file), null);
        }

        // then
        assertThat(result.getDataTable().getVarQuantity()).isEqualTo(5);
        assertThat(result.getDataTable().getDataVariables().stream().map(DataVariable::getName).collect(Collectors.toList()))
                .containsExactly("Id", "Item", "cost", "count", "total");
    }

    private File getFile(String name) {
        try {
            return Paths.get(CSVFileReaderTest.class.getClassLoader().getResource(name).toURI()).toFile();
        } catch (URISyntaxException use) {
            throw new RuntimeException(use);
        }
    }
}
