package edu.harvard.iq.dataverse.dataset.difference;

import com.google.common.collect.Lists;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

class MultipleItemDiffTest {

    @Test
    public void generatePairs() {
        //given
        ArrayList<String> twoElements = Lists.newArrayList("a", "b");
        ArrayList<String> oneElement = Lists.newArrayList("1");

        //when

        List<Tuple2<Option<String>, Option<String>>> pairs = new MultipleItemDiff<>(twoElements,
                                                                                      oneElement).generatePairs();
        //then

        Assertions.assertAll(() -> assertEquals(2, pairs.size()),
                             () -> assertEquals(pairs.get(0)._1.getOrNull(), "a"),
                             () -> assertEquals(pairs.get(0)._2.getOrNull(), "1"),
                             () -> assertEquals(pairs.get(1)._1.getOrNull(), "b"),
                             () -> assertFalse(pairs.get(1)._2.isDefined()));
    }
}