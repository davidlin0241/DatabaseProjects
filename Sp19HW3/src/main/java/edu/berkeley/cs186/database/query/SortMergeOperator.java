package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.RecordIterator;

import javax.xml.crypto.Data;

public class SortMergeOperator extends JoinOperator {
    public SortMergeOperator(QueryOperator leftSource,
                             QueryOperator rightSource,
                             String leftColumnName,
                             String rightColumnName,
                             Database.Transaction transaction) throws QueryPlanException, DatabaseException {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

        // for HW4
        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
        return new SortMergeIterator();
    }

    public int estimateIOCost() throws QueryPlanException {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator extends JoinIterator {
        /**
        * Some member variables are provided for guidance, but there are many possible solutions.
        * You should implement the solution that's best for you, using any member variables you need.
        * You're free to use these member variables, but you're not obligated to.
        */

        private String leftTableName;
        private String rightTableName;
        private RecordIterator leftIterator;
        private RecordIterator rightIterator;
        private Record leftRecord;
        private Record nextRecord;
        private Record rightRecord;
        private boolean marked;

        public SortMergeIterator() throws QueryPlanException, DatabaseException {
            super();
            //throw new UnsupportedOperationException("TODO(hw3): implement");

            SortOperator leftSortOperator = new SortOperator(SortMergeOperator.this.getTransaction(), getLeftTableName(), new LeftRecordComparator());
            SortOperator rightSortOperator = new SortOperator(SortMergeOperator.this.getTransaction(), getRightTableName(), new RightRecordComparator());

            leftTableName = leftSortOperator.sort();
            rightTableName = rightSortOperator.sort();

            leftIterator = SortMergeOperator.this.getRecordIterator(leftTableName);
            rightIterator = SortMergeOperator.this.getRecordIterator(rightTableName);

            leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
            rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;

            try {
                fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
        }

        private void fetchNextRecord() throws DatabaseException {
            LR_RecordComparator comparator = new LR_RecordComparator();
            List<DataBox> leftValues, rightValues;

            this.nextRecord = null;
            do {

                if (leftRecord == null) {
                    throw new DatabaseException("No more records :)");
                }

                if (!marked) {
                    while (leftRecord != null && comparator.compare(leftRecord, rightRecord) < 0) {
                        leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
                    }
                    while (rightRecord != null && comparator.compare(leftRecord, rightRecord) > 0) {
                        rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;
                    }
                    rightIterator.mark();
                    marked = true;
                }

                if (leftRecord != null && rightRecord != null && comparator.compare(leftRecord, rightRecord) == 0) {
                    leftValues = new ArrayList<>(leftRecord.getValues());
                    rightValues = new ArrayList<>(rightRecord.getValues());
                    leftValues.addAll(rightValues);

                    nextRecord = new Record(leftValues);
                    rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;
                } else {
                    rightIterator.reset();
                    rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;
                    leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
                    marked = false;
                }

            } while (!hasNext());
        }

        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        public boolean hasNext() {
            return nextRecord != null;
            //throw new UnsupportedOperationException("TODO(hw3): implement");
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */

        public Record next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
            return nextRecord;

            //throw new UnsupportedOperationException("TODO(hw3): implement");
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        private class LeftRecordComparator implements Comparator<Record> {
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
            }
        }

        private class RightRecordComparator implements Comparator<Record> {
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }

        /**
        * Left-Right Record comparator
        * o1 : leftRecord
        * o2: rightRecord
        */
        private class LR_RecordComparator implements Comparator<Record> {
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }
    }
}
