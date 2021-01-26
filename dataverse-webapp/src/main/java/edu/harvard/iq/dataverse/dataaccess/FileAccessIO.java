/*
   Copyright (C) 2005-2012, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
   Version 3.0.
*/

package edu.harvard.iq.dataverse.dataaccess;

import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;



public class FileAccessIO<T extends DvObject> extends StorageIO<T> {

    private static final Logger logger = Logger.getLogger(FileAccessIO.class.getCanonicalName());

    public FileAccessIO(String filesDirectory) {
        this((T) null, filesDirectory);
    }

    public FileAccessIO(T dvObject, String filesDirectory) {
        super(dvObject);

        this.setIsLocalFile(true);
        filesRootDirectory = filesDirectory;
    }

    // "Direct" File Access IO, opened on a physical file not associated with
    // a specific DvObject
    public FileAccessIO(String storageLocation, String filesDirectory) {
        physicalPath = Paths.get(storageLocation);
        filesRootDirectory = filesDirectory;
    }

    private Path physicalPath = null;
    private String filesRootDirectory;

    @Override
    public void open(DataAccessOption... options) throws IOException {

        if (isWriteAccessRequested(options)) {
            isWriteAccess = true;
            isReadAccess = false;
        } else {
            isWriteAccess = false;
            isReadAccess = true;
        }

        if (dvObject instanceof DataFile) {
            DataFile dataFile = this.getDataFile();

            if (dataFile.getStorageIdentifier() == null || "".equals(dataFile.getStorageIdentifier())) {
                throw new IOException("Data Access: No local storage identifier defined for this datafile.");
            }

            if (isReadAccess) {
                FileInputStream fin = openLocalFileAsInputStream();

                this.setInputStream(fin);
                setChannel(fin.getChannel());

                if (dataFile.getContentType() != null
                        && dataFile.getContentType().equals("text/tab-separated-values")
                        && dataFile.isTabularData()
                        && dataFile.getDataTable() != null
                        && (!this.noVarHeader())) {

                    List<DataVariable> datavariables = dataFile.getDataTable().getDataVariables();
                    String varHeaderLine = generateVariableHeader(datavariables);
                    this.setVarHeader(varHeaderLine);
                }
            } else if (isWriteAccess) {
                // Creates a new directory as needed for a dataset.
                if (dataFile.getOwner().getFileSystemDirectory(filesRootDirectory) != null
                        && !Files.exists(dataFile.getOwner().getFileSystemDirectory(filesRootDirectory))) {
                    Files.createDirectories(dataFile.getOwner().getFileSystemDirectory(filesRootDirectory));
                }
                FileOutputStream fout = openLocalFileAsOutputStream();

                this.setOutputStream(fout);
                setChannel(fout.getChannel());
            }

            this.setMimeType(dataFile.getContentType());

            try {
                this.setFileName(dataFile.getFileMetadata().getLabel());
            } catch (Exception ex) {
                this.setFileName("unknown");
            }
        } else if (dvObject instanceof Dataset) {
            //This case is for uploading a dataset related auxiliary file 
            //e.g. image thumbnails/metadata exports
            //TODO: do we really need to do anything here? should we return the dataset directory?
            Dataset dataset = this.getDataset();
            if (isReadAccess) {
                //TODO: Not necessary for dataset as there is no files associated with this
                //  FileInputStream fin = openLocalFileAsInputStream();
//                Path path= dataset.getFileSystemDirectory();                    
//                if (path == null) {
//                    throw new IOException("Failed to locate Dataset"+dataset.getIdentifier());
//                }
//
//                this.setInputStream(fin);  
            } else if (isWriteAccess) {
                //this checks whether a directory for a dataset exists 
                if (dataset.getFileSystemDirectory(filesRootDirectory) != null
                        && !Files.exists(dataset.getFileSystemDirectory(filesRootDirectory))) {
                    Files.createDirectories(dataset.getFileSystemDirectory(filesRootDirectory));
                }
                dataset.setStorageIdentifier("file://" + dataset.getAuthority() + "/" + dataset.getIdentifier());
            }

        } else if (dvObject instanceof Dataverse) {
            throw new IOException("Data Access: Storage driver does not support dvObject type Dataverse yet");
        } else {
            throw new IOException("Data Access: Invalid DvObject type");
        }
    }

