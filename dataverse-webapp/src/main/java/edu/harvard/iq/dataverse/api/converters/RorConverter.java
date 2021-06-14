package edu.harvard.iq.dataverse.api.converters;

import edu.harvard.iq.dataverse.api.dto.RorEntryDTO;
import edu.harvard.iq.dataverse.persistence.ror.RorData;
import edu.harvard.iq.dataverse.persistence.ror.RorLabel;
import org.apache.commons.lang3.StringUtils;

import javax.ejb.Stateless;
import java.util.Arrays;
import java.util.stream.Collectors;

@Stateless
public class RorConverter {

    // -------------------- LOGIC --------------------

    public RorData toEntity(RorEntryDTO entry) {
        RorData converted = new RorData();

        converted.setRorId(extractRor(entry.getId()));
        converted.setName(entry.getName());

        if (entry.getCities().length > 0) {
            converted.setCity(entry.getCities()[0].getCity());
        }

        if (entry.getLinks().length > 0) {
            converted.setLink(entry.getLinks()[0]);
        }

        if (entry.getCountry() != null) {
            converted.setCountryName(entry.getCountry().getCountryName());
            converted.setCountryCode(entry.getCountry().getCountryCode());
        }

        converted.getAcronyms().addAll(Arrays.asList(entry.getAcronyms()));
        converted.getNameAliases().addAll(Arrays.asList(entry.getAliases()));
        converted.getLabels().addAll(
                Arrays.stream(entry.getLabels())
                .map(l -> new RorLabel(l.getLabel(), l.getIso639()))
                .collect(Collectors.toSet()));

        return converted;
    }

    // -------------------- PRIVATE --------------------

    public String extractRor(String rorId) {
        if (StringUtils.isBlank(rorId) || !rorId.contains("/0")) {
            return StringUtils.EMPTY;
        }
        return rorId.substring(rorId.lastIndexOf("/") + 1);
    }
}
