package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.DatasetFieldConstant;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.FieldType;
import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.WidgetWrapper;
import edu.harvard.iq.dataverse.search.dto.CheckboxSearchField;
import edu.harvard.iq.dataverse.search.dto.NumberSearchField;
import edu.harvard.iq.dataverse.search.dto.SearchBlock;
import edu.harvard.iq.dataverse.search.dto.SearchField;
import edu.harvard.iq.dataverse.search.dto.TextSearchField;
import edu.harvard.iq.dataverse.util.BundleUtil;
import io.vavr.Tuple;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Page class responsible for showing search fields for Metadata blocks, files/dataverses blocks
 * and redirecting to search results.
 */
@ViewScoped
@Named("AdvancedSearchPage")
public class AdvancedSearchPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(AdvancedSearchPage.class.getCanonicalName());

    @EJB
    DataverseServiceBean dataverseServiceBean;

    @EJB
    DatasetFieldServiceBean datasetFieldService;

    @Inject
    WidgetWrapper widgetWrapper;

    @Inject
    SolrQueryCreator solrQueryCreator;

    private Dataverse dataverse;
    private String dataverseIdentifier;

    private List<MetadataBlock> metadataBlocks;
    private SearchBlock dataversesSearchBlock;
    private SearchBlock filesSearchBlock;
    private List<SearchBlock> metadataSearchBlocks = new ArrayList<>();

    // -------------------- LOGIC --------------------

    /**
     * Initalizes all components required to view the the page correctly.
     */
    public void init() {

        if (dataverseIdentifier != null) {
            dataverse = dataverseServiceBean.findByAlias(dataverseIdentifier);
        }
        if (dataverse == null) {
            dataverse = dataverseServiceBean.findRootDataverse();
        }
        metadataBlocks = dataverse.getMetadataBlocks();
        List<DatasetFieldType> metadataFieldList = datasetFieldService.findAllAdvancedSearchFieldTypes();

        mapAllMetadataBlocks(metadataFieldList);

        mapDataversesAndFilesBlocks();
    }

    /**
     * Composes query and redirects to the page with results.
     *
     * @return url with query
     * @throws IOException
     */
    public String find() throws IOException {
        List<SearchBlock> allSearchBlocks = new ArrayList<>(metadataSearchBlocks);
        allSearchBlocks.add(filesSearchBlock);
        allSearchBlocks.add(dataversesSearchBlock);

        String query = solrQueryCreator.constructQuery(allSearchBlocks);

        String returnString = "/dataverse.xhtml?q=";
        returnString += URLEncoder.encode(query, "UTF-8");
        returnString += "&alias=" + dataverse.getAlias() + "&faces-redirect=true";
        returnString = widgetWrapper.wrapURL(returnString);

        logger.fine(returnString);
        return returnString;
    }

    // -------------------- PRIVATE --------------------

    private void mapDataversesAndFilesBlocks() {
        dataversesSearchBlock = new SearchBlock("dataverses",
                BundleUtil.getStringFromBundle("advanced.search.header.dataverses"), constructDataversesSearchFields());

        filesSearchBlock = new SearchBlock("files",
                BundleUtil.getStringFromBundle("advanced.search.header.files"),
                constructFilesSearchFields());
    }

    private void mapAllMetadataBlocks(List<DatasetFieldType> metadataFieldList) {
        for (MetadataBlock mdb : metadataBlocks) {

            List<SearchField> searchFields = mapMetadataBlockFieldsToSearchFields(metadataFieldList, mdb);

            metadataSearchBlocks.add(new SearchBlock(mdb.getName(), mdb.getLocaleDisplayName(), searchFields));
        }
        addExtraFieldsToCitationMetadataBlock();
    }

    private List<SearchField> mapMetadataBlockFieldsToSearchFields(List<DatasetFieldType> metadataFieldList, MetadataBlock mdb) {
        List<SearchField> searchFields = new ArrayList<>();

        metadataFieldList.stream()
                .filter(datasetFieldType -> datasetFieldType.getMetadataBlock().getId().equals(mdb.getId()))
                .forEach(datasetFieldType -> searchFields.addAll(mapDatasetFields(datasetFieldType)));
        return searchFields;
    }

    private void addExtraFieldsToCitationMetadataBlock() {
        metadataSearchBlocks.stream()
                .filter(searchBlock -> searchBlock.getBlockName().equals(SearchFields.DATASET_CITATION))
                .map(SearchBlock::getSearchFields)
                .forEach(searchFields -> {

                    searchFields.add(new TextSearchField(SearchFields.DATASET_PERSISTENT_ID,
                            BundleUtil.getStringFromBundle("dataset.metadata.persistentId"),
                            BundleUtil.getStringFromBundle("dataset.metadata.persistentId.tip"),
                            FieldType.TEXT));

                    searchFields.add(new TextSearchField(SearchFields.DATASET_PUBLICATION_DATE,
                            BundleUtil.getStringFromBundle("dataset.metadata.publicationYear"),
                            BundleUtil.getStringFromBundle("dataset.metadata.publicationYear.tip"),
                            FieldType.TEXT));

                });
    }

    private List<SearchField> mapDatasetFields(DatasetFieldType datasetFieldType) {
        List<SearchField> searchFields = new ArrayList<>();

        if (containsCheckboxValues(datasetFieldType)) {

            searchFields.add(mapCheckBoxValues(datasetFieldType));

        } else if (isTextOrDateField(datasetFieldType)) {
            searchFields.add(new TextSearchField(datasetFieldType.getName(),
                    datasetFieldType.getDisplayName(),
                    datasetFieldType.getLocaleDescription(),
                    datasetFieldType.getFieldType()));

        } else if (isNumberField(datasetFieldType)) {
            searchFields.add(new NumberSearchField(datasetFieldType.getName(),
                    datasetFieldType.getDisplayName(),
                    datasetFieldType.getLocaleDescription(),
                    datasetFieldType.getFieldType()));
        }

        return searchFields;
    }

    private CheckboxSearchField mapCheckBoxValues(DatasetFieldType datasetFieldType) {
        CheckboxSearchField checkboxSearchField = new CheckboxSearchField(datasetFieldType.getName(),
                datasetFieldType.getDisplayName(),
                StringUtils.EMPTY,
                FieldType.CHECKBOX);

        for (ControlledVocabularyValue vocabValue : datasetFieldType.getControlledVocabularyValues()) {
            checkboxSearchField.getCheckboxLabelAndValue().add(Tuple.of(vocabValue.getLocaleStrValue(),
                    vocabValue.getStrValue()));

        }
        return checkboxSearchField;
    }

    private boolean containsCheckboxValues(DatasetFieldType datasetFieldType) {
        return !datasetFieldType.getControlledVocabularyValues().isEmpty();
    }

    private boolean isNumberField(DatasetFieldType datasetFieldType) {
        return datasetFieldType.getFieldType().equals(FieldType.INT) ||
                datasetFieldType.getFieldType().equals(FieldType.FLOAT);
    }

    private boolean isTextOrDateField(DatasetFieldType datasetFieldType) {
        return datasetFieldType.getFieldType().equals(FieldType.TEXT) ||
                datasetFieldType.getFieldType().equals(FieldType.TEXTBOX) ||
                datasetFieldType.getFieldType().equals(FieldType.DATE);
    }

    private List<SearchField> constructFilesSearchFields() {
        List<SearchField> filesSearchFields = new ArrayList<>();

        filesSearchFields.add(new TextSearchField(SearchFields.FILE_NAME,
                BundleUtil.getStringFromBundle("name"),
                BundleUtil.getStringFromBundle("advanced.search.files.name.tip"),
                FieldType.TEXT));

        filesSearchFields.add(new TextSearchField(SearchFields.FILE_DESCRIPTION,
                BundleUtil.getStringFromBundle("description"),
                BundleUtil.getStringFromBundle("advanced.search.files.description.tip"),
                FieldType.TEXT));

        filesSearchFields.add(new TextSearchField(SearchFields.FILE_TYPE_SEARCHABLE,
                BundleUtil.getStringFromBundle("advanced.search.files.fileType"),
                BundleUtil.getStringFromBundle("advanced.search.files.fileType.tip"),
                FieldType.TEXT));

        filesSearchFields.add(new TextSearchField(SearchFields.FILE_PERSISTENT_ID,
                BundleUtil.getStringFromBundle("advanced.search.files.persistentId"),
                BundleUtil.getStringFromBundle("advanced.search.files.persistentId.tip"),
                FieldType.TEXT));

        filesSearchFields.add(new TextSearchField(SearchFields.VARIABLE_NAME,
                BundleUtil.getStringFromBundle("advanced.search.files.variableName"),
                BundleUtil.getStringFromBundle("advanced.search.files.variableName.tip"),
                FieldType.TEXT));

        filesSearchFields.add(new TextSearchField(SearchFields.VARIABLE_LABEL,
                BundleUtil.getStringFromBundle("advanced.search.files.variableLabel"),
                BundleUtil.getStringFromBundle("advanced.search.files.variableLabel.tip"),
                FieldType.TEXT));

        return filesSearchFields;
    }

    private List<SearchField> constructDataversesSearchFields() {
        List<SearchField> dataversesSearchFields = new ArrayList<>();

        dataversesSearchFields.add(new TextSearchField(SearchFields.DATAVERSE_NAME,
                BundleUtil.getStringFromBundle("name"),
                BundleUtil.getStringFromBundle("advanced.search.dataverses.name.tip"),
                FieldType.TEXT));

        dataversesSearchFields.add(new TextSearchField(SearchFields.DATAVERSE_ALIAS,
                BundleUtil.getStringFromBundle("identifier"),
                BundleUtil.getStringFromBundle("dataverse.identifier.title"),
                FieldType.TEXT));

        dataversesSearchFields.add(new TextSearchField(SearchFields.DATAVERSE_AFFILIATION,
                BundleUtil.getStringFromBundle("affiliation"),
                BundleUtil.getStringFromBundle("advanced.search.dataverses.affiliation.tip"),
                FieldType.TEXT));

        dataversesSearchFields.add(new TextSearchField(SearchFields.DATAVERSE_DESCRIPTION,
                BundleUtil.getStringFromBundle("description"),
                BundleUtil.getStringFromBundle("advanced.search.dataverses.description.tip"),
                FieldType.TEXT));

        CheckboxSearchField checkboxSearchField = new CheckboxSearchField(SearchFields.DATAVERSE_SUBJECT,
                BundleUtil.getStringFromBundle("subject"),
                BundleUtil.getStringFromBundle("advanced.search.dataverses.subject.tip"),
                FieldType.CHECKBOX);

        DatasetFieldType subjectType = datasetFieldService.findByName(DatasetFieldConstant.subject);

        for (ControlledVocabularyValue vocabValue : subjectType.getControlledVocabularyValues()) {
            checkboxSearchField.getCheckboxLabelAndValue().add(Tuple.of(vocabValue.getLocaleStrValue(),
                    vocabValue.getStrValue()));

        }

        dataversesSearchFields.add(checkboxSearchField);

        return dataversesSearchFields;
    }

    // -------------------- GETTERS --------------------

    public Dataverse getDataverse() {
        return dataverse;
    }

    public String getDataverseIdentifier() {
        return dataverseIdentifier;
    }

    public List<MetadataBlock> getMetadataBlocks() {
        return metadataBlocks;
    }

    public List<SearchBlock> getMetadataSearchBlocks() {
        return metadataSearchBlocks;
    }

    public SearchBlock getDataversesSearchBlock() {
        return dataversesSearchBlock;
    }

    public SearchBlock getFilesSearchBlock() {
        return filesSearchBlock;
    }

    // -------------------- SETTERS --------------------

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public void setDataverseIdentifier(String dataverseIdentifier) {
        this.dataverseIdentifier = dataverseIdentifier;
    }
}
