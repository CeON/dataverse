package edu.harvard.iq.dataverse.datavariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;

import edu.harvard.iq.dataverse.UnitTestUtils;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.CategoryMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.VarGroup;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.VariableMetadata;

public class VariableMetadataDDIParserTest {

    @Test
    /**
     * Test XML ddi parser
     */
    public void testDDIReader() {

        XMLStreamReader xmlr = null;

        XMLInputFactory factory = XMLInputFactory.newInstance();
        try {
            String text = UnitTestUtils.readFileToString("xml/dct.xml");
            Reader stringReader = new StringReader(text);
            xmlr = factory.createXMLStreamReader(stringReader);
        } catch (Exception e) {
            xmlr = null;
            assertNotNull(xmlr);
        }
        VariableMetadataDDIParser dti = new VariableMetadataDDIParser();

        Map<Long, VariableMetadata> mapVarToVarMet = new HashMap<Long, VariableMetadata>();
        Map<Long, VarGroup> varGroupMap = new HashMap<Long, VarGroup>();

        try {

            dti.processDataDscr(xmlr, mapVarToVarMet, varGroupMap);

        } catch (XMLStreamException e) {
            assertNotNull(null);
        }

        assertEquals(mapVarToVarMet.size(), 3);
        variableTest(mapVarToVarMet);

        assertEquals(varGroupMap.size(), 2);
        groupTest(varGroupMap);


        return;

    }

    void variableTest(Map<Long, VariableMetadata> vmMap) {

        VariableMetadata vm = vmMap.get(1170L);
        assertNotNull(vm);

        assertEquals(vm.getLabel(), "gender");
        assertEquals(vm.getInterviewinstruction(), "These are interview instructions.");
        assertEquals(vm.getLiteralquestion(), "This is a literal question.");
        assertEquals(vm.getNotes(), "These are notes.\nA lot of them.");
        assertEquals(vm.getUniverse(), "Our universe");
        assertEquals(false, vm.isIsweightvar());
        assertEquals(false, vm.isWeighted());

        testCategoriesVar1(vm);


        vm = vmMap.get(1169L);
        assertNotNull(vm);
        assertEquals(false, vm.isIsweightvar());
        assertEquals(true, vm.isWeighted());
        assertEquals(vm.getLabel(), "age_rollup");

        assertEquals(vm.getInterviewinstruction(), null);
        assertEquals(vm.getLiteralquestion(), null);
        assertEquals(vm.getNotes(), "This variable is weighted.");
        assertEquals(vm.getUniverse(), null);
        assertNotNull(vm.getWeightvariable());
        long idWeight = vm.getWeightvariable().getId();
        assertEquals(idWeight, 1168);

        testCategoriesVar2(vm);

        vm = vmMap.get(1168L);
        assertNotNull(vm);
        assertEquals(true, vm.isIsweightvar());
        assertEquals(false, vm.isWeighted());
        assertEquals(vm.getLabel(), "weight");
        assertEquals(vm.getInterviewinstruction(), null);
        assertEquals(vm.getLiteralquestion(), "Literal question for weight");
        assertEquals(vm.getNotes(), "Notes");
        assertEquals(vm.getUniverse(), null);

        testCategoriesVar3(vm);

    }

    void testCategoriesVar2(VariableMetadata vm) {
        Collection<CategoryMetadata> cms = vm.getCategoriesMetadata();
        assertEquals(cms.size(), 4);

        for (CategoryMetadata cm : cms) {
            switch (cm.getCategory().getValue()) {
                case "1":
                    assertEquals(Math.abs(cm.getWfreq() - 0) <= 0.01, true);
                    break;
                case "2":
                    assertEquals(Math.abs(cm.getWfreq() - 866.44) <= 0.01, true);
                    break;
                case "3":
                    assertEquals(Math.abs(cm.getWfreq() - 1226.35) <= 0.01, true);
                    break;
                case "4":
                    assertEquals(Math.abs(cm.getWfreq() - 952.22) <= 0.01, true);
                    break;
                default:
                    assertEquals(0, 1);
            }
        }
    }

    void testCategoriesVar1(VariableMetadata vm) {
        Collection<CategoryMetadata> cms = vm.getCategoriesMetadata();
        assertEquals(cms.size(), 0);

    }

    void testCategoriesVar3(VariableMetadata vm) {
        Collection<CategoryMetadata> cms = vm.getCategoriesMetadata();
        assertEquals(cms.size(), 0);
    }

    void groupTest(Map<Long, VarGroup> varGroupMap) {

        VarGroup vg1 = varGroupMap.get(1L);
        assertNotNull(vg1);

        //first group
        Set<DataVariable> dvSet1 = new HashSet<DataVariable>();
        DataVariable dv = new DataVariable();
        dv.setId(1169L);
        dvSet1.add(dv);
        dv.setId(1170L);
        dvSet1.add(dv);
        eachGroupTest(vg1, "New Group 1", dvSet1);

        //second group
        VarGroup vg2 = varGroupMap.get(2L);
        assertNotNull(vg2);
        Set<DataVariable> dvSet2 = new HashSet<DataVariable>();
        dv.setId(1168L);
        dvSet2.add(dv);
        eachGroupTest(vg2, "New Group 2", dvSet2);

    }

    void eachGroupTest(VarGroup vg, String label, Set<DataVariable> dvSet) {
        assertEquals(vg.getLabel(), label);
        Set<DataVariable> varsInGroups = vg.getVarsInGroup();
        assertNotNull(varsInGroups);
        assertEquals(varsInGroups.size(), dvSet.size());
        assertEquals(varsInGroups, dvSet);
    }
}
