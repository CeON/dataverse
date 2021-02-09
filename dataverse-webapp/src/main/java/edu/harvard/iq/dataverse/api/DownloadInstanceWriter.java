package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.DataConverter;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.dataaccess.InputStreamIO;
import edu.harvard.iq.dataverse.dataaccess.S3AccessIO;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.dataaccess.StoredOriginalFile;
import edu.harvard.iq.dataverse.dataaccess.TabularSubsetGenerator;
import edu.harvard.iq.dataverse.datafile.page.WholeDatasetDownloadLogger;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateGuestbookResponseCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Leonid Andreev
 */
@Provider
public class DownloadInstanceWriter implements MessageBodyWriter<DownloadInstance> {

    private static final Logger logger = LoggerFactory.getLogger(DownloadInstanceWriter.class);

    @Inject
    private DataConverter dataConverter;

    @Inject
    private WholeDatasetDownloadLogger datasetDownloadLogger;

    private DataAccess dataAccess = DataAccess.dataAccess();

    @Override
    public boolean isWriteable(Class<?> clazz, Type type, Annotation[] annotation, MediaType mediaType) {
        return clazz == DownloadInstance.class;
    }

    @Override
    public long getSize(DownloadInstance di, Class<?> clazz, Type type, Annotation[] annotation, MediaType mediaType) {
        return -1;
        //return getFileSize(di);
    }

