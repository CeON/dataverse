package edu.harvard.iq.dataverse.persistence.group;


import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import java.util.Set;

@NamedQueries({
        @NamedQuery(name = "MailDomainGroup.findAll", query = "SELECT m FROM MailDomainGroup m")
})
@Entity
public class MailDomainGroup extends PersistedGlobalGroup {

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MailDomainItem> domainItems;

    // -------------------- LOGIC --------------------

    @Override
    public boolean isEditable() {
        return false;
    }
}
