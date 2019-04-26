package edu.harvard.iq.dataverse.bannersandmessages.banners;

import edu.harvard.iq.dataverse.bannersandmessages.banners.dto.BannerMapper;
import edu.harvard.iq.dataverse.bannersandmessages.banners.dto.DataverseBannerDto;
import edu.harvard.iq.dataverse.bannersandmessages.banners.dto.DataverseLocalizedBannerDto;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

import javax.ejb.EJB;
import javax.ejb.Stateful;
import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Stateful
public class LazyBannerHistory extends LazyDataModel<DataverseBannerDto> {

    @EJB
    private BannerDAO dao;

    @Inject
    private BannerMapper mapper;

    @Inject
    private SettingsWrapper settingsWrapper;

    private Long dataverseId;
    private List<DataverseBannerDto> dataverseBannerDtos;

    @Override
    public List<DataverseBannerDto> load(int first, int pageSize, String sortField, SortOrder sortOrder, Map<String, Object> filters) {

        List<DataverseBanner> dataverseTextMessages =
                dao.fetchBannersForDataverseWithPaging(dataverseId, first, pageSize);

        dataverseBannerDtos = mapper.mapToDtos(dataverseTextMessages);

        removeBannersLanguagesNotPresentInDataverse(dataverseBannerDtos);
        sortMessageLocales(dataverseBannerDtos);

        setPageSize(pageSize);
        setRowCount(dao.countBannersForDataverse(dataverseId).intValue());

        return dataverseBannerDtos;
    }

    @Override
    public Object getRowKey(DataverseBannerDto object) {
        return object.getId();
    }

    @Override
    public DataverseBannerDto getRowData(String rowKey) {
        Long id = Long.valueOf(rowKey);

        return dataverseBannerDtos.stream()
                .filter(dataverseBannerDto -> dataverseBannerDto.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private List<DataverseBannerDto> removeBannersLanguagesNotPresentInDataverse(List<DataverseBannerDto> dataList) {
        Set<String> dataverseLocales = settingsWrapper.getConfiguredLocales().keySet();

        dataList.forEach(dataverseBannerDto -> dataverseBannerDto.getDataverseLocalizedBanner()
                .removeIf(localizedBannerDto -> !dataverseLocales.contains(localizedBannerDto.getLocale())));
        return dataList;
    }

    private List<DataverseBannerDto> sortMessageLocales(List<DataverseBannerDto> dataList) {
        dataList.forEach(dataverseBannerDto ->
                dataverseBannerDto.getDataverseLocalizedBanner()
                        .sort(Comparator.comparing(DataverseLocalizedBannerDto::getLocale)));
        return dataList;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

}
