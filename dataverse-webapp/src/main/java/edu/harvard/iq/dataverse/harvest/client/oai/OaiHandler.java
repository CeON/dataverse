/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.harvest.client.oai;

import org.dspace.xoai.model.oaipmh.Granularity;
import org.dspace.xoai.model.oaipmh.Header;
import org.dspace.xoai.model.oaipmh.MetadataFormat;
import org.dspace.xoai.model.oaipmh.Set;
import org.dspace.xoai.serviceprovider.ServiceProvider;
import org.dspace.xoai.serviceprovider.client.HttpOAIClient;
import org.dspace.xoai.serviceprovider.exceptions.BadArgumentException;
import org.dspace.xoai.serviceprovider.exceptions.IdDoesNotExistException;
import org.dspace.xoai.serviceprovider.exceptions.InvalidOAIResponse;
import org.dspace.xoai.serviceprovider.exceptions.NoSetHierarchyException;
import org.dspace.xoai.serviceprovider.model.Context;
import org.dspace.xoai.serviceprovider.parameters.ListIdentifiersParameters;
import edu.harvard.iq.dataverse.harvest.client.FastGetRecord;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @author Leonid Andreev
 */
public class OaiHandler implements Serializable {

    public OaiHandler() {

    }

    public OaiHandler(String baseOaiUrl) {
        this.baseOaiUrl = baseOaiUrl;
    }

    public OaiHandler(HarvestingClient harvestingClient) throws OaiHandlerException, IdDoesNotExistException {
        this.baseOaiUrl = harvestingClient.getHarvestingUrl();

        if (StringUtils.isEmpty(baseOaiUrl)) {
            throw new OaiHandlerException("Valid OAI url is needed to create a handler");
        }
        this.baseOaiUrl = harvestingClient.getHarvestingUrl();

        this.metadataPrefix = harvestingClient.getMetadataPrefix();
        if (StringUtils.isEmpty(metadataPrefix)) {
            throw new OaiHandlerException("HarvestingClient must have a metadataPrefix to create a handler");
        }

        if (!StringUtils.isEmpty(harvestingClient.getHarvestingSet())) {
            try {
                this.setName = URLEncoder.encode(harvestingClient.getHarvestingSet(), "UTF-8");
            } catch (UnsupportedEncodingException uee) {
                throw new OaiHandlerException("Harvesting set: unsupported (non-UTF8) encoding");
            }
        }

        this.fromDate = harvestingClient.getLastNonEmptyHarvestTime();

        this.harvestingClient = harvestingClient;
    }

    private String baseOaiUrl; //= harvestingClient.getHarvestingUrl();
    private String metadataPrefix; // = harvestingClient.getMetadataPrefix();
    private MetadataFormat metadataFormat;
    private String setName;
    private Date fromDate;

    private ServiceProvider serviceProvider;

    private HarvestingClient harvestingClient;

    public String getSetName() {
        return setName;
    }

