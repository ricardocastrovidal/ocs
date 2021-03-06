package edu.gemini.epics.acm;

import java.util.logging.Logger;

import edu.gemini.epics.EpicsWriter;
import edu.gemini.epics.ReadWriteClientEpicsChannel;
import gov.aps.jca.CAException;
import gov.aps.jca.TimeoutException;

final class CaParameterImpl<T> implements CaParameter<T> {
    
    private static final Logger LOG = Logger.getLogger(CaParameterImpl.class.getName()); 

    private final String name;
    private final String channel;
    private final String description;
    private EpicsWriter epicsWriter;
    private ReadWriteClientEpicsChannel<String> rwChannel;
    private T value;

    private CaParameterImpl(String name, String channel, String description,
                            EpicsWriter epicsWriter) {
        this.name = name;
        this.channel = channel;
        this.description = description;
        this.epicsWriter = epicsWriter;
        this.rwChannel = epicsWriter.getStringChannel(channel);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public T value() {
        return value;
    }

    @Override
    public void set(T value) throws CAException, TimeoutException {
        this.value = value;

        rwChannel.setValue(value.toString());
    }

    public void unbind() {
        try {
            epicsWriter.destroyChannel(rwChannel);
        } catch (CAException e) {
            LOG.warning(e.getMessage());
        }
        rwChannel = null;
        epicsWriter = null;
    }

    public void write() throws CAException, TimeoutException {
        rwChannel.setValue(value.toString());
    }

    static public CaParameterImpl<Double> createDoubleParameter(
            String name, String channel, String description, EpicsWriter epicsWriter) {
        return new CaParameterImpl<>(name, channel, description, epicsWriter);
    }

    static public CaParameterImpl<Integer> createIntegerParameter(
            String name, String channel, String description, EpicsWriter epicsWriter) {
        return new CaParameterImpl<>(name, channel, description, epicsWriter);
    }

    static public CaParameterImpl<Float> createFloatParameter(
            String name, String channel, String description, EpicsWriter epicsWriter) {
        return new CaParameterImpl<>(name, channel, description, epicsWriter);
    }

    static public CaParameterImpl<String> createStringParameter(
            String name, String channel, String description, EpicsWriter epicsWriter) {
        return new CaParameterImpl<>(name, channel, description, epicsWriter);
    }

    @Override
    public String description() {
        return description;
    }

}
