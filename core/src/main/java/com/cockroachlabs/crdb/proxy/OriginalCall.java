package com.cockroachlabs.crdb.proxy;

/**
 * A call to the original (un-enhanced) method body. May throw any checked
 * exception the underlying method declares.
 */
@FunctionalInterface
public interface OriginalCall {
    Object call() throws Exception;
}
