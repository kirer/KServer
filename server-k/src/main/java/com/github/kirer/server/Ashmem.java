package com.github.kirer.server;

public class Ashmem {

    /**
     * Create shared memory file (server side)
     *
     * @param size Size of shared memory in bytes
     * @return 0 on success, -1 on failure
     */
    public static native int create(int size);

    /**
     * Connect to shared memory file (client side)
     *
     * @param size Size of shared memory in bytes
     * @return 0 on success, -1 on failure
     */
    public static native int connect(int size);

    /**
     * Write data to shared memory
     *
     * @param data Byte array to write
     * @return 0 on success, -1 on failure
     */
    public static native int write(byte[] data);

    /**
     * Read data from shared memory
     *
     * @return Byte array containing the data, or null if no data/error
     */
    public static native byte[] read();

    /**
     * Cleanup shared memory resources
     *
     * @return 0 on success, -1 on failure
     */
    public static native int cleanup();
}
