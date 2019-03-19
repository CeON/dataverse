package edu.harvard.iq.dataverse.license.dto;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.license.License;
import edu.harvard.iq.dataverse.license.LicenseIcon;
import edu.harvard.iq.dataverse.license.LocaleText;
import io.vavr.control.Try;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

class LicenseMapperTest {

    @Test
    void shouldCorrectlyMapToDto() {
        //given
        License license = createTestLicense();

        //when
        LicenseMapper licenseMapper = new LicenseMapper();
        LicenseDto licenseDto = licenseMapper.mapToDto(license);

        //then
        Assert.assertEquals(new Long(1L), licenseDto.getId());
        Assert.assertEquals(new Long(1L), licenseDto.getPosition());
        Assert.assertEquals("testLicense", licenseDto.getName());
        Assert.assertEquals("www.test.com", licenseDto.getUrl());

        Assert.assertEquals(Locale.ENGLISH, licenseDto.getLocalizedNames().get(0).getLocale());
        Assert.assertEquals("English license", licenseDto.getLocalizedNames().get(0).getText());
        Assert.assertEquals(new Locale("pl"), licenseDto.getLocalizedNames().get(1).getLocale());
        Assert.assertEquals("Polish license", licenseDto.getLocalizedNames().get(1).getText());

        Assert.assertNotNull(licenseDto.getIcon().getContent());

    }

    private License createTestLicense() {
        License license = new License();
        license.setActive(true);
        license.setId(1L);
        license.setName("testLicense");
        license.setPosition(1L);
        license.setUrl("www.test.com");
        license.setLocalizedNames(createLocaleTexts());
        license.setIcon(createLicenseIcon(license));

        return license;
    }

    private LicenseIcon createLicenseIcon(License license) {

        LicenseIcon licenseIcon = new LicenseIcon();
        licenseIcon.setContent(Try.of(() -> IOUtils.toByteArray(this.getClass().getResourceAsStream("/images/banner.png")))
                .getOrElseGet(throwable -> new byte[0]));
        licenseIcon.setContentType("png");
        licenseIcon.setLicense(license);

        return licenseIcon;
    }

    private List<LocaleText> createLocaleTexts() {
        LocaleText englishText = new LocaleText(Locale.ENGLISH, "English license");
        LocaleText polishText = new LocaleText(new Locale("pl"), "Polish license");

        return Lists.newArrayList(englishText, polishText);
    }

}