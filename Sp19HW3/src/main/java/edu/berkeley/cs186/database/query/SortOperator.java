package edu.berkeley.cs186.database.query;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.common.Pair;
import edu.berkeley.cs186.database.io.Page;

import java.util.*;

public class SortOperator {
    private Database.Transaction transaction;
    private String tableName;
    private Comparator<Record> comparator;
    private Schema operatorSchema;
    private int numBuffers;
    private String sortedTableName = null;

    public SortOperator(Database.Transaction transaction, String tableName,
                        Comparator<Record> comparator) throws DatabaseException, QueryPlanException {
        this.transaction = transaction;
        this.tableName = tableName;
        this.comparator = comparator;
        this.operatorSchema = this.computeSchema();
        this.numBuffers = this.transaction.getNumMemoryPages();
    }

    public Schema computeSchema() throws QueryPlanException {
        try {
            return this.transaction.getFullyQualifiedSchema(this.tableName);
        } catch (DatabaseException de) {
            throw new QueryPlanException(de);
        }
    }

    public class Run {
        String tempTableName;

        public Run() throws DatabaseException {
            this.tempTableName = SortOperator.this.transaction.createTempTable(
                                     SortOperator.this.operatorSchema);
        }

        public void addRecord(List<DataBox> values) throws DatabaseException {
            SortOperator.this.transaction.addRecord(this.tempTableName, values);
        }

        public void addRecords(List<Record> records) throws DatabaseException {
            for (Record r : records) {
                this.addRecord(r.getValues());
            }
        }

        public Iterator<Record> iterator() throws DatabaseException {
            return SortOperator.this.transaction.getRecordIterator(this.tempTableName);
        }

        public String tableName() {
            return this.tempTableName;
        }
    }

    /**
     * Returns a NEW run that is the sorted version of the input run.
     * Can do an in memory sort over all the records in this run
     * using one of Java's built-in sorting methods.
     * Note: Don't worry about modifying the original run.
     * Returning a new run would bring one extra page in memory beyond the
     * size of the buffer, but it is done this way for ease.
     */
    //take in a run of at most numBuffers page large
    public Run sortRun(Run run) throws DatabaseException {
        List<Record> runList = new ArrayList<>();
        Iterator<Record> runIter = run.iterator();
        Run newRun = createRun();

        while (runIter.hasNext()) {
            runList.add(runIter.next());
        }

        runList.sort(comparator);
        newRun.addRecords(runList);
        return newRun;

        //throw new UnsupportedOperationException("TODO(hw3): implement");
    }

    public boolean iterListDoneCheck(List<Iterator<Record>> l) {
        for (int i = 0; i < l.size(); i++) {
            Iterator<Record> iter = l.get(i);
            if (iter.hasNext()) {
                return false;
            }
        }
        return true;
    }
    /**
     * Given a list of sorted runs, returns a new run that is the result
     * of merging the input runs. You should use a Priority Queue (java.util.PriorityQueue)
     * to determine which record should be should be added to the output run next.
     * It is recommended that your Priority Queue hold Pair<Record, Integer> objects
     * where a Pair (r, i) is the Record r with the smallest value you are
     * sorting on currently unmerged from run i.
     */
    public Run mergeSortedRuns(List<Run> runs) throws DatabaseException {
        //number of runs in the list must be <= numBuffers
        //in mergeSortedRuns, you should only be merging B - 1 (numBuffers - 1) runs at a time.
        //At any point, your PQ should only have a most B - 1 records in it (one for each of the runs)
        PriorityQueue<Pair<Record, Integer>> pq =  new PriorityQueue<>(new RecordPairComparator());
        Pair<Record, Integer> record;
        List<Iterator<Record>> recordIterList = new ArrayList<>();
        Run merged = createRun();
        int runIndex;

        for (int i = 0; i < runs.size(); i++) {
            recordIterList.add(runs.get(i).iterator());
        }

        for (int i = 0; i < runs.size(); i++) {
            pq.add(new Pair<>(recordIterList.get(i).next(), i));
        }

        while (pq.size() > 0) {
            record = pq.remove();
            merged.addRecord(record.getFirst().getValues());

            runIndex = record.getSecond();
            if (recordIterList.get(runIndex).hasNext()) {
                pq.add(new Pair<>(recordIterList.get(runIndex).next(), runIndex));
            }
        }

        return merged;
        //throw new UnsupportedOperationException("TODO(hw3): implement");
    }

    /**
     * Given a list of N sorted runs, returns a list of
     * sorted runs that is the result of merging (numBuffers - 1)
     * of the input runs at a time.
     */
    public List<Run> mergePass(List<Run> runs) throws DatabaseException {
        List<Run> runList = new ArrayList<>();
        int index, max = numBuffers - 1;
        for (int i = 0; i < runs.size(); i += max) {
            index = Math.min(runs.size(), i + max);
            runList.add(mergeSortedRuns(runs.subList(i, index)));
        }

        return runList;
        //throw new UnsupportedOperationException("TODO(hw3): implement");
    }

    /**
     * Does an external merge sort on the table with name tableName
     * using numBuffers.
     * Returns the name of the table that backs the final run.
     */
    public String sort() throws DatabaseException {
        //throw new UnsupportedOperationException("TODO(hw3): implement");

        //In sort() you should make sure you don't add more than numBuffer pages of values
        //into the run that you pass into sortRun, so you don't need checks in sortRun.
        List<Run> sorted = new ArrayList<>();
        List<Record> records = new ArrayList<>();
        BacktrackingIterator<Page> pageIter = transaction.getPageIterator(tableName);
        pageIter.next();
        BacktrackingIterator<Record> recordIter;
        Run run;

        while (pageIter.hasNext()) {

            recordIter = transaction.getBlockIterator(tableName, pageIter, numBuffers);
            run = createRun();
            recordIter.forEachRemaining(records::add);
            run.addRecords(records);
            sorted.add(sortRun(run));
        }

        while (sorted.size() != 1 ) {
            sorted = mergePass(sorted);
        }

        return sorted.get(0).tableName();
    }

    public Iterator<Record> iterator() throws DatabaseException {
        if (sortedTableName == null) {
            sortedTableName = sort();
        }
        return this.transaction.getRecordIterator(sortedTableName);
    }

    private class RecordPairComparator implements Comparator<Pair<Record, Integer>> {
        public int compare(Pair<Record, Integer> o1, Pair<Record, Integer> o2) {
            return SortOperator.this.comparator.compare(o1.getFirst(), o2.getFirst());

        }
    }
    public Run createRun() throws DatabaseException {
        return new Run();
    }
}

