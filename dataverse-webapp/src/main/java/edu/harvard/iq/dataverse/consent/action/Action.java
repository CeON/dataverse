package edu.harvard.iq.dataverse.consent.action;

import edu.harvard.iq.dataverse.consent.ConsentActionDto;

public interface Action {

    public void executeAction(ConsentActionDto consentActionDto);
}
