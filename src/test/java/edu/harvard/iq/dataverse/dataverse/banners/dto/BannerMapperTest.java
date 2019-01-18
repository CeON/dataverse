package edu.harvard.iq.dataverse.dataverse.banners.dto;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.dataverse.banners.DataverseBanner;
import edu.harvard.iq.dataverse.dataverse.banners.DataverseLocalizedBanner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;

import static edu.harvard.iq.dataverse.util.DateUtil.convertToDate;

public class BannerMapperTest {

    private static final Date FROM_TIME = convertToDate(
            LocalDateTime.of(2018, 10, 1, 9, 15, 45));
    private static final Date TO_TIME = convertToDate(
            LocalDateTime.of(2018, 11, 2, 10, 25, 55));
    private static final Path BANNER_PATH = Paths.get("src/test/resources/images/banner.png");

    @Test
    public void shouldMapToDto() throws IOException {
        //given
        BannerMapper bannerMapper = new BannerMapper();
        DataverseBanner dataverseBanner = createBannerEntity();

        //when
        DataverseBannerDto dto = bannerMapper.mapToDto(dataverseBanner);
        DataverseLocalizedBannerDto bannerDto = dto.getDataverseLocalizedBanner().get(0);
        DataverseLocalizedBanner localizedBanner = dataverseBanner.getDataverseLocalizedBanner().iterator().next();

        //then
        Assert.assertEquals(dto.getDataverseId(), dataverseBanner.getDataverse().getId());
        Assert.assertEquals(dto.getFromTime(), dataverseBanner.getFromTime());
        Assert.assertEquals(dto.getToTime(), dataverseBanner.getToTime());
        Assert.assertEquals(localizedBanner.getImage(), bannerDto.getImage());
        Assert.assertEquals(localizedBanner.getLocale(), bannerDto.getLocale());
        Assert.assertEquals(localizedBanner.getContentType(), bannerDto.getContentType());
        Assert.assertEquals(localizedBanner.getImageLink(), bannerDto.getImageLink());

    }

    @Test
    public void shouldMapToEntity() throws IOException {
        //given
        BannerMapper bannerMapper = new BannerMapper();
        DataverseBannerDto bannerDto = createBannerDto();
        Dataverse dataverse = new Dataverse();
        dataverse.setId(1L);

        //when
        DataverseBanner banner = bannerMapper.mapToEntity(bannerDto, dataverse);
        DataverseLocalizedBannerDto localizedBannerDto = bannerDto.getDataverseLocalizedBanner().iterator().next();
        DataverseLocalizedBanner localizedBanner = banner.getDataverseLocalizedBanner().iterator().next();

        //then
        Assert.assertEquals(banner.getDataverse().getId(), bannerDto.getDataverseId());
        Assert.assertEquals(banner.getFromTime(), bannerDto.getFromTime());
        Assert.assertEquals(banner.getToTime(), bannerDto.getToTime());
        Assert.assertEquals(localizedBanner.getImage(), localizedBannerDto.getImage());
        Assert.assertEquals(localizedBanner.getLocale(), localizedBannerDto.getLocale());
        Assert.assertEquals(localizedBanner.getContentType(), localizedBannerDto.getContentType());
        Assert.assertEquals(localizedBanner.getImageLink(), localizedBannerDto.getImageLink());

    }

    private DataverseBanner createBannerEntity() throws IOException {
        DataverseBanner dataverseBanner = new DataverseBanner();
        dataverseBanner.setActive(false);
        dataverseBanner.setFromTime(FROM_TIME);
        dataverseBanner.setToTime(TO_TIME);
        dataverseBanner.setId(1L);
        Dataverse dataverse = new Dataverse();
        dataverse.setId(1L);
        dataverseBanner.setDataverse(dataverse);

        DataverseLocalizedBanner dataverseLocalizedBanner = new DataverseLocalizedBanner();
        dataverseLocalizedBanner.setContentType("image/jpeg");
        dataverseLocalizedBanner.setLocale("en");
        dataverseLocalizedBanner.setImage(Files.readAllBytes(BANNER_PATH));
        dataverseBanner.setDataverseLocalizedBanner(Sets.newHashSet(dataverseLocalizedBanner));
        return dataverseBanner;
    }

    private DataverseBannerDto createBannerDto() throws IOException {
        DataverseBannerDto dataverseBanner = new DataverseBannerDto();
        dataverseBanner.setActive(false);
        dataverseBanner.setFromTime(FROM_TIME);
        dataverseBanner.setToTime(TO_TIME);
        dataverseBanner.setId(1L);
        dataverseBanner.setDataverseId(1L);

        DataverseLocalizedBannerDto dataverseLocalizedBanner = new DataverseLocalizedBannerDto();
        dataverseLocalizedBanner.setContentType("image/jpeg");
        dataverseLocalizedBanner.setLocale("en");
        dataverseLocalizedBanner.setImage(Files.readAllBytes(BANNER_PATH));
        dataverseBanner.setDataverseLocalizedBanner(Lists.newArrayList(dataverseLocalizedBanner));
        return dataverseBanner;
    }
}
