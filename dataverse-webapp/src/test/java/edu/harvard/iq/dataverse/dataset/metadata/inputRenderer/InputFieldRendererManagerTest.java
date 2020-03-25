package edu.harvard.iq.dataverse.dataset.metadata.inputRenderer;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.buttonaction.FieldButtonActionHandler;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.InputRendererType;
import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.enterprise.inject.Instance;

import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class InputFieldRendererManagerTest {

    @InjectMocks
    private InputFieldRendererManager inputFieldRendererManager;

    @Mock
    private Instance<InputFieldRendererFactory<?>> inputRendererFactories;
    
    @Mock
    private InputFieldRendererFactory<TextInputFieldRenderer> rendererFactory1;
    
    @Mock
    private InputFieldRendererFactory<VocabSelectInputFieldRenderer> rendererFactory2;
    
    
    @BeforeEach
    public void beforeEach() {
        when(rendererFactory1.isFactoryForType()).thenReturn(InputRendererType.TEXT);
        when(rendererFactory2.isFactoryForType()).thenReturn(InputRendererType.VOCABULARY_SELECT);
        when(inputRendererFactories.iterator()).thenReturn(IteratorUtils.arrayIterator(rendererFactory1, rendererFactory2));
        
        inputFieldRendererManager.postConstruct();
    }
    
    // -------------------- TESTS --------------------
    
    @Test
    public void obtainRenderer() {
        // given
        DatasetFieldType fieldType = new DatasetFieldType();
        fieldType.setInputRendererType(InputRendererType.VOCABULARY_SELECT);
        fieldType.setInputRendererOptions("{}");
        
        VocabSelectInputFieldRenderer renderer = Mockito.mock(VocabSelectInputFieldRenderer.class);
        
        when(rendererFactory2.createRenderer(Mockito.any())).thenReturn(renderer);
        
        // when
        InputFieldRenderer retRenderer = inputFieldRendererManager.obtainRenderer(fieldType);
        
        // then
        assertSame(renderer, retRenderer);
    }
    
    @Test
    public void obtainRenderersByType() {
        // given
        DatasetFieldType fieldType1 = new DatasetFieldType();
        fieldType1.setId(1L);
        fieldType1.setInputRendererType(InputRendererType.VOCABULARY_SELECT);
        fieldType1.setInputRendererOptions("{}");
        
        DatasetFieldType fieldType2 = new DatasetFieldType();
        fieldType1.setId(2L);
        fieldType2.setInputRendererType(InputRendererType.TEXT);
        fieldType2.setInputRendererOptions("{}");
        
        DatasetField field1 = new DatasetField();
        field1.setDatasetFieldType(fieldType1);

        DatasetField field2 = new DatasetField();
        field2.setDatasetFieldType(fieldType2);

        DatasetField field3 = new DatasetField();
        field3.setDatasetFieldType(fieldType2);

        TextInputFieldRenderer textRenderer = Mockito.mock(TextInputFieldRenderer.class);
        VocabSelectInputFieldRenderer vocabRenderer = Mockito.mock(VocabSelectInputFieldRenderer.class);

        when(rendererFactory1.createRenderer(Mockito.any())).thenReturn(textRenderer);
        when(rendererFactory2.createRenderer(Mockito.any())).thenReturn(vocabRenderer);
        
        // when
        Map<DatasetFieldType, InputFieldRenderer> retRenderers = inputFieldRendererManager.obtainRenderersByType(
                Lists.newArrayList(field1, field2, field3));
        
        // then
        assertEquals(2, retRenderers.size());
        assertSame(vocabRenderer, retRenderers.get(fieldType1));
        assertSame(textRenderer, retRenderers.get(fieldType2));
    }
}
