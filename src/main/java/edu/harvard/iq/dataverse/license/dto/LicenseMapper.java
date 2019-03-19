package edu.harvard.iq.dataverse.license.dto;

import edu.harvard.iq.dataverse.license.License;
import edu.harvard.iq.dataverse.license.LicenseIcon;
import edu.harvard.iq.dataverse.license.LocaleText;
import org.primefaces.model.ByteArrayContent;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.List;
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
                true,
                license.getPosition(),
                localeTextDtos);
    }

    public List<LicenseDto> mapToDtos(List<License> licenses) {
        return licenses.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // -------------------- PRIVATE --------------------

    private LicenseIconDto mapToDto(LicenseIcon licenseIcon) {
        if (licenseIcon == null) {
            return new LicenseIconDto(new ByteArrayContent(new byte[0]));
        }

        return new LicenseIconDto(licenseIcon.getId(),
                new ByteArrayContent(licenseIcon.getContent(),
                        licenseIcon.getContentType(),
                        licenseIcon.getLicense().getName(),
                        licenseIcon.getContent().length));
    }

    private LocaleTextDto mapToDto(LocaleText localeText) {
        return new LocaleTextDto(localeText.getLocale(), localeText.getText());
    }
}
