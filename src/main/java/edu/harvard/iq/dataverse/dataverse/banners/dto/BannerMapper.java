package edu.harvard.iq.dataverse.dataverse.banners.dto;

import edu.harvard.iq.dataverse.dataverse.banners.DataverseBanner;
import edu.harvard.iq.dataverse.locale.DataverseLocaleBean;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Stateless
public class BannerMapper {

    public DataverseBannerDto mapToDto(DataverseBanner dataverseBanner) {
        DataverseBannerDto dto = new DataverseBannerDto();

        dto.setId(dataverseBanner.getId());
        dto.setFromTime(dataverseBanner.getFromTime());
        dto.setToTime(dataverseBanner.getToTime());
        dto.setActive(dataverseBanner.isActive());

        List<DataverseLocalizedBannerDto> dlbDto = new ArrayList<>();

        dataverseBanner.getDataverseLocalizedBanner()
                .forEach(dlb ->
                {
                    DataverseLocalizedBannerDto localBannerDto =
                            new DataverseLocalizedBannerDto(dlb.getId(), dlb.getLocale(),
                                    new DataverseLocaleBean().getLanguage(dlb.getLocale()),
                                    dlb.getImage(), dlb.getImageLink());

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
}