    public String getBaseOaiUrl() {
        return baseOaiUrl;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public MetadataFormat getMetadataFormat() {
        return metadataFormat;
    }

    public HarvestingClient getHarvestingClient() {
        return this.harvestingClient;
    }

    public void withSetName(String setName) {
        this.setName = setName;
    }

    public void withFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public void setHarvestingClient(HarvestingClient harvestingClient) {
        this.harvestingClient = harvestingClient;
    }


    private ServiceProvider getServiceProvider() throws OaiHandlerException {
        if (serviceProvider == null) {
            if (baseOaiUrl == null) {
                throw new OaiHandlerException("Could not instantiate Service Provider, missing OAI server URL.");
            }
            Context context = new Context();

            context.withBaseUrl(baseOaiUrl);
            context.withGranularity(Granularity.Second);
            context.withOAIClient(new HttpOAIClient(baseOaiUrl));

            serviceProvider = new ServiceProvider(context);
        }

        return serviceProvider;
    }

    OaiHandler withServiceProvider(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        return this;
    }

    /**
     * Fetches all available metadata formats from the remote and searches for the set metadata prefix.
     */
    public OaiHandler withFetchedMetadataFormat() throws OaiHandlerException, IdDoesNotExistException {
        if (this.metadataPrefix == null) {
            throw new OaiHandlerException("Can't fetch metadata format, prefix not set.");
        }

        this.metadataFormat = runListMetadataFormats().stream()
                .filter(format -> metadataPrefix.equals(format.getMetadataPrefix()))
                .findFirst()
                .orElseThrow(() -> new OaiHandlerException("Couldn't find meta data format with prefix:" + metadataPrefix));

        return this;
    }

    public List<String> runListSets() throws OaiHandlerException {

        ServiceProvider sp = getServiceProvider();

        Iterator<Set> setIter;

        try {
            setIter = sp.listSets();
        } catch (NoSetHierarchyException nshe) {
            return null;
        } catch (InvalidOAIResponse ior) {
            throw new OaiHandlerException("No valid response received from the OAI server.");
        }

        List<String> sets = new ArrayList<>();

        while (setIter.hasNext()) {
            Set set = setIter.next();
            String setSpec = set.getSpec();
            /*
            if (set.getDescriptions() != null && !set.getDescriptions().isEmpty()) {
                Description description = set.getDescriptions().get(0);

            }
            */
            if (!StringUtils.isEmpty(setSpec)) {
                sets.add(setSpec);
            }
        }

        if (sets.size() < 1) {
            return null;
        }
        return sets;

    }

    public List<MetadataFormat> runListMetadataFormats() throws OaiHandlerException, IdDoesNotExistException {
        ServiceProvider sp = getServiceProvider();

        Iterator<MetadataFormat> mfIter;

        try {
            mfIter = sp.listMetadataFormats();
        } catch (InvalidOAIResponse ior) {
            throw new OaiHandlerException("No valid response received from the OAI server.");
        }

        List<MetadataFormat> formats = new ArrayList<>();

        while (mfIter.hasNext()) {
            MetadataFormat format = mfIter.next();
            String formatName = format.getMetadataPrefix();
            if (!StringUtils.isEmpty(formatName)) {
                formats.add(format);
            }
        }

        return formats;
    }

    public Iterator<Header> runListIdentifiers() throws OaiHandlerException {
        ListIdentifiersParameters parameters = buildListIdentifiersParams();
        try {
            return getServiceProvider().listIdentifiers(parameters);
        } catch (BadArgumentException bae) {
            throw new OaiHandlerException("BadArgumentException thrown when attempted to run ListIdentifiers");
        }

    }

    public FastGetRecord runGetRecord(String identifier) throws OaiHandlerException {
        if (StringUtils.isEmpty(this.baseOaiUrl)) {
            throw new OaiHandlerException("Attempted to execute GetRecord without server URL specified.");
        }
        if (StringUtils.isEmpty(this.metadataPrefix)) {
            throw new OaiHandlerException("Attempted to execute GetRecord without metadataPrefix specified");
        }

        try {
            return new FastGetRecord(this.baseOaiUrl, identifier, this.metadataPrefix);
        } catch (ParserConfigurationException pce) {
            throw new OaiHandlerException("ParserConfigurationException executing GetRecord: " + pce.getMessage());
        } catch (SAXException se) {
            throw new OaiHandlerException("SAXException executing GetRecord: " + se.getMessage());
        } catch (TransformerException te) {
            throw new OaiHandlerException("TransformerException executing GetRecord: " + te.getMessage());
        } catch (IOException ioe) {
            throw new OaiHandlerException("IOException executing GetRecord: " + ioe.getMessage());
        }
    }


    private ListIdentifiersParameters buildListIdentifiersParams() throws OaiHandlerException {
        ListIdentifiersParameters mip = ListIdentifiersParameters.request();

        if (StringUtils.isEmpty(this.metadataPrefix)) {
            throw new OaiHandlerException("Attempted to create a ListIdentifiers request without metadataPrefix specified");
        }
        mip.withMetadataPrefix(metadataPrefix);

        if (this.fromDate != null) {
            mip.withFrom(this.fromDate);
        }

        if (!StringUtils.isEmpty(this.setName)) {
            mip.withSetSpec(this.setName);
        }

        return mip;
    }

    public void runIdentify() {
        // not implemented yet
        // (we will need it, both for validating the remote server,
        // and to learn about its extended capabilities)
    }
}
