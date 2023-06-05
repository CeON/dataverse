package edu.harvard.iq.dataverse.harvest.server.web.servlet;

import com.lyncode.xml.exceptions.XmlWriteException;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.export.ExportService;
import edu.harvard.iq.dataverse.export.ExporterType;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.harvest.server.OAIRecordServiceBean;
import edu.harvard.iq.dataverse.harvest.server.OAISetServiceBean;
import edu.harvard.iq.dataverse.harvest.server.xoai.XdataProvider;
import edu.harvard.iq.dataverse.harvest.server.xoai.XgetRecord;
import edu.harvard.iq.dataverse.harvest.server.xoai.XitemRepository;
import edu.harvard.iq.dataverse.harvest.server.xoai.XlistRecords;
import edu.harvard.iq.dataverse.harvest.server.xoai.XsetRepository;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.lang.StringUtils;
import org.dspace.xoai.dataprovider.builder.OAIRequestParametersBuilder;
import org.dspace.xoai.dataprovider.exceptions.OAIException;
import org.dspace.xoai.dataprovider.model.Context;
import org.dspace.xoai.dataprovider.model.MetadataFormat;
import org.dspace.xoai.dataprovider.repository.ItemRepository;
import org.dspace.xoai.dataprovider.repository.Repository;
import org.dspace.xoai.dataprovider.repository.RepositoryConfiguration;
import org.dspace.xoai.dataprovider.repository.SetRepository;
import org.dspace.xoai.model.oaipmh.DeletedRecord;
import org.dspace.xoai.model.oaipmh.Granularity;
import org.dspace.xoai.model.oaipmh.OAIPMH;
import org.dspace.xoai.model.oaipmh.Verb;
import org.dspace.xoai.services.impl.SimpleResumptionTokenFormat;
import org.dspace.xoai.xml.XSISchema;
import org.dspace.xoai.xml.XmlWriter;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

import static org.dspace.xoai.model.oaipmh.OAIPMH.NAMESPACE_URI;
import static org.dspace.xoai.model.oaipmh.OAIPMH.SCHEMA_LOCATION;
import static org.dspace.xoai.xml.XmlWriter.defaultContext;

/**
 * @author Leonid Andreev
 * Dedicated servlet for handling OAI-PMH requests.
 * Uses lyncode XOAI data provider implementation for serving content.
 * The servlet itself is somewhat influenced by the older OCLC OAIcat implementation.
 */
