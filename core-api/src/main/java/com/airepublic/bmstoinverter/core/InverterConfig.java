/**
 * This software is free to use and to distribute in its unchanged form for private use.
 * Commercial use is prohibited without an explicit license agreement of the copyright holder.
 * Any changes to this software must be made solely in the project repository at https://github.com/ai-republic/bms-to-inverter.
 * The copyright holder is not liable for any damages in whatever form that may occur by using this software.
 *
 * (c) Copyright 2022 and onwards - Torsten Oltmanns
 *
 * @author Torsten Oltmanns - bms-to-inverter''AT''gmail.com
 */
package com.airepublic.bmstoinverter.core;

/**
 * Configuration read from the confg.properties for the {@link Inverter}.
 */
public class InverterConfig {
    private final String portLocator;
    private final int baudRate;
    private final long sendIntervalMillis;
    private final InverterDescriptor descriptor;

    public InverterConfig(final String portLocator, final int baudRate, final long sendIntervalMillis, final InverterDescriptor descriptor) {
        this.portLocator = portLocator;
        this.baudRate = baudRate;
        this.sendIntervalMillis = sendIntervalMillis;
        this.descriptor = descriptor;
    }


    /**
     * Gets the port locator like /dev/ttyS0, can0, com3, etc.
     *
     * @return the portLocator the port locator
     */
    public String getPortLocator() {
        return portLocator;
    }


    /**
     * Gets the baud rate.
     *
     * @return the baud rate
     */
    public int getBaudRate() {
        return baudRate;
    }


    /**
     * Gets the sending interval in milliseconds.
     *
     * @return the sending interval in milliseconds
     */
    public long getSendIntervalMillis() {
        return sendIntervalMillis;
    }


    /**
     * Gets the {@link InverterDescriptor} for the associated {@link Inverter}.
     *
     * @return the {@link InverterDescriptor} for the associated {@link Inverter}
     */
    public InverterDescriptor getDescriptor() {
        return descriptor;
    }
}
