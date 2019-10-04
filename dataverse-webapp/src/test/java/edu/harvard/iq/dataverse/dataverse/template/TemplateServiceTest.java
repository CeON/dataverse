package edu.harvard.iq.dataverse.dataverse.template;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import io.vavr.control.Try;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TemplateServiceTest {

    @InjectMocks
    private TemplateService templateService;

    @Mock
    private EjbDataverseEngine ejbDataverseEngine;

    @Mock
    private DataverseRequestServiceBean dvRequest;

    @Mock
    private TemplateDao templateDao;

    @BeforeEach
    public void setUp() throws CommandException {
        templateService.setCurrentTime(LocalDateTime.of(LocalDate.of(2019, 12, 12), LocalTime.of(13, 15)));

        when(ejbDataverseEngine.submit(any(Command.class)))
                .then(InvocationOnMock::getMock);
    }

    // -------------------- TESTS --------------------

    @Test
    public void shouldSuccessfullyDeleteTemplate() throws CommandException {
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
        Assert.assertFalse(updatedDataverse.isFailure());
        Assert.assertTrue(updatedDataverse.get().getTemplates().isEmpty());
        Assert.assertNull(updatedDataverse.get().getDefaultTemplate());
    }

    @Test
    public void deleteTemplateShouldFail() throws CommandException {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        dataverse.setTemplates(new ArrayList<>());

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenThrow(new RuntimeException());

        //when
        Try<Dataverse> updatedDataverse = templateService.deleteTemplate(dataverse, template);

        //then
        Assert.assertTrue(updatedDataverse.isFailure());
    }

    @Test
    public void shouldSuccessfullyCloneTemplate() throws CommandException {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        template.setCreateTime(new Date());
        dataverse.setTemplates(new ArrayList<>());
        dataverse.getTemplates().add(template);
        LocalDateTime testTime = LocalDateTime.of(LocalDate.of(2019, 12, 12), LocalTime.of(13, 15));

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenReturn(template);

        //when
        Try<Template> cloneOperation = templateService.cloneTemplate(template, dataverse);

        Optional<Template> createdTemplate = dataverse.getTemplates().stream()
                .filter(template1 -> template1.getCreateTime().equals(Timestamp.valueOf(testTime)))
                .findAny();

        //then
        Assert.assertFalse(cloneOperation.isFailure());
        Assert.assertEquals(0, createdTemplate.get().getUsageCount().longValue());
        Assert.assertEquals(Timestamp.valueOf(testTime), createdTemplate.get().getCreateTime());
        Assert.assertEquals(2, dataverse.getTemplates().size());

    }

    @Test
    public void templateCloningShouldFail() throws CommandException {
        //given
        Dataverse dataverse = new Dataverse();
        Template template = new Template();
        dataverse.setTemplates(new ArrayList<>());

        when(ejbDataverseEngine.submit(any(Command.class)))
                .thenThrow(new RuntimeException());

        //when
        Try<Template> cloneOperation = templateService.cloneTemplate(template, dataverse);

        //then
        Assert.assertTrue(cloneOperation.isFailure());
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
        Assert.assertEquals(dataverse.getDefaultTemplate(), template);

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
        Assert.assertNull(dataverse.getDefaultTemplate());
    }

    @Test
    public void updateDefaultTemplates_ForInheritedValue() {
        //given
        Dataverse dataverse = new Dataverse();
        Dataverse dataverseOwner = new Dataverse();

        Template template = new Template();
        template.setName("nice template");
        dataverseOwner.setDefaultTemplate(template);
        dataverse.setOwner(dataverseOwner);

        //when
        templateService.updateDataverseTemplates(true, dataverse);

        //then
        Assert.assertEquals(dataverse.getDefaultTemplate(), template);

    }

    @Test
    public void updateDefaultTemplates_ForNonInheritedValue() {
        //given
        Dataverse dataverse = new Dataverse();
        Dataverse dataverseOwner = new Dataverse();

        Template template = new Template();
        template.setName("nice template");
        dataverseOwner.setTemplates(Lists.newArrayList(template));
        dataverse.setOwner(dataverseOwner);
        dataverse.setDefaultTemplate(template);

        //when
        templateService.updateDataverseTemplates(false, dataverse);

        //then
        Assert.assertNull(dataverse.getDefaultTemplate());

    }
}