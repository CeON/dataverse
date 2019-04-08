package edu.harvard.iq.dataverse.license.othertermsofuse.dto;

import edu.harvard.iq.dataverse.license.othertermsofuse.OtherTermsOfUse;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class OtherTermsOfUseMapperTest {

    private OtherTermsOfUseMapper otherTermsOfUseMapper = new OtherTermsOfUseMapper();

    // -------------------- TEST --------------------

    @Test
    public void shouldCorrectlyMapToDto() {
        //given
        OtherTermsOfUse otherTermsOfUse = createOtherTermsOfUse();

        //when
        OtherTermsOfUseDto otherTermsOfUseDto = otherTermsOfUseMapper.mapToDto(otherTermsOfUse);

        //then

        Assert.assertEquals(otherTermsOfUse.getId(), otherTermsOfUseDto.getId());
        Assert.assertEquals(otherTermsOfUse.getName(), otherTermsOfUseDto.getName());
        Assert.assertEquals(otherTermsOfUse.isActive(), otherTermsOfUseDto.isActive());
    }

    // -------------------- PRIVATE --------------------

    private OtherTermsOfUse createOtherTermsOfUse() {
        OtherTermsOfUse otou = new OtherTermsOfUse();
        otou.setId(1L);
        otou.setActive(false);
        otou.setName("test other terms of use");
        return otou;
    }
}