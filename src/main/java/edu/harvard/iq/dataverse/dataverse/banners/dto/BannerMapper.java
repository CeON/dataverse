package edu.harvard.iq.dataverse.dataverse.banners.dto;

import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.dataverse.banners.BannerLimits;
import edu.harvard.iq.dataverse.dataverse.banners.DataverseBanner;
import edu.harvard.iq.dataverse.dataverse.banners.DataverseLocalizedBanner;
import edu.harvard.iq.dataverse.locale.DataverseLocaleBean;
import org.imgscalr.Scalr;
import org.primefaces.model.DefaultStreamedContent;

import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
public class BannerMapper {

    private static final Logger logger = Logger.getLogger(BannerMapper.class.getCanonicalName());

    public DataverseBannerDto mapToDto(DataverseBanner dataverseBanner) {
        DataverseBannerDto dto = new DataverseBannerDto();

        dto.setId(dataverseBanner.getId());
        dto.setFromTime(dataverseBanner.getFromTime());
        dto.setToTime(dataverseBanner.getToTime());
        dto.setActive(dataverseBanner.isActive());
        dto.setDataverseId(dataverseBanner.getDataverse().getId());

        List<DataverseLocalizedBannerDto> dlbDto = new ArrayList<>();

        dataverseBanner.getDataverseLocalizedBanner()
                .forEach(dlb ->
                {
                    DataverseLocalizedBannerDto localBannerDto =
                            new DataverseLocalizedBannerDto(dlb.getId(), dlb.getLocale(),
                                    new DataverseLocaleBean().getLanguage(dlb.getLocale()),
                                    dlb.getImage(), dlb.getImageLink());

                    ByteArrayOutputStream resizedImage = convertImageToMiniSize(dlb.getImage());

                    localBannerDto.setMiniDisplayImage(
                            new DefaultStreamedContent(new ByteArrayInputStream(resizedImage.toByteArray()),
                                    "image/jpeg"));

                    localBannerDto.setContentType(dlb.getContentType());
                    dlbDto.add(localBannerDto);
                });

        dto.setDataverseLocalizedBanner(dlbDto);

        return dto;
    }

    public List<DataverseBannerDto> mapToDtos(List<DataverseBanner> dataverseBanners) {
        List<DataverseBannerDto> dtos = new ArrayList<>();

        dataverseBanners.forEach(dataverseBanner -> dtos.add(mapToDto(dataverseBanner)));

        return dtos;
    }

    public DataverseBanner mapToEntity(DataverseBannerDto dto, Dataverse dataverse) {
        DataverseBanner banner = new DataverseBanner();

        banner.setActive(dto.isActive());
        banner.setFromTime(dto.getFromTime());
        banner.setToTime(dto.getToTime());
        banner.setDataverse(dataverse);

        dto.getDataverseLocalizedBanner().forEach(fuDto -> {
            DataverseLocalizedBanner dataverseLocalizedBanner = new DataverseLocalizedBanner();
            dataverseLocalizedBanner.setImage(fuDto.getFile().getContents());
            dataverseLocalizedBanner.setImageLink(fuDto.getImageLink());
            dataverseLocalizedBanner.setDataverseBanner(banner);
            dataverseLocalizedBanner.setLocale(fuDto.getLocale());
            dataverseLocalizedBanner.setContentType(fuDto.getFile().getContentType());

            banner.getDataverseLocalizedBanner().add(dataverseLocalizedBanner);
        });
        return banner;
    }

    public List<DataverseLocalizedBannerDto> mapDefaultLocales() {
        Map<String, String> locales = new DataverseLocaleBean().getDataverseLocales();

        return locales.entrySet().stream()
                .map(e -> new DataverseLocalizedBannerDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public DataverseBannerDto mapToNewBanner(Long dataverseId) {
        DataverseBannerDto dto = new DataverseBannerDto();

        dto.setDataverseId(dataverseId);
        dto.setDataverseLocalizedBanner(mapDefaultLocales());

        return dto;
    }

    private ByteArrayOutputStream convertImageToMiniSize(byte[] image) {

        try {
            BufferedImage loadedImage = ImageIO.read(new ByteArrayInputStream(image));

            BufferedImage resizedImage = Scalr.resize(loadedImage,
                    BannerLimits.MAX_WIDTH.getValue() / 3,
                    BannerLimits.MAX_HEIGHT.getValue() / 3);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, "jpeg", os);
            return os;

        } catch (IOException e) {
            logger.fine("There was a problem loading the image");
        }
        return new ByteArrayOutputStream();
    }
}
