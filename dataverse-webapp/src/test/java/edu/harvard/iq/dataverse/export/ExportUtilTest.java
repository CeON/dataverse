package edu.harvard.iq.dataverse.export;

import org.junit.Assert;
import org.junit.Test;


public class ExportUtilTest {

    @Test
    public void testIsPerson() {
        Assert.assertTrue(ExportUtil.isPerson("Jan Kowalski"));
        Assert.assertTrue(ExportUtil.isPerson("John Kowalsky"));
        Assert.assertTrue(ExportUtil.isPerson("Kowalski, Jan"));
        Assert.assertTrue(ExportUtil.isPerson("Kowalski, J."));
        Assert.assertTrue(ExportUtil.isPerson("Kowalski, J.K."));
        Assert.assertTrue(ExportUtil.isPerson("Kowalski, J.K.P."));
        Assert.assertTrue(ExportUtil.isPerson("Jan Maria Kowalski"));
        Assert.assertTrue(ExportUtil.isPerson("Jan Maria Kowalski Rokita"));

        Assert.assertFalse(ExportUtil.isPerson("Xxx Kowalski"));
        Assert.assertFalse(ExportUtil.isPerson("Kowalski, Xxx"));
        Assert.assertFalse(ExportUtil.isPerson("Kowalski, j."));
        Assert.assertFalse(ExportUtil.isPerson("Kowalski, J.x."));
        Assert.assertFalse(ExportUtil.isPerson("Kowalski, J.5."));
        Assert.assertFalse(ExportUtil.isPerson("Kowalski, J.k.P."));
        Assert.assertFalse(ExportUtil.isPerson("Jan Maria Kowalski Rokita Nowak"));
        Assert.assertFalse(ExportUtil.isPerson("Jan"));
    }

    @Test
    public void testIsOrganization() {
        Assert.assertFalse(ExportUtil.isOrganization("Jan Kowalski"));
        Assert.assertFalse(ExportUtil.isOrganization("John Kowalsky"));
        Assert.assertFalse(ExportUtil.isOrganization("Kowalski, Jan"));
        Assert.assertFalse(ExportUtil.isOrganization("Kowalski, J."));
        Assert.assertFalse(ExportUtil.isOrganization("Kowalski, J.K."));
        Assert.assertFalse(ExportUtil.isOrganization("Kowalski, J.K.P."));
        Assert.assertFalse(ExportUtil.isOrganization("Jan Maria Kowalski"));
        Assert.assertFalse(ExportUtil.isOrganization("Jan Maria Kowalski Rokita"));

        Assert.assertTrue(ExportUtil.isOrganization("Xxx Kowalski"));
        Assert.assertTrue(ExportUtil.isOrganization("Kowalski, Xxx"));
        Assert.assertTrue(ExportUtil.isOrganization("Kowalski, j."));
        Assert.assertTrue(ExportUtil.isOrganization("Kowalski, J.x."));
        Assert.assertTrue(ExportUtil.isOrganization("Kowalski, J.5."));
        Assert.assertTrue(ExportUtil.isOrganization("Kowalski, J.k.P."));
        Assert.assertTrue(ExportUtil.isOrganization("Jan Maria Kowalski Rokita Nowak"));
        Assert.assertTrue(ExportUtil.isOrganization("Jan"));
    }
}
