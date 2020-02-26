/*
 * Copyright (c) 2020. BEST S.A. and/or its affiliates. All rights reserved.
 */
package edu.harvard.iq.dataverse.util;

import edu.harvard.iq.dataverse.ingest.IngestUtilTest;
import org.dataverse.unf.UNFUtil;
import org.dataverse.unf.UnfException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UNFUtilTest {

	@Test
	public void calculateUNF() {
		String[] unfValues = {"a", "b", "c"};
		String datasetUnfValue = null;
		try {
			datasetUnfValue = UNFUtil.calculateUNF(unfValues);
		} catch (IOException | UnfException ex) {
			Logger.getLogger(UNFUtilTest.class.getName()).log(Level.SEVERE, null, ex);
		}
		assertEquals("UNF:6:FWBO/a1GcxDnM3fNLdzrHw==", datasetUnfValue);
	}
}
