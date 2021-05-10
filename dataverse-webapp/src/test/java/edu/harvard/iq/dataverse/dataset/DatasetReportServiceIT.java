package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.assertj.core.api.Assertions;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class DatasetReportServiceIT extends WebappArquillianDeployment {
    private static final Logger logger = LoggerFactory.getLogger(DatasetReportServiceIT.class);

    @Inject
    private DatasetReportService service;

    // -------------------- TESTS --------------------

    @Test
    public void createReport() throws IOException {

        // given & when
        String output;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            service.createReport(outputStream);
            output = outputStream.toString();
        }

        // then
        StringReader stringReader = new StringReader(output);
        List<CSVRecord> records = CSVFormat.DEFAULT
                .withHeader()
                .withSkipHeaderRecord()
                .parse(stringReader)
                .getRecords();

        assertThat(records)
                .extracting(r -> r.get(0), r -> r.get(1), CSVRecord::size)
                .containsExactly(
                        tuple("testfile6.zip", "53", 21),
                        tuple("testfile1.zip", "55", 21),
                        tuple("restricted.zip", "58", 21));
    }
}