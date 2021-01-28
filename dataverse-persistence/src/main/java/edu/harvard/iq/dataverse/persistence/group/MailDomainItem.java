package edu.harvard.iq.dataverse.persistence.group;


import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class MailDomainItem {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private MailDomainGroup owner;

    private String domain;

    @Enumerated(EnumType.STRING)
    private MailDomainProcessingType processingType;
}
