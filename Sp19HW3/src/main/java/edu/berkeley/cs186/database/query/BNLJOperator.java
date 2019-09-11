package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.table.Record;

public class BNLJOperator extends JoinOperator {
    private int numBuffers;

    public BNLJOperator(QueryOperator leftSource,
                        QueryOperator rightSource,
                        String leftColumnName,
                        String rightColumnName,
                        Database.Transaction transaction) throws QueryPlanException, DatabaseException {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.BNLJ);

        this.numBuffers = transaction.getNumMemoryPages();

        // for HW4
        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
        return new BNLJIterator();
    }

    public int estimateIOCost() {
        //This method implements the the IO cost estimation of the Block Nested Loop Join

        int usableBuffers = numBuffers -
                            2; //Common mistake have to first calculate the number of usable buffers

        int numLeftPages = getLeftSource().getStats().getNumPages();

        int numRightPages = getRightSource().getStats().getNumPages();

        return ((int) Math.ceil((double) numLeftPages / (double) usableBuffers)) * numRightPages +
               numLeftPages;

    }

    /**
     * BNLJ: Block Nested Loop Join
     *  See lecture slides.
     *
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might prove to be a useful reference).
     */

    private class BNLJIterator extends JoinIterator {
        /**
         * Some member variables are provided for guidance, but there are many possible solutions.
         * You should implement the solution that's best for you, using any member variables you need.
         * You're free to use these member variables, but you're not obligated to.
         */

        private Iterator<Page> leftIterator = null;
        private Iterator<Page> rightIterator = null;
        private BacktrackingIterator<Record> leftRecordIterator = null;
        private BacktrackingIterator<Record> rightRecordIterator = null;
        private Record leftRecord = null;
        private Record rightRecord = null;
        private Record nextRecord = null;

        public BNLJIterator() throws QueryPlanException, DatabaseException {
            super();

            rightIterator = BNLJOperator.this.getPageIterator(this.getRightTableName());
            leftIterator = BNLJOperator.this.getPageIterator(this.getLeftTableName());

            rightIterator.next();
            leftIterator.next();

            rightRecordIterator = BNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);
            leftRecordIterator = BNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftIterator, numBuffers - 2);

            nextRecord = null;

            leftRecord = leftRecordIterator.hasNext() ? leftRecordIterator.next() : null;
            rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;

            // We mark the first record so we can reset to it when we advance the left record.
            if (rightRecord != null) {
                rightRecordIterator.mark();
            } else { return; }

            if (leftRecord != null) {
                leftRecordIterator.mark();
            }

            try {
                fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
            //throw new UnsupportedOperationException("TODO(hw3): implement");
        }

        private void resetRightRecordIter() {
            this.rightRecordIterator.reset();
            assert(rightRecordIterator.hasNext());
            rightRecord = rightRecordIterator.next();
            rightRecordIterator.mark();
        }

        private void resetLeftRecordIter() {
            this.leftRecordIterator.reset();
            assert(leftRecordIterator.hasNext());
            leftRecord = leftRecordIterator.next();
            leftRecordIterator.mark();
        }

        private void nextLeftRecord() throws DatabaseException {
            if (!leftRecordIterator.hasNext()) { throw new DatabaseException("All Done!"); }
            leftRecord = leftRecordIterator.next();
        }

        private void fetchNextRightPage() throws DatabaseException {
            rightRecordIterator = BNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);
            rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
            rightRecordIterator.mark();
        }

        private void fetchNextLeftBlock() throws DatabaseException {
            leftRecordIterator = BNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftIterator, numBuffers - 2);
            leftRecord = leftRecordIterator.hasNext() ? leftRecordIterator.next() : null;
            leftRecordIterator.mark();

            //always included when fetching left
            rightIterator = BNLJOperator.this.getPageIterator(this.getRightTableName());
            rightIterator.next();
        }

        //fetchNextLeftPage() and fetchNextRightPage()
        //resetRightRecordIter, resetLeftRecordIter
        private void fetchNextRecord() throws DatabaseException {
            if (this.leftRecord == null) { throw new DatabaseException("No new record to fetch"); }
            this.nextRecord = null;
            do {
                if (this.rightRecord != null) {
                    DataBox leftJoinValue = this.leftRecord.getValues().get(BNLJOperator.this.getLeftColumnIndex());
                    DataBox rightJoinValue = rightRecord.getValues().get(BNLJOperator.this.getRightColumnIndex());
                    if (leftJoinValue.equals(rightJoinValue)) {
                        List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
                        List<DataBox> rightValues = new ArrayList<>(rightRecord.getValues());
                        leftValues.addAll(rightValues);
                        this.nextRecord = new Record(leftValues);
                    }
                    this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
                } else if (leftRecordIterator.hasNext())  { //done with one row of a (left) page, move down to another row
                    nextLeftRecord();
                    resetRightRecordIter();
                } else if (rightIterator.hasNext()) { //done with the entire (right) page, move down to another page for the right table
                    fetchNextRightPage();
                    resetLeftRecordIter();
                } else if (leftIterator.hasNext()) { //done with the entire (left) page, move down to another left page
                    fetchNextLeftBlock();
                    fetchNextRightPage();
                } else {
                    throw new DatabaseException("All done :)");
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

    }
}
