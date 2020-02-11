package edu.harvard.iq.dataverse.dataset.difference;

import com.google.common.collect.Streams;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.util.ArrayList;
import java.util.List;

public class MultipleItemDiff<T> {

    private List<T> oldValues;
    private List<T> newValues;

    // -------------------- CONSTRUCTORS --------------------

    public MultipleItemDiff(List<T> oldValue, List<T> newValue) {
        this.oldValues = oldValue;
        this.newValues = newValue;
    }

    // -------------------- GETTERS --------------------

    public List<T> getOldValue() {
        return oldValues;
    }

    public List<T> getNewValue() {
        return newValues;
    }

    // -------------------- LOGIC --------------------

    /**
     * Generates pairs by combining old and new values, contrary to {@link Streams#zip} the lists don't have to be even.
     * <p></p>
     * You can decide what to do with missing value by using {@link Option} api.
     */
    public List<Tuple2<Option<T>, Option<T>>> generatePairs(){
        ArrayList<Tuple2<Option<T>, Option<T>>> combinedValues = new ArrayList<>();

        int biggerListSize = Math.max(newValues.size(), oldValues.size());
        for (int loop = 0; loop < biggerListSize; loop++) {
            final int finalLoop = loop;

            Option<T> oldValue = Try.of(() -> Option.of(oldValues.get(finalLoop))).getOrElse(Option::none);
            Option<T> newValue = Try.of(() -> Option.of(newValues.get(finalLoop))).getOrElse(Option::none);

            combinedValues.add(Tuple.of(oldValue, newValue));
        }

        return combinedValues;
    }
}
