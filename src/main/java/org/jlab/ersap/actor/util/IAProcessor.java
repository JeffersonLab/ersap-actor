package org.jlab.ersap.actor.util;

public interface IAProcessor {

    /**
     * Processes an incoming object.
     *
     * @param o The object to be processed.
     *
     * @return An object as a result of the processing. Specifics are
     * defined by the class implementing this interface.
     */
    public Object process(Object o);

    /**
     * Resets the state of the processor.
     * It should prepare the processor for a new round of processing.
     */
    public void reset();

    /**
     * Destructs or shuts down the processor.
     * It should perform any necessary cleanup and resource deallocation.
     */
    public void destruct();

}