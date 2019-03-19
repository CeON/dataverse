package edu.harvard.iq.dataverse.license;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.license.dto.LicenseDto;
import edu.harvard.iq.dataverse.license.dto.LicenseIconDto;
import edu.harvard.iq.dataverse.license.dto.LocaleTextDto;
import org.apache.commons.lang.StringUtils;
import org.primefaces.model.ByteArrayContent;

import javax.ejb.Stateless;
import java.util.Locale;


/**
 * Creates licenses that are not valid but are required to be viewed in the licenses list.
 */
@Stateless
public class InvalidLicensesCreator {

    // -------------------- LOGIC --------------------

    /**
     * Creates AllRightsReserved not valid license.
     *
     * @param position
     * @return LicenseDto
     */
    public LicenseDto createAllRightsReserved(Long position) {

        return new LicenseDto("All rights reserved",
                StringUtils.EMPTY,
                new LicenseIconDto(new ByteArrayContent(new byte[0])),
                false,
                false,
                position,
                Lists.newArrayList(
                        createLocaleText(Locale.ENGLISH, "All rights reserved"),
                        createLocaleText(new Locale("pl"), "Wszystkie prawa zastrzeżone")));
    }

    /**
     * Creates RestrictedAccess not valid license.
     *
     * @param position
     * @return LicenseDto
     */
    public LicenseDto createRestrictedAccess(Long position) {

        return new LicenseDto("Restricted access",
                StringUtils.EMPTY,
                new LicenseIconDto(new ByteArrayContent(new byte[0])),
                false,
                false,
                position,
                Lists.newArrayList(
                        createLocaleText(Locale.ENGLISH, "Restricted access"),
                        createLocaleText(new Locale("pl"), "Ograniczony dostęp")));
    }

    // -------------------- PRIVATE --------------------

    private LocaleTextDto createLocaleText(Locale locale, String text) {
        return new LocaleTextDto(locale, text);
    }
}
