/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.dataaccess;

import edu.harvard.iq.dataverse.persistence.datafile.DataFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Leonid Andreev
 */
public class InputStreamIO extends StorageIO<DataFile> {

    private long size;

    public InputStreamIO(InputStream inputStream, long size, String fileName, String mimeType) {
        super();
        this.setIsLocalFile(false);
        this.setInputStream(inputStream);
        setChannel(Channels.newChannel(inputStream));
        this.size = size;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public long getSize() throws IOException {
        return size;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public void open(DataAccessOption... options) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public void savePath(Path fileSystemPath) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public void delete() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public Channel openAuxChannel(String auxItemTag, DataAccessOption... options) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public boolean isAuxObjectCached(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public long getAuxObjectSize(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public Path getAuxObjectAsPath(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public void backupAsAux(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public void savePathAsAux(Path fileSystemPath, String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public void saveInputStreamAsAux(InputStream inputStream, String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public List<String> listAuxObjects() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this is a stream-only DataAccess IO object, it has no auxiliary objects associated with it.");
    }


    @Override
    public void deleteAuxObject(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this is a stream-only DataAccess IO object, it has no auxiliary objects associated with it.");
    }

    @Override
    public void deleteAllAuxObjects() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this is a stream-only DataAccess IO object, it has no auxiliary objects associated with it.");
    }


    @Override
    public String getStorageLocation() {
        return null;
    }

    @Override
    public Path getFileSystemPath() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this is a stream-only DataAccess IO object, it has no local filesystem path associated with it.");
    }

    @Override
    public boolean exists() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: there is no output stream associated with this object.");
    }

    @Override
    public InputStream getAuxFileAsInputStream(String auxItemTag) {
        throw new UnsupportedOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public void revertBackupAsAux(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public boolean isMD5CheckSupported() {
        throw new UnsupportedOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public String getMD5() throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }

    @Override
    public String getAuxObjectMD5(String auxItemTag) throws IOException {
        throw new UnsupportedDataAccessOperationException("InputStreamIO: this method is not supported in this DataAccess driver.");
    }


}
