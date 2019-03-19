package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.license.dto.LicenseDto;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.Locale;

class InvalidLicensesCreatorTest {

    private InvalidLicensesCreator invalidLicensesCreator = new InvalidLicensesCreator();

    @Test
    void createAllRightsReserved() {
        //when
        LicenseDto allRightsReservedLicense = invalidLicensesCreator.createAllRightsReserved(1L);

        //then
        Assert.assertFalse(allRightsReservedLicense.isValidLicense());
        Assert.assertFalse(allRightsReservedLicense.isActive());
        Assert.assertNotNull(allRightsReservedLicense.getIcon().getContent());
        Assert.assertEquals(Long.valueOf(1L), allRightsReservedLicense.getPosition());
        Assert.assertEquals("All rights reserved", allRightsReservedLicense.getName());
        Assert.assertEquals(StringUtils.EMPTY, allRightsReservedLicense.getUrl());

        Assert.assertEquals(Locale.ENGLISH, allRightsReservedLicense.getLocalizedNames().get(0).getLocale());
        Assert.assertEquals("All rights reserved", allRightsReservedLicense.getLocalizedNames().get(0).getText());
        Assert.assertEquals(new Locale("pl"), allRightsReservedLicense.getLocalizedNames().get(1).getLocale());
        Assert.assertEquals("Wszystkie prawa zastrzeżone", allRightsReservedLicense.getLocalizedNames().get(1).getText());
    }

    @Test
    void createRestrictedAccess() {
        //when
        LicenseDto createRestrictedAccess = invalidLicensesCreator.createRestrictedAccess(1L);

        //then
        Assert.assertFalse(createRestrictedAccess.isValidLicense());
        Assert.assertFalse(createRestrictedAccess.isActive());
        Assert.assertNotNull(createRestrictedAccess.getIcon().getContent());
        Assert.assertEquals(Long.valueOf(1L), createRestrictedAccess.getPosition());
        Assert.assertEquals("Restricted access", createRestrictedAccess.getName());
        Assert.assertEquals(StringUtils.EMPTY, createRestrictedAccess.getUrl());

        Assert.assertEquals(Locale.ENGLISH, createRestrictedAccess.getLocalizedNames().get(0).getLocale());
        Assert.assertEquals("Restricted access", createRestrictedAccess.getLocalizedNames().get(0).getText());
        Assert.assertEquals(new Locale("pl"), createRestrictedAccess.getLocalizedNames().get(1).getLocale());
        Assert.assertEquals("Ograniczony dostęp", createRestrictedAccess.getLocalizedNames().get(1).getText());
    }
}