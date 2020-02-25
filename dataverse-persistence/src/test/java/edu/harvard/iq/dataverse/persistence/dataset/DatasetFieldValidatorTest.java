/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.persistence.dataset;

import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author skraffmi
 */
public class DatasetFieldValidatorTest {

    public DatasetFieldValidatorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testIsValid() {

        //given
        DatasetField df = new DatasetField();
        DatasetVersion datasetVersion = new DatasetVersion();
        Dataset dataset = new Dataset();
        Dataverse dataverse = new Dataverse();
        dataset.setOwner(dataverse);
        datasetVersion.setDataset(dataset);
        DatasetFieldType dft = new DatasetFieldType();
        dft.setFieldType(FieldType.TEXT);
        //Test Text against regular expression that takes a 5 character string
        dft.setValidationFormat("^[a-zA-Z ]{5,5}$");
        df.setDatasetFieldType(dft);
        df.setFieldValue("asdfg");
        df.setDatasetVersion(datasetVersion);

        final ConstraintValidatorContext ctx =
                Mockito.mock(ConstraintValidatorContext.class);

        //when & then
        DatasetFieldValidator instance = new DatasetFieldValidator();
        boolean expResult = true;
        boolean result = instance.isValid(df, ctx);
        assertEquals(expResult, result);

        //Fill in a value - should be valid now....
        df.setFieldValue("value");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        //if not required - can be blank
        dft.setRequired(false);
        df.setFieldValue("");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        //Make string too long - should fail.
        df.setFieldValue("asdfgX");
        result = instance.isValid(df, ctx);
        assertEquals(false, result);

        //Make string too long - should fail.
        df.setFieldValue("asdf");
        result = instance.isValid(df, ctx);
        assertEquals(false, result);

        //Now lets try Dates
        dft.setFieldType(FieldType.DATE);
        dft.setValidationFormat(null);
        df.setFieldValue("1999AD");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("44BCE");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("2004-10-27");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("2002-08");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("[1999?]");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("Blergh");
        result = instance.isValid(df, ctx);
        assertEquals(false, result);

        //Float
        dft.setFieldType(FieldType.FLOAT);
        df.setFieldValue("44");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("44 1/2");
        result = instance.isValid(df, ctx);
        assertEquals(false, result);

        //Integer
        dft.setFieldType(FieldType.INT);
        df.setFieldValue("44");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("-44");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);

        df.setFieldValue("12.14");
        result = instance.isValid(df, ctx);
        assertEquals(false, result);

        //URL
        dft.setFieldType(FieldType.URL);
        df.setFieldValue("http://cnn.com");
        result = instance.isValid(df, ctx);
        assertEquals(true, result);


        df.setFieldValue("espn.com");
        result = instance.isValid(df, ctx);
        assertEquals(false, result);

    }

    @Test
    public void testIsValidAuthorIdentifierOrcid() {
        //given
        DatasetFieldValidator validator = new DatasetFieldValidator();
        Pattern pattern = DatasetAuthor.getValidPattern(DatasetAuthor.REGEX_ORCID);

        //when & then
        assertTrue(validator.isValidAuthorIdentifier("0000-0002-1825-0097", pattern));
        // An "X" at the end of an ORCID is less common but still valid.
        assertTrue(validator.isValidAuthorIdentifier("0000-0002-1694-233X", pattern));
        assertFalse(validator.isValidAuthorIdentifier("0000 0002 1825 0097", pattern));
        assertFalse(validator.isValidAuthorIdentifier(" 0000-0002-1825-0097", pattern));
        assertFalse(validator.isValidAuthorIdentifier("0000-0002-1825-0097 ", pattern));
        assertFalse(validator.isValidAuthorIdentifier("junk", pattern));
    }

    @Test
    public void testIsValidAuthorIdentifierIsni() {
        //given
        DatasetFieldValidator validator = new DatasetFieldValidator();
        Pattern pattern = DatasetAuthor.getValidPattern(DatasetAuthor.REGEX_ISNI);

        //when & then
        assertTrue(validator.isValidAuthorIdentifier("0000000121032683", pattern));
        assertFalse(validator.isValidAuthorIdentifier("junk", pattern));
    }

    @Test
    public void testIsValidAuthorIdentifierLcna() {
        //given
        DatasetFieldValidator validator = new DatasetFieldValidator();
        Pattern pattern = DatasetAuthor.getValidPattern(DatasetAuthor.REGEX_LCNA);

        //when & then
        assertTrue(validator.isValidAuthorIdentifier("n82058243", pattern));
        assertTrue(validator.isValidAuthorIdentifier("foobar123", pattern));
        assertFalse(validator.isValidAuthorIdentifier("junk", pattern));
    }

    @Test
    public void testIsValidAuthorIdentifierViaf() {
        //given
        DatasetFieldValidator validator = new DatasetFieldValidator();
        Pattern pattern = DatasetAuthor.getValidPattern(DatasetAuthor.REGEX_VIAF);

        //when & then
        assertTrue(validator.isValidAuthorIdentifier("172389567", pattern));
        assertFalse(validator.isValidAuthorIdentifier("junk", pattern));
    }

    @Test
    public void testIsValidAuthorIdentifierGnd() {
        //given
        DatasetFieldValidator validator = new DatasetFieldValidator();
        Pattern pattern = DatasetAuthor.getValidPattern(DatasetAuthor.REGEX_GND);

        //when & then
        assertTrue(validator.isValidAuthorIdentifier("4079154-3", pattern));
        assertFalse(validator.isValidAuthorIdentifier("junk", pattern));
    }

}
