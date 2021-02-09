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
import edu.harvard.iq.dataverse.util.SystemConfig;

import java.io.IOException;

/**
 * @author Leonid Andreev
 */

public class DataAccess {

    private static final String DEFAULT_STORAGE_DRIVER_IDENTIFIER = System.getProperty("dataverse.files.storage-driver-id");

    private static final DataAccess INSTANCE = new DataAccess();

    private S3ClientFactory s3ClientFactory;
    
    // -------------------- CONSTRUCTORS --------------------
    
    private DataAccess() {
        this.s3ClientFactory = new S3ClientFactory();
    }
    
    // -------------------- LOGIC --------------------

    public static DataAccess dataAccess() {
        return INSTANCE;
    }

    /**
     * The getStorageIO() methods initialize StorageIO objects for
     * datafiles that are already saved using one of the supported Dataverse DataAccess IO drivers.
     */
    public <T extends DvObject> StorageIO<T> getStorageIO(T dvObject) throws IOException {

        if (dvObject == null
                || dvObject.getStorageIdentifier() == null
                || dvObject.getStorageIdentifier().isEmpty()) {
            throw new IOException("getDataAccessObject: null or invalid datafile.");
        }

        if (dvObject.getStorageIdentifier().startsWith("file://")
                || (!dvObject.getStorageIdentifier().matches("^[a-z][a-z0-9]*://.*"))) {
            return new FileAccessIO<>(dvObject, SystemConfig.getFilesDirectoryStatic());
        } else if (dvObject.getStorageIdentifier().startsWith("s3://")) {
            return new S3AccessIO<>(dvObject, s3ClientFactory.getClient());
        } else if (dvObject.getStorageIdentifier().startsWith("tmp://")) {
            throw new IOException("DataAccess IO attempted on a temporary file that hasn't been permanently saved yet.");
        }

        throw new IOException("getDataAccessObject: Unsupported storage method.");
    }

    /**
     * Experimental extension of the StorageIO system allowing direct access to
     * stored physical files that may not be associated with any DvObjects
     */
    public StorageIO getDirectStorageIO(String storageLocation) throws IOException {
        if (storageLocation.startsWith("file://")) {
            return new FileAccessIO(storageLocation.substring(7), SystemConfig.getFilesDirectoryStatic());
        } else if (storageLocation.startsWith("s3://")) {
            return new S3AccessIO<>(storageLocation.substring(5), s3ClientFactory.getClient());
        }

        throw new IOException("getDirectStorageIO: Unsupported storage method.");
    }

    /**
     * createDataAccessObject() methods create a *new*, empty DataAccess objects,
     * for saving new, not yet saved datafiles.
     */
    public <T extends DvObject> StorageIO<T> createNewStorageIO(T dvObject, String storageTag) throws IOException {

        return createNewStorageIO(dvObject, storageTag, DEFAULT_STORAGE_DRIVER_IDENTIFIER);
    }

    // -------------------- PRIVATE --------------------

    private <T extends DvObject> StorageIO<T> createNewStorageIO(T dvObject, String storageTag, String driverIdentifier) throws IOException {
        if (dvObject == null
                || storageTag == null
                || storageTag.isEmpty()) {
            throw new IOException("getDataAccessObject: null or invalid datafile.");
        }

        StorageIO<T> storageIO = null;

        dvObject.setStorageIdentifier(storageTag);

        if (driverIdentifier == null) {
            driverIdentifier = "file";
        }

        if (driverIdentifier.equals("file")) {
            storageIO = new FileAccessIO<>(dvObject, SystemConfig.getFilesDirectoryStatic());
        } else if (driverIdentifier.equals("s3")) {
            storageIO = new S3AccessIO<>(dvObject, s3ClientFactory.getClient());
        } else {
            throw new IOException("createDataAccessObject: Unsupported storage method " + driverIdentifier);
        }

        storageIO.open(DataAccessOption.WRITE_ACCESS);
        return storageIO;
    }


}
