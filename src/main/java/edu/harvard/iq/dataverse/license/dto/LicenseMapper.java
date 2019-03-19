package edu.harvard.iq.dataverse.license.dto;

import edu.harvard.iq.dataverse.license.License;
import edu.harvard.iq.dataverse.license.LicenseIcon;
import edu.harvard.iq.dataverse.license.LocaleText;
import org.imgscalr.Scalr;
import org.primefaces.model.ByteArrayContent;

import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
                new ByteArrayContent(convertImageToFixedSize(licenseIcon.getContent(), 88, 31).toByteArray()
                        , licenseIcon.getContentType(), licenseIcon.getLicense().getName(), licenseIcon.getContent().length),
                licenseIcon.getLicense());
    }

    private LocaleTextDto mapToDto(LocaleText localeText) {
        return new LocaleTextDto(localeText.getLocale(), localeText.getText());
    }

    private ByteArrayOutputStream convertImageToFixedSize(byte[] image, int width, int height) {

        try {
            BufferedImage loadedImage = ImageIO.read(new ByteArrayInputStream(image));

            BufferedImage resizedImage = Scalr.resize(loadedImage, width, height);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, "png", os);
            return os;

        } catch (IOException e) {
            throw new RuntimeException("There was a problem loading the image", e);
        }
    }
}
