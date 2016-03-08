package azkaban.utils.hash;

/**
 * Created by lilonghua on 16/3/8.
 */
/**
 * Interface for a hash function.
 * https://svn.apache.org/repos/asf/flume/branches/branch-0.9.5/flume-core/src/main/java/com/cloudera/util/consistenthash/HashFunction.java
 */
public interface HashFunction {
    int hash(Object s);
}