public class OAIServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger("edu.harvard.iq.dataverse.harvest.server.web.servlet.OAIServlet");

    private static final String OAI_PMH = "OAI-PMH";
    private static final String RESPONSEDATE_FIELD = "responseDate";
    private static final String REQUEST_FIELD = "request";
    private static final String DATAVERSE_EXTENDED_METADATA_FORMAT = "dataverse_json";
    private static final String DATAVERSE_EXTENDED_METADATA_INFO = "Custom Dataverse metadata in JSON format (Dataverse4 to Dataverse4 harvesting only)";
    private static final String DATAVERSE_EXTENDED_METADATA_SCHEMA = "JSON schema pending";

    private OAISetServiceBean setService;
    private OAIRecordServiceBean recordService;
    private SettingsServiceBean settingsService;
    private DataverseDao dataverseDao;
    private DatasetDao datasetDao;
    private SystemConfig systemConfig;
    private ExportService exportService;

    private Context xoaiContext;
    private SetRepository setRepository;
    private ItemRepository itemRepository;
    private RepositoryConfiguration repositoryConfiguration;
    private Repository xoaiRepository;
    private XdataProvider dataProvider;

    // -------------------- CONSTRUCTORS --------------------

    public OAIServlet() { }

    @Inject
    public OAIServlet(OAISetServiceBean setService, OAIRecordServiceBean recordService,
                      SettingsServiceBean settingsService, DataverseDao dataverseDao,
                      DatasetDao datasetDao, SystemConfig systemConfig,
                      ExportService exportService) {
        this.setService = setService;
        this.recordService = recordService;
        this.settingsService = settingsService;
        this.dataverseDao = dataverseDao;
        this.datasetDao = datasetDao;
        this.systemConfig = systemConfig;
        this.exportService = exportService;
    }

    // -------------------- GETTERS --------------------

    protected Context getXoaiContext() {
        return xoaiContext;
    }

    protected Repository getXoaiRepository() {
        return xoaiRepository;
    }

    /**
     * Returns a short description of the servlet.
     */
    @Override
    public String getServletInfo() {
        return "Dataverse OAI Servlet";
    }

    // -------------------- LOGIC --------------------

    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        xoaiContext = createContext();

        if (isDataverseOaiExtensionsSupported()) {
            xoaiContext = addDataverseJsonMetadataFormat(xoaiContext);
        }

        setRepository = new XsetRepository(setService);
        itemRepository = new XitemRepository(recordService, datasetDao);

        repositoryConfiguration = createRepositoryConfiguration();

        xoaiRepository = new Repository()
                .withSetRepository(setRepository)
                .withItemRepository(itemRepository)
                .withResumptionTokenFormatter(new SimpleResumptionTokenFormat())
                .withConfiguration(repositoryConfiguration);

        dataProvider = new XdataProvider(getXoaiContext(), getXoaiRepository());
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    protected OAIRequestParametersBuilder newXoaiRequest() {
        return new OAIRequestParametersBuilder();
    }

    public boolean isHarvestingServerEnabled() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.OAIServerEnabled);
    }

    // -------------------- PRIVATE --------------------

    private Context createContext() {
        Context context = new Context();
        addSupportedMetadataFormats(context);
        return context;
    }

    private void addSupportedMetadataFormats(Context context) {
        Map<ExporterType, Exporter> exporters = exportService.getAllExporters();
        for (Exporter exporter : exporters.values()) {
            if (!exporter.isXMLFormat() || !exporter.isHarvestable()) {
                continue;
            }
            MetadataFormat metadataFormat = MetadataFormat.metadataFormat(exporter.getProviderName());
            if (exporter.getXMLNameSpace().isEmpty() && exporter.getXMLSchemaLocation().isEmpty()) {
                continue;
            }
            metadataFormat.withNamespace(exporter.getXMLNameSpace());
            metadataFormat.withSchemaLocation(exporter.getXMLSchemaLocation());
            context.withMetadataFormat(metadataFormat);
        }
    }

    private Context addDataverseJsonMetadataFormat(Context context) {
        MetadataFormat metadataFormat = MetadataFormat.metadataFormat(DATAVERSE_EXTENDED_METADATA_FORMAT);
        metadataFormat.withNamespace(DATAVERSE_EXTENDED_METADATA_INFO);
        metadataFormat.withSchemaLocation(DATAVERSE_EXTENDED_METADATA_SCHEMA);
        context.withMetadataFormat(metadataFormat);
        return context;
    }

    private boolean isDataverseOaiExtensionsSupported() {
        return true;
    }

    private RepositoryConfiguration createRepositoryConfiguration() {
        // TODO:
        // some of the settings below - such as the max list numbers -
        // need to be configurable!

        String dataverseName = dataverseDao.findRootDataverse().getName();
        String repositoryName = StringUtils.isEmpty(dataverseName) || "Root".equals(dataverseName) ? "Test Dataverse OAI Archive" : dataverseName + " Dataverse OAI Archive";
        Date earliestDate = recordService.findEarliestDate();

        RepositoryConfiguration repositoryConfiguration = new RepositoryConfiguration()
                .withRepositoryName(repositoryName)
                .withBaseUrl(systemConfig.getDataverseSiteUrl() + "/oai")
                .withCompression("gzip")        // ?
                .withCompression("deflate")     // ?
                .withAdminEmail(settingsService.getValueForKey(SettingsServiceBean.Key.SystemEmail))
                .withDeleteMethod(DeletedRecord.TRANSIENT)
                .withGranularity(Granularity.Second)
                .withMaxListIdentifiers(100)
                .withMaxListRecords(100)
                .withMaxListSets(100)
                .withEarliestDate(earliestDate != null ? earliestDate : new Date());

        return repositoryConfiguration;
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            if (!isHarvestingServerEnabled()) {
                response.sendError(
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Sorry. OAI Service is disabled on this Dataverse node.");
                return;
            }

            OAIRequestParametersBuilder parametersBuilder = newXoaiRequest();

            for (Object p : request.getParameterMap().keySet()) {
                String parameterName = (String) p;
                String parameterValue = request.getParameter(parameterName);
                parametersBuilder = parametersBuilder.with(parameterName, parameterValue);
            }

            OAIPMH handle = dataProvider.handle(parametersBuilder);
            response.setContentType("text/xml;charset=UTF-8");

            if (isGetRecord(request) && !handle.hasErrors()) {
                writeGetRecord(response, handle);
            } else if (isListRecords(request) && !handle.hasErrors()) {
                writeListRecords(response, handle);
            } else {
                XmlWriter xmlWriter = new XmlWriter(response.getOutputStream());
                xmlWriter.write(handle);
                xmlWriter.flush();
                xmlWriter.close();
            }

        } catch (IOException ex) {
            logger.warning("IO exception in Get; " + ex.getMessage());
            throw new ServletException("IO Exception in Get", ex);
        } catch (OAIException oex) {
            logger.warning("OAI exception in Get; " + oex.getMessage());
            throw new ServletException("OAI Exception in Get", oex);
        } catch (XMLStreamException xse) {
            logger.warning("XML Stream exception in Get; " + xse.getMessage());
            throw new ServletException("XML Stream Exception in Get", xse);
        } catch (XmlWriteException xwe) {
            logger.warning("XML Write exception in Get; " + xwe.getMessage());
            throw new ServletException("XML Write Exception in Get", xwe);
        } catch (Exception e) {
            logger.warning("Unknown exception in Get; " + e.getMessage());
            throw new ServletException("Unknown servlet exception in Get.", e);
        }
    }

    // Custom methods for the potentially expensive GetRecord and ListRecords requests:
    private void writeListRecords(HttpServletResponse response, OAIPMH handle)
            throws IOException {
        OutputStream outputStream = response.getOutputStream();
        outputStream.write(oaiPmhResponseToString(handle).getBytes());
        Verb verb = handle.getVerb();

        if (verb == null) {
            throw new IOException("An error or a valid response must be set");
        }

        if (!verb.getType().equals(Verb.Type.ListRecords)) {
            throw new IOException("writeListRecords() called on a non-ListRecords verb");
        }

        outputStream.write(("<" + verb.getType().displayName() + ">").getBytes());
        outputStream.flush();

        ((XlistRecords) verb).writeToStream(outputStream, exportService, systemConfig.getDataverseSiteUrl());

        outputStream.write(("</" + verb.getType().displayName() + ">").getBytes());
        outputStream.write(("</" + OAI_PMH + ">\n").getBytes());

        outputStream.flush();
        outputStream.close();
    }

    private void writeGetRecord(HttpServletResponse response, OAIPMH handle)
            throws IOException, XmlWriteException, XMLStreamException {
        OutputStream outputStream = response.getOutputStream();
        outputStream.write(oaiPmhResponseToString(handle).getBytes());
        Verb verb = handle.getVerb();

        if (verb == null) {
            throw new IOException("An error or a valid response must be set");
        }

        if (!verb.getType().equals(Verb.Type.GetRecord)) {
            throw new IOException("writeListRecords() called on a non-GetRecord verb");
        }

        outputStream.write(("<" + verb.getType().displayName() + ">").getBytes());
        outputStream.flush();

        ((XgetRecord) verb).writeToStream(outputStream, exportService, systemConfig.getDataverseSiteUrl());

        outputStream.write(("</" + verb.getType().displayName() + ">").getBytes());
        outputStream.write(("</" + OAI_PMH + ">\n").getBytes());

        outputStream.flush();
        outputStream.close();

    }

    /**
     * This function produces the string representation of the top level,
     * "service" record of an OAIPMH response (i.e., the header that precedes
     * the actual "payload" record, such as <GetRecord>, <ListIdentifiers>,
     * <ListRecords>, etc.
     */
   private String oaiPmhResponseToString(OAIPMH handle) {
        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            XmlWriter writer = new XmlWriter(byteOutputStream, defaultContext());

            writer.writeStartElement(OAI_PMH);
            writer.writeDefaultNamespace(NAMESPACE_URI);
            writer.writeNamespace(XSISchema.PREFIX, XSISchema.NAMESPACE_URI);
            writer.writeAttribute(XSISchema.PREFIX, XSISchema.NAMESPACE_URI, "schemaLocation",
                                  NAMESPACE_URI + " " + SCHEMA_LOCATION);

            writer.writeElement(RESPONSEDATE_FIELD, handle.getResponseDate(), Granularity.Second);
            writer.writeElement(REQUEST_FIELD, handle.getRequest());
            writer.writeEndElement();
            writer.flush();
            writer.close();

            return byteOutputStream.toString().replaceFirst("</" + OAI_PMH + ">", "");
        } catch (Exception ex) {
            logger.warning("caught exception trying to convert an OAIPMH response header to string: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    private boolean isGetRecord(HttpServletRequest request) {
        return "GetRecord".equals(request.getParameter("verb"));
    }

    private boolean isListRecords(HttpServletRequest request) {
        return "ListRecords".equals(request.getParameter("verb"));
    }
}
