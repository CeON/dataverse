package edu.harvard.iq.dataverse.dataverse.banners.dto;

import edu.harvard.iq.dataverse.dataverse.banners.DataverseBanner;
import edu.harvard.iq.dataverse.locale.DataverseLocaleBean;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.List;

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
                        dlbDto.add(new DataverseLocalizedBannerDto(dlb.getId(), dlb.getLocale(), dlb.getImage(),
                                new DataverseLocaleBean().getLanguage(dlb.getLocale()))));

        dto.setDataverseLocalizedBanner(dlbDto);

        return dto;
    }

    public List<DataverseBannerDto> mapToDtos(List<DataverseBanner> dataverseBanners) {
        List<DataverseBannerDto> dtos = new ArrayList<>();

        dataverseBanners.forEach(dataverseBanner -> dtos.add(mapToDto(dataverseBanner)));

        return dtos;
    }
}
