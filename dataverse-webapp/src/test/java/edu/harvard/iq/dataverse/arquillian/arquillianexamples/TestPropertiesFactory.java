package edu.harvard.iq.dataverse.arquillian.arquillianexamples;

import edu.harvard.iq.dataverse.settings.FileSettingLocations;
import edu.harvard.iq.dataverse.settings.FileSettingLocationsFactory;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Specializes;

public class TestPropertiesFactory extends FileSettingLocationsFactory {

    @Override
    @Produces @Specializes
    public FileSettingLocations buildSettingLocations() {
        FileSettingLocations locations = super.buildSettingLocations();
        locations.addLocation(FileSettingLocations.SettingLocationType.CLASSPATH,
                "/config/dataverse.test.properties",
                true);
        return locations;
    }
}
