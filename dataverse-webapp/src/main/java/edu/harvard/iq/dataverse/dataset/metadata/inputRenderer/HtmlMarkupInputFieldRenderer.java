package edu.harvard.iq.dataverse.dataset.metadata.inputRenderer;

import edu.harvard.iq.dataverse.persistence.dataset.InputRendererType;

public class HtmlMarkupInputFieldRenderer implements InputFieldRenderer {

    // -------------------- GETTERS --------------------
    
    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@link InputRendererType#HTML_MARKUP}
     */
    @Override
    public InputRendererType getType() {
        return InputRendererType.HTML_MARKUP;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}
     */
    @Override
    public boolean renderInTwoColumns() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}
     */
    @Override
    public boolean isHidden() {
        return false;
    }

}