    @Override
    public void writeTo(DownloadInstance di, Class<?> clazz, Type type, Annotation[] annotation, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream outstream) throws IOException, WebApplicationException {
        if (di.getDownloadInfo() != null && di.getDownloadInfo().getDataFile() != null) {

            DataFile dataFile = di.getDownloadInfo().getDataFile();
            StorageIO<DataFile> storageIO = dataAccess.getStorageIO(dataFile);
            boolean checkForWholeDatasetDownload = false;

            if (storageIO != null) {
                try {
                    storageIO.open();
                } catch (IOException ioex) {
                    //throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
                    logger.info("Datafile {}: Failed to locate and/or open physical file. Error message: {}", dataFile.getId(), ioex.getLocalizedMessage());
                    throw new NotFoundException("Datafile " + dataFile.getId() + ": Failed to locate and/or open physical file.");
                }

                if (di.getConversionParam() != null) {
                    // Image Thumbnail and Tabular data conversion: 
                    // NOTE: only supported on local files, as of 4.0.2!
                    // NOTE: should be supported on all files for which StorageIO drivers
                    // are available (but not on harvested files1) -- L.A. 4.6.2

                    if (di.getConversionParam().equals("imageThumb") && !dataFile.isHarvested()) {
                        if ("".equals(di.getConversionParamValue())) {
                            storageIO = ImageThumbConverter.getImageThumbnailAsInputStream(storageIO, ImageThumbConverter.DEFAULT_THUMBNAIL_SIZE);
                        } else {
                            try {
                                int size = new Integer(di.getConversionParamValue());
                                if (size > 0) {
                                    storageIO = ImageThumbConverter.getImageThumbnailAsInputStream(storageIO, size);
                                }
                            } catch (java.lang.NumberFormatException ex) {
                                storageIO = ImageThumbConverter.getImageThumbnailAsInputStream(storageIO, ImageThumbConverter.DEFAULT_THUMBNAIL_SIZE);
                            }

                            // and, since we now have tabular data files that can 
                            // have thumbnail previews... obviously, we don't want to 
                            // add the variable header to the image stream!

                            storageIO.setNoVarHeader(Boolean.TRUE);
                            storageIO.setVarHeader(null);
                        }
                    } else if (dataFile.isTabularData()) {
                        logger.debug("request for tabular data download;");
                        // We can now generate thumbnails for some tabular data files (specifically, 
                        // tab files tagged as "geospatial"). We are going to assume that you can 
                        // do only ONE thing at a time - request the thumbnail for the file, or 
                        // request any tabular-specific services. 

                        // For majority of the cases in this branch this should be enabled
                        checkForWholeDatasetDownload = true;

                        if (di.getConversionParam().equals("noVarHeader")) {
                            logger.debug("tabular data with no var header requested");
                            storageIO.setNoVarHeader(Boolean.TRUE);
                            storageIO.setVarHeader(null);
                        } else if (di.getConversionParam().equals("format")) {
                            // Conversions, and downloads of "stored originals" are 
                            // now supported on all DataFiles for which StorageIO 
                            // access drivers are available.

                            if ("original".equals(di.getConversionParamValue())) {
                                logger.debug("stored original of an ingested file requested");
                                storageIO = StoredOriginalFile.retreive(storageIO);
                            } else {
                                // Other format conversions: 
                                logger.debug("format conversion on a tabular file requested (" + di.getConversionParamValue() + ")");
                                String requestedMimeType = di.getServiceFormatType(di.getConversionParam(), di.getConversionParamValue());
                                if (requestedMimeType == null) {
                                    // default mime type, in case real type is unknown;
                                    // (this shouldn't happen in real life - but just in case): 
                                    requestedMimeType = "application/octet-stream";
                                }
                                storageIO =
                                        dataConverter.performFormatConversion(dataFile,
                                                                              storageIO,
                                                                              di.getConversionParamValue(), requestedMimeType);
                            }
                        } else if (di.getConversionParam().equals("subset")) {
                            logger.debug("processing subset request.");

                            // TODO: 
                            // If there are parameters on the list that are 
                            // not valid variable ids, or if the do not belong to 
                            // the datafile referenced - I simply skip them; 
                            // perhaps I should throw an invalid argument exception 
                            // instead. 

                            if (di.getExtraArguments() != null && di.getExtraArguments().size() > 0) {
                                logger.debug("processing extra arguments list of length " + di.getExtraArguments().size());
                                List<Integer> variablePositionIndex = new ArrayList<>();
                                String subsetVariableHeader = null;
                                for (int i = 0; i < di.getExtraArguments().size(); i++) {
                                    DataVariable variable = (DataVariable) di.getExtraArguments().get(i);
                                    if (variable != null) {
                                        if (variable.getDataTable().getDataFile().getId().equals(dataFile.getId())) {
                                            logger.debug("adding variable id " + variable.getId() + " to the list.");
                                            variablePositionIndex.add(variable.getFileOrder());
                                            if (subsetVariableHeader == null) {
                                                subsetVariableHeader = variable.getName();
                                            } else {
                                                subsetVariableHeader = subsetVariableHeader.concat("\t");
                                                subsetVariableHeader = subsetVariableHeader.concat(variable.getName());
                                            }
                                        } else {
                                            logger.warn("variable does not belong to this data file.");
                                        }
                                    }
                                }

                                if (variablePositionIndex.size() > 0) {
                                    // As we're going to download subset of data, we don't check for whole dataset download
                                    checkForWholeDatasetDownload = false;

                                    try {
                                        File tempSubsetFile = File.createTempFile("tempSubsetFile", ".tmp");
                                        TabularSubsetGenerator tabularSubsetGenerator = new TabularSubsetGenerator();
                                        tabularSubsetGenerator.subsetFile(storageIO.getInputStream(), tempSubsetFile.getAbsolutePath(), variablePositionIndex, dataFile.getDataTable().getCaseQuantity(), "\t");

                                        if (tempSubsetFile.exists()) {
                                            FileInputStream subsetStream = new FileInputStream(tempSubsetFile);
                                            long subsetSize = tempSubsetFile.length();

                                            InputStreamIO subsetStreamIO = new InputStreamIO(subsetStream, subsetSize);
                                            logger.debug("successfully created subset output stream.");
                                            subsetVariableHeader = subsetVariableHeader.concat("\n");
                                            subsetStreamIO.setVarHeader(subsetVariableHeader);

                                            String tabularFileName = storageIO.getFileName();

                                            if (tabularFileName != null && tabularFileName.endsWith(".tab")) {
                                                tabularFileName = tabularFileName.replaceAll("\\.tab$", "-subset.tab");
                                            } else if (tabularFileName != null && !"".equals(tabularFileName)) {
                                                tabularFileName = tabularFileName.concat("-subset.tab");
                                            } else {
                                                tabularFileName = "subset.tab";
                                            }

                                            subsetStreamIO.setFileName(tabularFileName);
                                            subsetStreamIO.setMimeType(storageIO.getMimeType());
                                            storageIO = subsetStreamIO;
                                        } else {
                                            storageIO = null;
                                        }
                                    } catch (IOException ioex) {
                                        storageIO = null;
                                    }
                                }
                            } else {
                                logger.debug("empty list of extra arguments.");
                            }
                        }
                    }


                    if (storageIO == null) {
                        throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
                    }
                } else {
                    // There's no conversion etc., so we should enable check
                    checkForWholeDatasetDownload = true;

                    if (storageIO instanceof S3AccessIO && !(dataFile.isTabularData()) && isRedirectToS3()) {
                        // definitely close the (still open) S3 input stream, 
                        // since we are not going to use it. The S3 documentation
                        // emphasizes that it is very important not to leave these
                        // lying around un-closed, since they are going to fill 
                        // up the S3 connection pool!
                        try {
                            storageIO.getInputStream().close();
                        } catch (IOException ioex) {
                            logger.warn("Exception during closing input stream: ", ioex);
                        }
                        // [attempt to] redirect: 
                        String redirect_url_str;
                        try {
                            redirect_url_str = ((S3AccessIO) storageIO).generateTemporaryS3Url();
                        } catch (IOException ioex) {
                            redirect_url_str = null;
                        }

                        if (redirect_url_str == null) {
                            throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
                        }

                        logger.info("Data Access API: direct S3 url: " + redirect_url_str);
                        URI redirect_uri;

                        try {
                            redirect_uri = new URI(redirect_url_str);
                        } catch (URISyntaxException ex) {
                            logger.info("Data Access API: failed to create S3 redirect url (" + redirect_url_str + ")");
                            redirect_uri = null;
                        }
                        if (redirect_uri != null) {
                            // increment the download count, if necessary:
                            if (di.getGbr() != null) {
                                try {
                                    logger.debug("writing guestbook response, for an S3 download redirect.");
                                    Command<?> cmd = new CreateGuestbookResponseCommand(di.getDataverseRequestService().getDataverseRequest(), di.getGbr(), di.getGbr().getDataFile().getOwner());
                                    di.getCommand().submit(cmd);
                                } catch (CommandException e) {
                                    logger.warn("Exception during create guestbook response command: ", e);
                                }
                            }

                            if (checkForWholeDatasetDownload) {
                                datasetDownloadLogger.incrementLogIfDownloadingWholeDataset(Collections.singletonList(dataFile));
                            }

                            // finally, issue the redirect:
                            Response response = Response.seeOther(redirect_uri).build();
                            logger.info("Issuing redirect to the file location on S3.");
                            throw new RedirectionException(response);
                        }
                        throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
                    }
                }

                InputStream instream = storageIO.getInputStream();
                if (instream != null) {
                    // headers:

                    String fileName = storageIO.getFileName();
                    String mimeType = storageIO.getMimeType();

                    // Provide both the "Content-disposition" and "Content-Type" headers,
                    // to satisfy the widest selection of browsers out there. 

                    httpHeaders.add("Content-disposition", "attachment; filename=\"" + fileName + "\"");
                    httpHeaders.add("Content-Type", mimeType + "; name=\"" + fileName + "\"");

                    long contentSize;
                    boolean useChunkedTransfer = false;
                    //if ((contentSize = getFileSize(di, storageIO.getVarHeader())) > 0) {
                    if ((contentSize = getContentSize(storageIO)) > 0) {
                        logger.debug("Content size (retrieved from the AccessObject): " + contentSize);
                        httpHeaders.add("Content-Length", contentSize);
                    } else {
                        //httpHeaders.add("Transfer-encoding", "chunked");
                        //useChunkedTransfer = true;
                    }

                    // (the httpHeaders map must be modified *before* writing any
                    // data in the output stream!)

                    int bufsize;
                    byte[] bffr = new byte[4 * 8192];
                    byte[] chunkClose = "\r\n".getBytes();

                    // before writing out any bytes from the input stream, flush
                    // any extra content, such as the variable header for the 
                    // subsettable files: 

                    if (storageIO.getVarHeader() != null) {
                        if (storageIO.getVarHeader().getBytes().length > 0) {
                            if (useChunkedTransfer) {
                                String chunkSizeLine = String.format("%x\r\n", storageIO.getVarHeader().getBytes().length);
                                outstream.write(chunkSizeLine.getBytes());
                            }
                            outstream.write(storageIO.getVarHeader().getBytes());
                            if (useChunkedTransfer) {
                                outstream.write(chunkClose);
                            }
                        }
                    }

                    while ((bufsize = instream.read(bffr)) != -1) {
                        if (useChunkedTransfer) {
                            String chunkSizeLine = String.format("%x\r\n", bufsize);
                            outstream.write(chunkSizeLine.getBytes());
                        }
                        outstream.write(bffr, 0, bufsize);
                        if (useChunkedTransfer) {
                            outstream.write(chunkClose);
                        }
                    }

                    if (useChunkedTransfer) {
                        String chunkClosing = "0\r\n\r\n";
                        outstream.write(chunkClosing.getBytes());
                    }


                    logger.debug("di conversion param: " + di.getConversionParam() + ", value: " + di.getConversionParamValue());

                    // Downloads of thumbnail images (scaled down, low-res versions of graphic image files) and 
                    // "preprocessed metadata" records for tabular data files are NOT considered "real" downloads, 
                    // so these should not produce guestbook entries: 

                    boolean meaningfulDownload = !(isThumbnailDownload(di) || isPreprocessedMetadataDownload(di));

                    // If file is a thumbnail or preprocessed metadata we won't count it as whole dataset download
                    checkForWholeDatasetDownload = checkForWholeDatasetDownload && meaningfulDownload;

                    if (di.getGbr() != null && meaningfulDownload) {
                        try {
                            logger.debug("writing guestbook response.");
                            Command<?> cmd = new CreateGuestbookResponseCommand(di.getDataverseRequestService().getDataverseRequest(), di.getGbr(), di.getGbr().getDataFile().getOwner());
                            di.getCommand().submit(cmd);
                        } catch (CommandException ce) {
                            logger.warn("Exception while writing into guestbook: ", ce);
                        }
                    } else {
                        logger.debug("not writing guestbook response");
                    }

                    if (checkForWholeDatasetDownload) {
                        datasetDownloadLogger.incrementLogIfDownloadingWholeDataset(Collections.singletonList(dataFile));
                    }

                    instream.close();
                    outstream.close();
                    return;
                }
            }
        }

        throw new WebApplicationException(Response.Status.NOT_FOUND);

    }

    private boolean isThumbnailDownload(DownloadInstance downloadInstance) {
        if (downloadInstance == null) {
            return false;
        }

        if (downloadInstance.getConversionParam() == null) {
            return false;
        }

        return downloadInstance.getConversionParam().equals("imageThumb");
    }

    private boolean isPreprocessedMetadataDownload(DownloadInstance downloadInstance) {
        if (downloadInstance == null) {
            return false;
        }

        if (downloadInstance.getConversionParam() == null) {
            return false;
        }

        if (downloadInstance.getConversionParamValue() == null) {
            return false;
        }

        return downloadInstance.getConversionParam().equals("format") && downloadInstance.getConversionParamValue().equals("prep");
    }

    private long getContentSize(StorageIO<?> accessObject) {

        try {
            long contentSize = accessObject.getSize();

            if (accessObject.getVarHeader() != null) {
                contentSize += accessObject.getVarHeader().getBytes().length;
            }
            return contentSize;

        } catch(IOException e) {
            logger.warn("Unable to obtain content size", e);
        }
        return -1;
    }

    private boolean isRedirectToS3() {
        String optionValue = System.getProperty("dataverse.files.s3-download-redirect");
        return "true".equalsIgnoreCase(optionValue);
    }

}