    @Override
    public void savePath(Path fileSystemPath) throws IOException {

        // Since this is a local fileystem file, we can use the
        // quick NIO Files.copy method: 

        Path outputPath = getFileSystemPath();

        if (outputPath == null) {
            throw new FileNotFoundException("FileAccessIO: Could not locate aux file for writing.");
        }
        Files.copy(fileSystemPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Channel openAuxChannel(String auxItemTag, DataAccessOption... options) throws IOException {

        Path auxPath = getAuxObjectAsPath(auxItemTag);

        if (isWriteAccessRequested(options)) {
            FileOutputStream auxOut = new FileOutputStream(auxPath.toFile());

            return auxOut.getChannel();
        }

        // Read access requested.
        // Check if this Aux object is cached; and if so, open for reading:

        if (!auxPath.toFile().exists()) {
            throw new FileNotFoundException("Auxiliary File " + dvObject.getStorageIdentifier() + "." + auxItemTag + " does not exist.");
        }

        FileInputStream auxIn = new FileInputStream(auxPath.toFile());

        return auxIn.getChannel();

    }

    @Override
    public boolean isAuxObjectCached(String auxItemTag) throws IOException {
        // Check if the file exists:

        Path auxPath = getAuxObjectAsPath(auxItemTag);

        return auxPath.toFile().exists();
    }

    @Override
    public long getAuxObjectSize(String auxItemTag) throws IOException {
        Path auxPath = getAuxObjectAsPath(auxItemTag);

        if (!auxPath.toFile().exists()) {
            throw new FileNotFoundException("Aux file does not exist.");
        }

        return auxPath.toFile().length();
    }

    @Override
    public Path getAuxObjectAsPath(String auxItemTag) throws IOException {

        if (auxItemTag == null || "".equals(auxItemTag)) {
            throw new IOException("Null or invalid Auxiliary Object Tag.");
        }

        String datasetDirectory = getDatasetDirectory();

        if (dvObject.getStorageIdentifier() == null || "".equals(dvObject.getStorageIdentifier())) {
            throw new IOException("Data Access: No local storage identifier defined for this datafile.");
        }
        Path auxPath = null;
        if (dvObject instanceof DataFile) {
            auxPath = Paths.get(datasetDirectory, dvObject.getStorageIdentifier() + "." + auxItemTag);
        } else if (dvObject instanceof Dataset) {
            auxPath = Paths.get(datasetDirectory, auxItemTag);
        } else if (dvObject instanceof Dataverse) {
        } else {
            throw new IOException("Aux path could not be generated for " + auxItemTag);
        }

        if (auxPath == null) {
            throw new IOException("Invalid Path location for the auxiliary file " + dvObject.getStorageIdentifier() + "." + auxItemTag);
        }

        return auxPath;
    }

    @Override
    public void backupAsAux(String auxItemTag) throws IOException {
        Path auxPath = getAuxObjectAsPath(auxItemTag);

        Files.move(getFileSystemPath(), auxPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void revertBackupAsAux(String auxItemTag) throws IOException {
        Path auxPath = getAuxObjectAsPath(auxItemTag);
        Files.move(auxPath, getFileSystemPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // this method copies a local filesystem Path into this DataAccess Auxiliary location:
    @Override
    public void savePathAsAux(Path fileSystemPath, String auxItemTag) throws IOException {
        // quick Files.copy method: 
        try {
            Path auxPath = getAuxObjectAsPath(auxItemTag);
            Files.copy(fileSystemPath, auxPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
        }
    }

    @Override
    public void saveInputStreamAsAux(InputStream inputStream, String auxItemTag, Long filesize) throws IOException {
        saveInputStreamAsAux(inputStream, auxItemTag);
    }

    @Override
    public void saveInputStreamAsAux(InputStream inputStream, String auxItemTag) throws IOException {

        // Since this is a local fileystem file, we can use the
        // quick NIO Files.copy method: 

        File outputFile = getAuxObjectAsPath(auxItemTag).toFile();

        if (outputFile == null) {
            throw new FileNotFoundException("FileAccessIO: Could not locate aux file for writing.");
        }

        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            int read;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }
        inputStream.close();
    }

    @Override
    public List<String> listAuxObjects() throws IOException {
        if (this.getDataFile() == null) {
            throw new IOException("This FileAccessIO object hasn't been properly initialized.");
        }

        List<Path> cachedFiles = listCachedFiles();

        if (cachedFiles == null) {
            return null;
        }

        List<String> cachedFileNames = new ArrayList<>();
        String baseName = this.getDataFile().getStorageIdentifier() + ".";
        for (Path auxPath : cachedFiles) {
            cachedFileNames.add(auxPath.getFileName().toString().substring(baseName.length()));
        }

        return cachedFileNames;
    }

    @Override
    public void deleteAuxObject(String auxItemTag) throws IOException {
        Path auxPath = getAuxObjectAsPath(auxItemTag);
        Files.delete(auxPath);
    }

    @Override
    public void deleteAllAuxObjects() throws IOException {
        List<Path> cachedFiles = listCachedFiles();

        if (cachedFiles == null) {
            return;
        }

        for (Path auxPath : cachedFiles) {
            Files.delete(auxPath);
        }

    }


    @Override
    public String getStorageLocation() {
        // For a local file, the "storage location" is a complete, absolute
        // filesystem path, with the "file://" prefix:

        try {
            Path testPath = getFileSystemPath();
            if (testPath != null) {
                return "file://" + testPath.toString();
            }
        } catch (IOException ioex) {
            // just return null, below:
        }

        return null;
    }

    @Override
    public Path getFileSystemPath() throws IOException {
        if (physicalPath != null) {
            return physicalPath;
        }

        String datasetDirectory = getDatasetDirectory();

        if (dvObject.getStorageIdentifier() == null || "".equals(dvObject.getStorageIdentifier())) {
            throw new IOException("Data Access: No local storage identifier defined for this datafile.");
        }

        physicalPath = Paths.get(datasetDirectory, dvObject.getStorageIdentifier());
        return physicalPath;

    }

    @Override
    public boolean exists() throws IOException {
        if (getFileSystemPath() == null) {
            throw new FileNotFoundException("FileAccessIO: invalid Access IO object.");
        }

        return getFileSystemPath().toFile().exists();
    }

    @Override
    public long getSize() throws IOException {
        return getFileSystemPath().toFile().length();
    }

    /*@Override
    public void delete() throws IOException {
        Path victim = getFileSystemPath();
        
        if (victim != null) {
            Files.delete(victim);
        } else {
            throw new IOException("Could not locate physical file location for the Filesystem object.");
        }
    }*/

    @Override
    public void delete() throws IOException {
        if (!isDirectAccess()) {
            throw new IOException("Direct Access IO must be used to permanently delete stored file objects");
        }

        if (physicalPath == null) {
            throw new IOException("Attempted delete on an unspecified physical path");
        }

        deleteAllAuxObjects();

        Files.delete(physicalPath);

    }

    // Auxilary helper methods, filesystem access-specific:

    private long getLocalFileSize() {
        long fileSize = -1;

        try {
            File testFile = getFileSystemPath().toFile();
            if (testFile != null) {
                fileSize = testFile.length();
            }
            return fileSize;
        } catch (IOException ex) {
            return -1;
        }

    }

    FileInputStream openLocalFileAsInputStream() throws IOException {
        return new FileInputStream(getFileSystemPath().toFile());
    }

    FileOutputStream openLocalFileAsOutputStream() throws IOException {
        return new FileOutputStream(getFileSystemPath().toFile());
    }

    private String getDatasetDirectory() throws IOException {
        if (dvObject == null) {
            throw new IOException("No DvObject defined in the Data Access Object");
        }

        Path datasetDirectoryPath = null;

        if (dvObject instanceof Dataset) {
            datasetDirectoryPath = this.getDataset().getFileSystemDirectory(filesRootDirectory);
        } else if (dvObject instanceof DataFile) {
            datasetDirectoryPath = this.getDataFile().getOwner().getFileSystemDirectory(filesRootDirectory);
        } else if (dvObject instanceof Dataverse) {
            throw new IOException("FileAccessIO: Dataverses are not a supported dvObject");
        }

        if (datasetDirectoryPath == null) {
            throw new IOException("Could not determine the filesystem directory of the parent dataset.");
        }
        String datasetDirectory = datasetDirectoryPath.toString();

        if (dvObject.getStorageIdentifier() == null || dvObject.getStorageIdentifier().isEmpty()) {
            throw new IOException("Data Access: No local storage identifier defined for this datafile.");
        }

        return datasetDirectory;
    }

    private List<Path> listCachedFiles() throws IOException {
        List<Path> auxItems = new ArrayList<>();

        // cached files for a given datafiles are stored on the filesystem
        // as <filesystemname>.*; for example, <filename>.thumb64 or 
        // <filename>.RData.

        String baseName;
        Path datasetDirectoryPath;

        if (isDirectAccess()) {
            baseName = physicalPath.getFileName().toString();
            datasetDirectoryPath = physicalPath.getParent();

        } else {
            if (this.getDataFile() == null || this.getDataFile().getStorageIdentifier() == null || this.getDataFile().getStorageIdentifier().isEmpty()) {
                throw new IOException("Null or invalid DataFile in FileAccessIO object.");
            }

            baseName = this.getDataFile().getStorageIdentifier();

            datasetDirectoryPath = this.getDataFile().getOwner().getFileSystemDirectory(filesRootDirectory.toString());
        }

        if (datasetDirectoryPath == null) {
            throw new IOException("Could not determine the filesystem directory of the parent dataset.");
        }

        DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path file) throws IOException {
                return (file.getFileName() != null
                        && file.getFileName().toString().startsWith(baseName + "."));
            }
        };

        DirectoryStream<Path> dirStream = Files.newDirectoryStream(datasetDirectoryPath, filter);

        if (dirStream != null) {
            for (Path filePath : dirStream) {
                auxItems.add(filePath);
            }
        }

        dirStream.close();

        return auxItems;
    }

    @Override
    public InputStream getAuxFileAsInputStream(String auxItemTag) throws IOException {
        InputStream in = null;

        if (this.isAuxObjectCached(auxItemTag)) {
            Path path = getAuxObjectAsPath(auxItemTag);
            in = Files.newInputStream(path);
        }
        return in;
    }

    @Override
    public boolean isMD5CheckSupported() {
        return false;
    }

    @Override
    public String getMD5() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

}