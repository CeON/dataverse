package edu.harvard.iq.dataverse.util;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Date;

public class DateUtilTest {

    @Test
    public void shouldCorrectlyFormatDate() {
        //given
        Date testDate = Date.from(Instant.ofEpochSecond(1547111385L));
        //when
        String formatedDate = DateUtil.formatDateToYMD_HM(testDate);
        //then
        Assert.assertEquals("2019-01-10 10:09", formatedDate);
    }

    @Test
    public void shouldCorrectlyReturnEmptyString() {
        //given

        //when
        String formatedDate = DateUtil.formatDateToYMD_HM(null);
        //then
        Assert.assertEquals(StringUtils.EMPTY, formatedDate);
    }
}
