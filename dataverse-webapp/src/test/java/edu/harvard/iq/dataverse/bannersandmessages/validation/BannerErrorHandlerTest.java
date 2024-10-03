package edu.harvard.iq.dataverse.bannersandmessages.validation;

import java.util.Locale;

import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Lists;

import edu.harvard.iq.dataverse.bannersandmessages.banners.BannerLimits;
import edu.harvard.iq.dataverse.persistence.dataverse.bannersandmessages.DataverseBanner;
import edu.harvard.iq.dataverse.persistence.dataverse.bannersandmessages.DataverseLocalizedBanner;

@RunWith(MockitoJUnitRunner.class)
public class BannerErrorHandlerTest {

    @Mock
    private FacesContext facesContextMock;
    @Mock
    private ExternalContext externalContextMock;
    @Mock
    private Locale locale;
    @Captor
    private ArgumentCaptor<FacesMessage> facesMesssage;

    
    private byte[] loadImage() throws Exception {
        
        return IOUtils.toByteArray(getClass().getClassLoader().getResource("images/banner.png"));
    }

    @Test
    public void shouldAddErrorMessageImageMissing() {
        //given
        BannerErrorHandler bannerErrorHandler = new BannerErrorHandler(new BannerLimits());
        DataverseBanner banner = new DataverseBanner();
        DataverseLocalizedBanner dataverseLocalizedBanner = new DataverseLocalizedBanner();
        dataverseLocalizedBanner.setImage(new byte[0]);
        banner.setDataverseLocalizedBanner(Lists.newArrayList(dataverseLocalizedBanner));

        //when
        bannerErrorHandler.handleBannerAddingErrors(banner, dataverseLocalizedBanner, facesContextMock);
        //then
        Mockito.verify(facesContextMock).addMessage(Mockito.eq("edit-text-messages-form:repeater:" + 0 + ":upload"),
                                                    facesMesssage.capture());
        FacesMessage message = facesMesssage.getValue();

        Assert.assertEquals("The image is missing", message.getDetail());

    }

    @Test
    public void shouldAddErrorMessageResolutionTooHigh() throws Exception {
        //given
        BannerErrorHandler bannerErrorHandler = new BannerErrorHandler(new BannerLimits(1, 1, Integer.MAX_VALUE));
        DataverseBanner banner = new DataverseBanner();
        DataverseLocalizedBanner dataverseLocalizedBanner = new DataverseLocalizedBanner();
        dataverseLocalizedBanner.setImage(loadImage());
        banner.setDataverseLocalizedBanner(Lists.newArrayList(dataverseLocalizedBanner));

        //when
        bannerErrorHandler.handleBannerAddingErrors(banner, dataverseLocalizedBanner, facesContextMock);

        //then
        Mockito.verify(facesContextMock).addMessage(Mockito.eq("edit-text-messages-form:repeater:" + 0 + ":upload"),
                                                    facesMesssage.capture());
        FacesMessage message = facesMesssage.getValue();

        Assert.assertEquals("The image could not be uploaded. Please try again with a image with smaller resolution.", message.getDetail());

    }
}
