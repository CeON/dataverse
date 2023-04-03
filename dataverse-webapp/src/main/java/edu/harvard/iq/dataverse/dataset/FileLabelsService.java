package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.annotations.PermissionNeeded;
import edu.harvard.iq.dataverse.api.dto.FileLabelsChangeOptionsDTO;
import edu.harvard.iq.dataverse.ingest.IngestUtil;
import edu.harvard.iq.dataverse.interceptors.Restricted;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersionRepository;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Stateless
public class FileLabelsService {

    private DatasetVersionRepository datasetVersionRepository;

    // -------------------- CONSTRUCTORS --------------------

    public FileLabelsService() { }

    @Inject
    public FileLabelsService(DatasetVersionRepository datasetVersionRepository) {
        this.datasetVersionRepository = datasetVersionRepository;
    }

    // -------------------- LOGIC --------------------

    /**
     * Return all file labels from the latest version of dataset, and mark as
     * affected those matching the given pattern from {@link FileLabelsChangeOptionsDTO}.
     */
    @Restricted(@PermissionNeeded(needs = {Permission.ViewUnpublishedDataset}, allRequired = true))
    public List<FileLabelInfo> prepareFileLabels(@PermissionNeeded Dataset dataset, FileLabelsChangeOptionsDTO options) {
        DatasetVersion latestVersion = dataset.getLatestVersion();
        List<FileMetadata> fileMetadatas = latestVersion.getFileMetadatas();
        Predicate<String> labelMatcher = prepareFileLabelMatcher(options.getPattern());
        List<FileLabelInfo> result = new ArrayList<>();
        for (FileMetadata fileMetadata : fileMetadatas) {
            Long fileId = fileMetadata.getDataFile().getId();
            boolean labelMatches = labelMatcher.test(fileMetadata.getLabel());
            boolean affected = (labelMatches || options.getFilesToIncludeIds().contains(fileId))
                    && !options.getFilesToExcludeIds().contains(fileId);
            result.add(new FileLabelInfo(fileId, fileMetadata.getLabel(), affected));
        }
        Collections.sort(result);
        return result;
    }

    /**
     * The method receives the output of {@link FileLabelsService#prepareFileLabels(Dataset, FileLabelsChangeOptionsDTO)}
     * method, and for the entries marked as affected it tries to change the
     * label according to the given options. If the label after change differs
     * from the original label the entry is marked as affected. At the end
     * all new labels are checked against the rest in order to find and handle
     * duplicates.
     */
    public List<FileLabelInfo> changeLabels(List<FileLabelInfo> inputLabels, FileLabelsChangeOptionsDTO options) {
        List<FileLabelInfo> changedLabels = new ArrayList<>();
        for (FileLabelInfo labelInfo : inputLabels) {
            if (!labelInfo.isAffected()) {
                changedLabels.add(labelInfo);
                continue;
            }
            String fileLabel = labelInfo.getLabel();
            String changed = fileLabel.replaceAll(options.getFrom(), options.getTo());
            boolean affected = !fileLabel.equals(changed);
            changedLabels.add(new FileLabelInfo(labelInfo.getId(), fileLabel,
                    affected ? changed : labelInfo.getLabelAfterChange(), affected));
        }
        // Handle duplicated names: first extract labels that aren't going to change to a map, then for each
        // changed label check whether it's a duplicate – if so keep generating new labels until it's not.
        // Then add this new label into the map, and check the next label.
        Map<String, FileLabelInfo> finalLabels = new HashMap<>(changedLabels.stream()
                .filter(e -> !e.isAffected())
                .collect(Collectors.toMap(FileLabelInfo::getLabel, e -> e, (prev, next) -> next)));
        for (FileLabelInfo labelInfo : changedLabels) {
            if (!labelInfo.isAffected()) {
                continue;
            }
            String newLabel = labelInfo.getLabelAfterChange();
            while (finalLabels.containsKey(newLabel)) {
                newLabel = IngestUtil.generateNewFileName(newLabel);
            }
            finalLabels.put(newLabel, new FileLabelInfo(labelInfo.getId(), labelInfo.getLabel(), newLabel, true));
        }
        return finalLabels.values().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * The method receives the output from {@link FileLabelsService#changeLabels(List, FileLabelsChangeOptionsDTO)}
     * and saves the labels marked as affected into the draft version of the
     * given dataset. The caller of the method should ensure that the draft
     * is properly created before the method is called, otherwise issues with
     * database constraints will follow.
     */
    @Restricted(@PermissionNeeded(needs = {Permission.EditDataset}, allRequired = true))
    public List<FileLabelInfo> updateDataset(@PermissionNeeded Dataset dataset, List<FileLabelInfo> labelToChange) {
        DatasetVersion editVersion = dataset.getEditVersion();
        Map<Long, FileLabelInfo> labelsToChangeById = labelToChange.stream()
                .filter(FileLabelInfo::isAffected)
                .collect(Collectors.toMap(FileLabelInfo::getId, l -> l, (prev, next) -> next));
        List<FileMetadata> fileMetadatas = editVersion.getFileMetadatas();
        for (FileMetadata metadata : fileMetadatas) {
            Long fileId = metadata.getDataFile().getId();
            if (!labelsToChangeById.containsKey(fileId)) {
                continue;
            }
            metadata.setLabel(labelsToChangeById.get(fileId).getLabelAfterChange());
        }
        datasetVersionRepository.save(editVersion);
        return labelToChange;
    }

    // -------------------- PRIVATE --------------------

    private Predicate<String> prepareFileLabelMatcher(String labelPattern) {
        if ("*".equals(labelPattern)) {
            return s -> true;
        } else if (labelPattern.contains("*")) {
            String pattern = labelPattern.replaceAll("\\*", ".*");
            return s -> s.matches(pattern);
        } else {
            return s -> s.contains(labelPattern);
        }
    }
}
