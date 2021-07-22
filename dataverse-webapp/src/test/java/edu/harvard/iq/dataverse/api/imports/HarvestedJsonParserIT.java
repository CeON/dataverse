package edu.harvard.iq.dataverse.api.imports;

import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class HarvestedJsonParserIT extends WebappArquillianDeployment {

    @Inject
    private HarvestedJsonParser harvestedJsonParser;

    @Test
    public void parseDataset() throws IOException, JsonParseException {
        //given
        final String harvestedDataset = IOUtils.toString(HarvestedJsonParserIT.class.getClassLoader().getResource("json/import/harvestedDataset.json"), StandardCharsets.UTF_8);

        //when
        final Dataset dataset = harvestedJsonParser.parseDataset(harvestedDataset);

        //then
        System.out.println(dataset);

    }
}