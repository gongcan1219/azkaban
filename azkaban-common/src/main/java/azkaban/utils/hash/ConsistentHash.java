package azkaban.utils.hash;

/**
 * Created by lilonghua on 16/3/8.
 */
/**
 * This is an implementation of a consistent hash. T is the type of a bin.
 *
 * It is mostly copied from Tom White's implementation found here:
 * http://www.lexemetech.com/2007/11/consistent-hashing.html
 *
 * Blog comments mention that there may be a bug in this implementation -- if
 * there is a key collision we may lose bins. Probabilistically this is small,
 * and even smaller with a higher more replication factor. This could be made
 * even rarer by enlarging the circle by using Long instead of Integer.
 *
 * getNBins and getNUniqBins return ordered lists of bins for a particular
 * object. This is useful for assigning backups if the first bin fails.
 *
 * This datastructure is not threadsafe.
 */

import org.apache.log4j.Logger;

import java.util.*;

/**
 * https://svn.apache.org/repos/asf/flume/branches/branch-0.9.5/flume-core/src/main/java/com/cloudera/util/consistenthash/ConsistentHash.java
 */
public class ConsistentHash<T> {
    // when looking for n unique bins, give up after a streak of MAX_DUPES
    // duplicates
    public final static int MAX_DUPES = 10;

    // # of times a bin is replicated in hash circle. (for better load balancing)
    private final int numberOfReplicas;

    private final HashFunction hashFunction;
    private final SortedMap<Integer, T> circle = new TreeMap<Integer, T>();

    static final Logger log = Logger.getLogger(ConsistentHash.class);

    public ConsistentHash(int numberOfReplicas, Collection<T> nodes) {
        this(new MD5HashFunction(), numberOfReplicas, nodes);
    }

    public ConsistentHash(HashFunction hashFunction, int numberOfReplicas,
                          Collection<T> nodes) {
        this.hashFunction = hashFunction;
        this.numberOfReplicas = numberOfReplicas;

        for (T node : nodes) {
            addBin(node);
        }
    }

    /**
     * Add a new bin to the consistent hash
     *
     * This assumes that the bin's toString method is immutable.
     *
     * This is not thread safe.
     */
    public void addBin(T bin) {
        for (int i = 0; i < numberOfReplicas; i++) {
            //log.info(bin.toString() + "\t" + i);
            // The string addition forces each replica to have different hash
            circle.put(hashFunction.hash(bin.toString() + i), bin);
        }
    }

    /**
     * Remove a bin from the consistent hash
     *
     * This assumes that the bin's toString method is immutable.
     *
     * This is not thread safe.
     */
    public void removeBin(T bin) {
        for (int i = 0; i < numberOfReplicas; i++) {
            // The string addition forces each replica to be different. This needs
            // to resolve to the same keys as addBin.
            circle.remove(hashFunction.hash(bin.toString() + i));
        }
    }

    /**
     * This returns the closest bin for the object. If the object is the bin it
     * should be an exact hit, but if it is a value traverse to find closest
     * subsequent bin.
     */
    public T getBinFor(Object key) {
        if (circle.isEmpty()) {
            return null;
        }
        int hash = hashFunction.hash(key);
        T bin = circle.get(hash);

        if (bin == null) {
            // inexact match -- find the next value in the circle
            SortedMap<Integer, T> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            bin = circle.get(hash);
        }
        return bin;
    }

    /**
     * This returns the closest n bins in order for the object. There may be
     * duplicates.
     */
    public List<T> getNBinsFor(Object key, int n) {
        if (circle.isEmpty()) {
            return Collections.<T> emptyList();
        }

        List<T> list = new ArrayList<T>(n);
        int hash = hashFunction.hash(key);
        for (int i = 0; i < n; i++) {
            if (!circle.containsKey(hash)) {
                // go to next element.
                SortedMap<Integer, T> tailMap = circle.tailMap(hash);
                hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            }
            list.add(circle.get(hash));

            // was a hit so we increment and loop to find the next bin in the
            // circle
            hash++;
        }
        return list;
    }

    /**
     * This returns the closest n bins in order for the object. There is extra
     * code that forces the bin values to be unique.
     *
     * This will return a list that has all the bins (and is smaller than n) if n
     * > number of bins.
     */
    public List<T> getNUniqueBinsFor(Object key, int n) {
        if (circle.isEmpty()) {
            return Collections.<T> emptyList();
        }

        List<T> list = new ArrayList<T>(n);
        int hash = hashFunction.hash(key);
        int duped = 0;
        for (int i = 0; i < n; i++) {
            if (!circle.containsKey(hash)) {
                // go to next element.
                SortedMap<Integer, T> tailMap = circle.tailMap(hash);
                hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            }
            T candidate = circle.get(hash);
            if (!list.contains(candidate)) {
                duped = 0;
                list.add(candidate);
            } else {
                duped++;
                i--; // try again.
                if (duped > MAX_DUPES) {
                    i++; // we've been duped too many times, just skip to next, returning
                    // fewer than n
                }

            }

            // find the next element in the circle
            hash++;
        }
        return list;
    }
}
