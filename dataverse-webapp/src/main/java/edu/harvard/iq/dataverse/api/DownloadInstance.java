/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.dataaccess.OptionalAccessService;
import edu.harvard.iq.dataverse.persistence.guestbook.GuestbookResponse;

import java.util.List;

/**
 * Used alongside {@link DownloadInstanceWriter} to return file instance in api.
 *
 * @author Leonid Andreev
 */
public class DownloadInstance {

    private List<Object> extraArguments = null;

    public List<Object> getExtraArguments() {
        return extraArguments;
    }

    public void setExtraArguments(List<Object> extraArguments) {
        this.extraArguments = extraArguments;
    }


    private DownloadInfo downloadInfo = null;
    private String conversionParam = null;
    private String conversionParamValue = null;

    private EjbDataverseEngine command;

    private DataverseRequestServiceBean dataverseRequestService;

    private GuestbookResponse gbr;


    public DownloadInstance() {

    }

    public DownloadInstance(DownloadInfo info) {
        this.downloadInfo = info;
    }

    public DownloadInfo getDownloadInfo() {
        return downloadInfo;
    }

    public void setDownloadInfo(DownloadInfo info) {
        this.downloadInfo = info;
    }

    public String getConversionParam() {
        return conversionParam;
    }

    public void setConversionParam(String param) {
        this.conversionParam = param;
    }

    public String getConversionParamValue() {
        return conversionParamValue;
    }

    public void setConversionParamValue(String paramValue) {
        this.conversionParamValue = paramValue;
    }

    // Move this method into the DownloadInfo instead -- ?
    public Boolean checkIfServiceSupportedAndSetConverter(String serviceArg, String serviceArgValue) {
        if (downloadInfo == null || serviceArg == null) {
            return false;
        }

        List<OptionalAccessService> servicesAvailable = downloadInfo.getServicesAvailable();

        for (OptionalAccessService dataService : servicesAvailable) {
            if (dataService != null) {
                if (serviceArg.equals("variables")) {
                    // Special case for the subsetting parameter (variables=<LIST>):
                    if ("subset".equals(dataService.getServiceName())) {
                        conversionParam = "subset";
                        conversionParamValue = serviceArgValue;
                        return true;
                    }
                } else if (serviceArg.equals("noVarHeader")) {
                    // Another special case available for tabular ("subsettable") data files - 
                    // "do not add variable header" flag:
                    if ("true".equalsIgnoreCase(serviceArgValue) || "1".equalsIgnoreCase(serviceArgValue)) {
                        if ("subset".equals(dataService.getServiceName())) {
                            this.conversionParam = serviceArg;
                            return true;
                        }
                    }
                } else if ("imageThumb".equals(serviceArg)) {
                    if ("true".equals(serviceArgValue)) {
                        this.conversionParam = serviceArg;
                        this.conversionParamValue = "";
                    } else {
                        this.conversionParam = serviceArg;
                        this.conversionParamValue = serviceArgValue;
                    }
                    return true;
                }
                String argValuePair = serviceArg + "=" + serviceArgValue;
                if (argValuePair.startsWith(dataService.getServiceArguments())) {
                    conversionParam = serviceArg;
                    conversionParamValue = serviceArgValue;
                    return true;
                }
                //}
            }
        }
        return false;
    }

    public String getServiceFormatType(String serviceArg, String serviceArgValue) {
        if (downloadInfo == null || serviceArg == null) {
            return null;
        }

        List<OptionalAccessService> servicesAvailable = downloadInfo.getServicesAvailable();

        for (OptionalAccessService dataService : servicesAvailable) {
            if (dataService != null) {
                // Special case for the subsetting parameter (variables=<LIST>):
                if (serviceArg.equals("variables")) {
                    if ("subset".equals(dataService.getServiceName())) {
                        conversionParam = "subset";
                        conversionParamValue = serviceArgValue;
                        return dataService.getMimeType();
                    }
                } else if (serviceArg.equals("imageThumb")) {
                    return "image/png";
                } else {
                    String argValuePair = serviceArg + "=" + serviceArgValue;
                    if (argValuePair.equals(dataService.getServiceArguments())) {
                        conversionParam = serviceArg;
                        conversionParamValue = serviceArgValue;
                        return dataService.getMimeType();
                    }
                }
            }
        }
        return null;
    }


    public EjbDataverseEngine getCommand() {
        return command;
    }

    public void setCommand(EjbDataverseEngine command) {
        this.command = command;
    }

    public GuestbookResponse getGbr() {
        return gbr;
    }

    public void setGbr(GuestbookResponse gbr) {
        this.gbr = gbr;
    }


    public DataverseRequestServiceBean getDataverseRequestService() {
        return dataverseRequestService;
    }

    public void setDataverseRequestService(DataverseRequestServiceBean dataverseRequestService) {
        this.dataverseRequestService = dataverseRequestService;
    }

}
