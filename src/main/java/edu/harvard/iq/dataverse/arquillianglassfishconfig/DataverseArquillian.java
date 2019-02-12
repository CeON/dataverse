package edu.harvard.iq.dataverse.arquillianglassfishconfig;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

public class DataverseArquillian extends Arquillian {

    public DataverseArquillian(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    public void run(RunNotifier notifier) {
        ArquillianGlassfishConfigurator arquillianGlassfishConfigurator =
                new ArquillianGlassfishConfigurator();

        arquillianGlassfishConfigurator.createTempGlassfishResources();
        super.run(notifier);
    }
}
