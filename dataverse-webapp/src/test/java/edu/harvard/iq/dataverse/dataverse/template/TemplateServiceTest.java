package edu.harvard.iq.dataverse.dataverse.template;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.impl.CreateTemplateCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseTemplateCommand;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import io.vavr.control.Try;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TemplateServiceTest {

    @Mock private EjbDataverseEngine ejbDataverseEngine;
    @Mock private DataverseRequestServiceBean dvRequest;
    @Mock private TemplateDao templateDao;

    @InjectMocks
    private TemplateService templateService;

    private int testTime = 1576156500;

    @BeforeEach
    public void setUp()  {
        templateService.setClock(Clock.fixed(Instant.ofEpochSecond(testTime), ZoneId.systemDefault()));

        when(ejbDataverseEngine.submit(any(Command.class)))
                .then(InvocationOnMock::getMock);
    }

    // -------------------- TESTS --------------------


    @Test
    public void createTemplate() {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        template.setId(11L);

        //when
        when(ejbDataverseEngine.submit(any(CreateTemplateCommand.class))).thenReturn(template);

        Try<Template> createdTemplate = templateService.createTemplate(dataverse, template);

        //then
        Assertions.assertTrue(createdTemplate.isSuccess());
        Assertions.assertEquals(0, createdTemplate.get().getUsageCount().longValue());
        Assertions.assertEquals(Instant.ofEpochSecond(testTime), createdTemplate.get().getCreateTime().toInstant());
        Assertions.assertTrue(dataverse.getTemplates().contains(template));

    }

    @Test
    public void updateTemplate() {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();

        //when
        when(ejbDataverseEngine.submit(any(UpdateDataverseTemplateCommand.class))).thenReturn(template);

        Try<Template> updatedTemplate = templateService.updateTemplate(dataverse, template);

        //then
        Assertions.assertTrue(updatedTemplate.isSuccess());
    }

    @Test
    public void shouldSuccessfullyDeleteTemplate()  {
        //given
        Dataverse dataverse = new Dataverse();
        dataverse.setTemplates(new ArrayList<>());

        Template template = new Template();
        dataverse.getTemplates().add(template);
        dataverse.setDefaultTemplate(template);

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenReturn(dataverse);

        //when
        Try<Dataverse> updatedDataverse = templateService.deleteTemplate(dataverse, template);

        //then
        Assertions.assertFalse(updatedDataverse.isFailure());
        Assertions.assertTrue(updatedDataverse.get().getTemplates().isEmpty());
        Assertions.assertNull(updatedDataverse.get().getDefaultTemplate());
    }

    @Test
    public void deleteTemplateShouldFail()  {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        dataverse.setTemplates(new ArrayList<>());

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenThrow(new RuntimeException());

        //when
        Try<Dataverse> updatedDataverse = templateService.deleteTemplate(dataverse, template);

        //then
        Assertions.assertTrue(updatedDataverse.isFailure());
    }

    @Test
    public void shouldSuccessfullyCopyAndMergeTemplate()  {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        template.setCreateTime(new Date());
        dataverse.setTemplates(new ArrayList<>());
        dataverse.getTemplates().add(template);
        Instant testTime = Instant.ofEpochSecond(1576156500);

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenReturn(template);

        //when
        Try<Template> cloneOperation = templateService.mergeIntoDataverse(dataverse, templateService.copyTemplate(template));

        Optional<Template> createdTemplate = dataverse.getTemplates().stream()
                .filter(template1 -> template1.getCreateTime().equals(Timestamp.from(testTime)))
                .findAny();

        //then
        Assertions.assertFalse(cloneOperation.isFailure());
        Assertions.assertEquals(0, createdTemplate.get().getUsageCount().longValue());
        Assertions.assertEquals(Timestamp.from(testTime), createdTemplate.get().getCreateTime());
        Assertions.assertEquals(2, dataverse.getTemplates().size());

    }

    @Test
    public void templateCopyAndMergeShouldFail()  {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        dataverse.setTemplates(new ArrayList<>());

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenThrow(new RuntimeException());

        //when
        Try<Template> cloneOperation = templateService.mergeIntoDataverse(dataverse, templateService.copyTemplate(template));

        //then
        Assertions.assertTrue(cloneOperation.isFailure());
    }


    @Test
    public void shouldSuccessfullyMakeTemplateDefaultForDataverse() {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        template.setName("nice template");

        //when
        templateService.makeTemplateDefaultForDataverse(dataverse, template);

        //then
        Assertions.assertEquals(dataverse.getDefaultTemplate(), template);

    }

    @Test
    public void removeDataverseDefaultTemplate() {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        template.setName("nice template");
        dataverse.setDefaultTemplate(template);

        //when
        templateService.removeDataverseDefaultTemplate(dataverse);

        //then
        Assertions.assertNull(dataverse.getDefaultTemplate());
    }

    @Test
    public void updateDefaultTemplates_ForInheritedValue()  {
        //given
        Dataverse dataverse = new Dataverse();
        Dataverse dataverseOwner = new Dataverse();

        Template template = new Template();
        template.setName("nice template");
        dataverseOwner.setDefaultTemplate(template);
        dataverse.setOwner(dataverseOwner);

        //when
        templateService.updateTemplateInheritance(dataverse, true);

        //then
        Assertions.assertEquals(dataverse.getDefaultTemplate(), template);
        Mockito.verify(ejbDataverseEngine, times(1)).submit(any());

    }

    @Test
    public void updateDefaultTemplates_ForNonInheritedValue()  {
        //given
        Dataverse dataverse = new Dataverse();
        Dataverse dataverseOwner = new Dataverse();

        Template template = new Template();
        template.setName("nice template");
        dataverseOwner.setTemplates(Lists.newArrayList(template));
        dataverse.setOwner(dataverseOwner);
        dataverse.setDefaultTemplate(template);

        //when
        templateService.updateTemplateInheritance(dataverse, false);
        Mockito.verify(ejbDataverseEngine, times(1)).submit(any());

        //then
        Assertions.assertNull(dataverse.getDefaultTemplate());

    }

}