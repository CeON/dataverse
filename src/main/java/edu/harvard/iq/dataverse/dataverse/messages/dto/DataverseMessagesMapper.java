package edu.harvard.iq.dataverse.dataverse.messages.dto;

import com.google.common.collect.Sets;
import edu.harvard.iq.dataverse.DataverseLocaleBean;
import edu.harvard.iq.dataverse.dataverse.messages.DataverseTextMessage;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Stateless
public class DataverseMessagesMapper {

    public DataverseTextMessageDto mapToDto(DataverseTextMessage textMessage) {
        DataverseTextMessageDto dto = new DataverseTextMessageDto();

        dto.setId(textMessage.getId());
        dto.setActive(textMessage.isActive());
        dto.setFromTime(textMessage.getFromTime());
        dto.setToTime(textMessage.getToTime());

        Set<DataverseLocalizedMessageDto> dataverseLocalizedMessageDto = new HashSet<>();
        ofNullable(textMessage.getDataverseLocalizedMessages()).orElseGet(Sets::newHashSet)
                .forEach(dlm -> dataverseLocalizedMessageDto.add(new DataverseLocalizedMessageDto(
                        dlm.getLocale(),
                        dlm.getMessage(),
                        new DataverseLocaleBean().getLanguage(dlm.getLocale()))));

        dto.setDataverseLocalizedMessage(dataverseLocalizedMessageDto);

        return dto;
    }

    public List<DataverseTextMessageDto> mapToDtos(List<DataverseTextMessage> textMessages) {
        List<DataverseTextMessageDto> dtos = new ArrayList<>();

        textMessages.forEach(dataverseTextMessage -> dtos.add(this.mapToDto(dataverseTextMessage)));

        return dtos;
    }

    public Set<DataverseLocalizedMessageDto> mapDefaultLocales() {
        Map<String, String> locales = new DataverseLocaleBean().getDataverseLocales();

        return locales.entrySet().stream()
                .map(e -> new DataverseLocalizedMessageDto(e.getKey(), null, e.getValue()))
                .collect(Collectors.toSet());
    }
}
