package edu.harvard.iq.dataverse.datafile.file;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvJsonCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FileMetadataService {

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- LOGIC --------------------

    public List<FileMetadata> updateFileMetadatasWithProvFreeform(List<FileMetadata> fileMetadatasToUpdate, Map<String, UpdatesEntry> provenanceUpdates) {
        ArrayList<FileMetadata> fileMetadataCopy = new ArrayList<>(fileMetadatasToUpdate);

        fileMetadataCopy.stream()
                .map(fileMetadata -> Tuple.of(fileMetadata, provenanceUpdates.get(fileMetadata.getDataFile().getChecksumValue())))
                .filter(this::isProvFreeFormAvailable)
                .forEach(fileMetadataWithProvenance -> fileMetadataWithProvenance._1().setProvFreeForm(fileMetadataWithProvenance._2.getProvFreeform()));

        return fileMetadataCopy;
    }

    public boolean saveStagedProvJson(boolean saveContext, List<FileMetadata> fileMetadatas, Map<String, UpdatesEntry> provenanceUpdates) throws AbstractApiBean.WrappedResponse {
        boolean commandsCalled = false;
        Set<String> finalChecksums = fileMetadatas.stream()
                .map(fileMetadata -> fileMetadata.getDataFile().getChecksumValue())
                .collect(Collectors.toSet());

        /*
            Some of the files for which the users may have uploaded provenance metadata
            may have already been dropped (for example, if these are brand new files
            and some of them have since failed to be saved in permanenent storage);
            This may be the case when this method is called from EditDatafilesPage or
            DatasetPage (in CREATE mode). Then the list of fileMetadatas that are
            still legit is passed to the method, and we make sure to only save the
            entries for the files on this list.
        */
        Set<Map.Entry<String, UpdatesEntry>> provenanceUpdatesForChange = provenanceUpdates.entrySet().stream()
                .filter(provMap -> finalChecksums.contains(provMap.getKey()))
                .collect(Collectors.toSet());

        for (Map.Entry<String, UpdatesEntry> m : provenanceUpdatesForChange) {
            String checksumValue = m.getKey();
            UpdatesEntry mapEntry = m.getValue();
            DataFile df = mapEntry.getDataFile();
            Option<String> provString = mapEntry.getProvJson();

            boolean entrySaved = false;
            if (mapEntry.getDeleteJson()) {
                df = commandEngine.submit((new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(), df, saveContext)));
                entrySaved = true;
            } else if (provString.isDefined()) {
                df = commandEngine.submit(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(), df, provString.get(), df.getProvEntityName(), saveContext));
                entrySaved = true;
            }

            if (entrySaved) {
                mapEntry.setDataFile(df);
                provenanceUpdates.put(checksumValue, mapEntry); //Updates the datafile to the latest.
                commandsCalled = true;
            }

        }

        return commandsCalled;
    }

    // -------------------- PRIVATE --------------------

    private boolean isProvFreeFormAvailable(Tuple2<FileMetadata, UpdatesEntry> fileMetadataWithProvenance) {
        return fileMetadataWithProvenance._2() != null && fileMetadataWithProvenance._2().getProvFreeform() != null;
    }
}
