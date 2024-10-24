package edu.harvard.iq.dataverse.license.dto;

import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseIcon;
import edu.harvard.iq.dataverse.persistence.datafile.license.LocaleText;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Stateless
public class LicenseMapper {

    // -------------------- LOGIC --------------------

    public LicenseDto mapToDto(License license) {

        List<LocaleTextDto> localeTextDtos = new ArrayList<>();

        for (LocaleText localizedName : license.getLocalizedNames()) {
            localeTextDtos.add(mapToDto(localizedName));
        }

        return new LicenseDto(license.getId(),
                              license.getName(),
                              license.getUrl(),
                              mapToDto(license.getIcon()),
                              license.isActive(),
                              license.getPosition(),
                              localeTextDtos);
    }

    public List<LicenseDto> mapToDtos(List<License> licenses) {
        return licenses.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public LicenseSimpleDto mapToSimpleDto(License license, Locale textLocale) {

        return new LicenseSimpleDto(license.getId(), license.getLocalizedName(textLocale));
    }

    public List<LicenseSimpleDto> mapToSimpleDtos(List<License> licenses, Locale textLocale) {
        List<LicenseSimpleDto> licenseSimpleDtos = new ArrayList<>();

        for (License license : licenses) {
            licenseSimpleDtos.add(this.mapToSimpleDto(license, textLocale));
        }

        return licenseSimpleDtos;
    }

    public License editLicense(LicenseDto licenseDto, License license) {
        license.setPosition(licenseDto.getPosition());
        license.setActive(licenseDto.isActive());
        license.setUrl(licenseDto.getUrl());
        license.setName(licenseDto.getName());
        license.setIcon(licenseDto.getIcon().getContent() == null ? null : editLicenseIcon(licenseDto, license, license.getIcon()));

        license.removeLocalizedNames(licenseDto.getLocalizedNames().stream()
                .map(LocaleTextDto::getLocale).collect(Collectors.toList()));
        licenseDto.getLocalizedNames().forEach(localeTextDto -> license.addLocalizedName(
                new LocaleText(localeTextDto.getLocale(), localeTextDto.getText())));

        return license;
    }

    public License mapToLicense(LicenseDto licenseDto) {
        License license = new License();

        license.setId(licenseDto.getId());
        license.setPosition(licenseDto.getPosition());
        license.setActive(licenseDto.isActive());
        license.setUrl(licenseDto.getUrl());
        license.setName(licenseDto.getName());
        license.setIcon(licenseDto.getIcon().getContent() == null ? null : mapToLicenseIcon(licenseDto, license));

        licenseDto.getLocalizedNames().forEach(localeTextDto -> license.addLocalizedName(
                new LocaleText(localeTextDto.getLocale(), localeTextDto.getText())));

        return license;
    }

    // -------------------- PRIVATE --------------------

    private LicenseIcon mapToLicenseIcon(LicenseDto licenseDto, License license) {
        LicenseIcon licenseIcon = new LicenseIcon();
        licenseIcon.setId(licenseDto.getId());
        licenseIcon.setContent(licenseDto.getIcon().getContent());

        licenseIcon.setLicense(license);
        licenseIcon.setContentType(licenseDto.getIcon().getContentType());

        return licenseIcon;
    }

    private LicenseIcon editLicenseIcon(LicenseDto licenseDto, License license, LicenseIcon licenseIcon) {
        LicenseIcon icon = licenseIcon == null ? new LicenseIcon() : licenseIcon;

        byte[] bytes = licenseDto.getIcon().getContent();

        icon.setContent(bytes);
        icon.setContentType(licenseDto.getIcon().getContentType());

        icon.setLicense(license);

        return icon;
    }

    private LicenseIconDto mapToDto(LicenseIcon licenseIcon) {
        if (licenseIcon == null) {
            return new LicenseIconDto(0L, new byte[0], StringUtils.EMPTY);
        }

        return new LicenseIconDto(licenseIcon.getId(), licenseIcon.getContent(),licenseIcon.getContentType());
    }

    private LocaleTextDto mapToDto(LocaleText localeText) {
        return new LocaleTextDto(localeText.getLocale(), localeText.getText());
    }
}
