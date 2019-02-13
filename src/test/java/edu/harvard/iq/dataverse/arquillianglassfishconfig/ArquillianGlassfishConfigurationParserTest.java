package edu.harvard.iq.dataverse.arquillianglassfishconfig;

import io.vavr.control.Try;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ArquillianGlassfishConfigurationParserTest {

    @Test
    public void shouldSuccessfullyCreateTemporaryFile() {
        //given
        ArquillianGlassfishConfigurationParser parser = new ArquillianGlassfishConfigurationParser();

        //when
        parser.createTempGlassfishResources();

        //then
        Assert.assertTrue(Files.exists(Paths.get(ArquillianGlassfishConfigurationParser.NEW_RESOURCE_PATH)));
    }

    @AfterClass
    public static void removeTempGlassfishResource() {
        Try.of(() -> Files.deleteIfExists(Paths.get(ArquillianGlassfishConfigurationParser.NEW_RESOURCE_PATH)))
                .getOrElseThrow(throwable -> new RuntimeException("Unable to delete temporary glassfish resource", throwable));
    }
}
