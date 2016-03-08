package azkaban.utils.hash;

/**
 * Created by lilonghua on 16/3/8.
 */
/**
 * Uses MD5 as a hash generator. This version actually takes a hashs of the
 * toString String.
 */

import org.apache.log4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * https://svn.apache.org/repos/asf/flume/branches/branch-0.9.5/flume-core/src/main/java/com/cloudera/util/consistenthash/MD5HashFunction.java
 */
public class MD5HashFunction implements HashFunction {
    static final Logger LOG = Logger.getLogger(MD5HashFunction.class);

    private MessageDigest digest;

    public MD5HashFunction() {
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOG.error("MD5 algorithm doesn't exist?", e);
            throw new IllegalArgumentException("This should never happen");
        }
    }

    //MessageDigest 线程不安全
    synchronized public int hash(Object s) {
        digest.reset();
        byte[] hash = digest.digest(s.toString().getBytes());

        // HACK just take the first 4 digits and make it an integer.
        // apparently this is what other algorithms use to turn it into an int
        // value.

        // http://github.com/dustin/java-memcached-client/blob/9b2b4be73ee4a74bf6d0cf47f89c33753a5b5329/src/main/java/net/spy/memcached/HashAlgorithm.java
        int h0 = (hash[0] & 0xFF);
        int h1 = (hash[1] & 0xFF) << 8;
        int h2 = (hash[2] & 0xFF) << 16;
        int h3 = (hash[3] & 0xFF) << 24;

        //int val = h0 + h1 + h2 + h3;
        return (h0 + h1 + h2 + h3);
    }

}
