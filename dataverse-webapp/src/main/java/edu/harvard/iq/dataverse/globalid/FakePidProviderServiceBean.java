package edu.harvard.iq.dataverse.globalid;

import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.GlobalId;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Stateless
public class FakePidProviderServiceBean extends AbstractGlobalIdServiceBean {

    @Override
    public boolean alreadyExists(DvObject dvo) throws Exception {
        return true;
    }

    @Override
    public boolean alreadyExists(GlobalId globalId) throws Exception {
        return false;
    }

    @Override
    public boolean registerWhenPublished() {
        return false;
    }

    @Override
    public List<String> getProviderInformation() {
        ArrayList<String> providerInfo = new ArrayList<>();
        String providerName = "FAKE";
        String providerLink = "http://dataverse.org";
        providerInfo.add(providerName);
        providerInfo.add(providerLink);
        return providerInfo;
    }

    @Override
    public String createIdentifier(DvObject dvo) throws Throwable {
        return "fakeIdentifier";
    }

    @Override
    public Map<String, String> getIdentifierMetadata(DvObject dvo) {
        Map<String, String> map = new HashMap<>();
        return map;
    }

    @Override
    public String modifyIdentifierTargetURL(DvObject dvo) throws Exception {
        return "fakeModifyIdentifierTargetURL";
    }

    @Override
    public void deleteIdentifier(DvObject dvo) throws Exception {
        // no-op
    }

    @Override
    public Map<String, String> lookupMetadataFromIdentifier(String protocol, String authority, String identifier) {
        Map<String, String> map = new HashMap<>();
        return map;
    }

    @Override
    public boolean publicizeIdentifier(DvObject studyIn) {
        if (studyIn.getIdentifier() == null || studyIn.getIdentifier().isEmpty()) {
            generateIdentifier(studyIn);
        }
        return true;
    }

}
