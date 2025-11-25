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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.airepublic.bmstoinverter.core.util.SystemProperties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.CDI;

@ApplicationScoped
public class InverterProducer extends PluginProducer {
    private final static Logger LOG = LoggerFactory.getLogger(InverterProducer.class);
    private static Inverter inverter = null;
    private final Map<String, InverterDescriptor> descriptors = new HashMap<>();

    /**
     * Constructor.
     */
    public InverterProducer() {
        ServiceLoader.load(InverterDescriptor.class).forEach(descriptor -> descriptors.put(descriptor.getName(), descriptor));
    }


    /**
     * Gets the {@link InverterDescriptor} for the specified name.
     *
     * @param name the name for the {@link InverterDescriptor}
     * @return the {@link InverterDescriptor}
     */
    public InverterDescriptor getDescriptor(final String name) {
        return descriptors.get(name);
    }


    @Produces
    @InverterQualifier
    public synchronized Inverter createInverter() {
        if (inverter == null) {
            String type = System.getProperty("inverter.type");

            // if no inverter is found, probably the config.properties have not been read
            if (type == null) {
                SystemProperties.updateSystemProperties(Paths.get(System.getProperty("configFile", "config.properties")));
                type = System.getProperty("inverter.type");

                if (type == null) {
                    LOG.error("No config.properties found or no BMSes are configured!");
                    System.exit(0);
                }
            }

            final InverterDescriptor descriptor = descriptors.get(System.getProperty("inverter.type"));
            inverter = CDI.current().select(descriptor.getInverterClass()).get();
            final String portLocator = System.getProperty("inverter.portLocator");
            final int baudRate = Integer.valueOf(System.getProperty("inverter.baudRate"));
            final long sendIntervalMillis = resolveSendIntervalMillis();
            final InverterConfig config = new InverterConfig(portLocator, baudRate, sendIntervalMillis, descriptor);
            LOG.info("Created inverter binding: " + descriptor.getName());

            // load configured plugins for the inverter
            final Set<InverterPlugin> plugins = loadPlugins(InverterPlugin.class);
            inverter.setPlugins(plugins);
            inverter.initialize(config);
        }

        return inverter;
    }


    public static void main(final String[] args) {
        System.setProperty("inverter.type", "SMA_SI_CAN");
        System.setProperty("inverter.portLocator", "can1");
        System.setProperty("inverter.baudRate", "500000");
        System.setProperty("inverter.sendIntervalMillis", "1000");

        System.setProperty("plugin.inverter.1.class", "com.airepublic.bmstoinverter.core.plugin.inverter.SimulatedBatteryPackPlugin");
        System.setProperty("plugin.inverter.1.property.1.name", "SOC");
        System.setProperty("plugin.inverter.1.property.1.value", "500");
        System.setProperty("plugin.inverter.1.property.1.description", "The configured preset batterypack SOC (unit 0.1%)");
        System.setProperty("plugin.inverter.1.property.2.name", "SOH");
        System.setProperty("plugin.inverter.1.property.2.value", "990");
        System.setProperty("plugin.inverter.1.property.2.description", "The configured preset batterypack SOH (unit 0.1%)");
        System.setProperty("plugin.inverter.1.property.3.name", "Current");
        System.setProperty("plugin.inverter.1.property.3.value", "0");
        System.setProperty("plugin.inverter.1.property.3.description", "The configured preset batterypack current (unit 0.1A)");
        System.setProperty("plugin.inverter.1.property.4.name", "Voltage");
        System.setProperty("plugin.inverter.1.property.4.value", "520");
        System.setProperty("plugin.inverter.1.property.4.description", "The configured preset batterypack voltage (unit 0.1V)");
        System.setProperty("plugin.inverter.1.property.5.name", "Max. charge current");
        System.setProperty("plugin.inverter.1.property.5.value", "200");
        System.setProperty("plugin.inverter.1.property.5.description", "The configured preset batterypack maximum charge current (unit 0.1A)");
        System.setProperty("plugin.inverter.1.property.6.name", "Max. discharge current");
        System.setProperty("plugin.inverter.1.property.6.value", "200");
        System.setProperty("plugin.inverter.1.property.6.description", "The configured preset batterypack maximum discharge current (unit 0.1A)");
        System.setProperty("plugin.inverter.1.property.7.name", "Max. voltage limit");
        System.setProperty("plugin.inverter.1.property.7.value", "540");
        System.setProperty("plugin.inverter.1.property.7.description", "The configured preset batterypack maximum voltage limit (unit 0.1V)");
        System.setProperty("plugin.inverter.1.property.8.name", "Min. voltage lime");
        System.setProperty("plugin.inverter.1.property.8.value", "480");
        System.setProperty("plugin.inverter.1.property.8.description", "The configured preset batterypack minimum voltage limit (unit 0.1V)");
        System.setProperty("plugin.inverter.1.property.9.name", "Average Temperature");
        System.setProperty("plugin.inverter.1.property.9.value", "250");
        System.setProperty("plugin.inverter.1.property.9.description", "The configured preset batterypack average temperature (unit 0.1C)");

        final InverterProducer p = new InverterProducer();
        p.createInverter();
    }

    private long resolveSendIntervalMillis() {
        final String sendIntervalMillis = System.getProperty("inverter.sendIntervalMillis");
        if (sendIntervalMillis != null) {
            return Math.round(Double.parseDouble(sendIntervalMillis));
        }

        final String legacySeconds = System.getProperty("inverter.sendInterval");
        if (legacySeconds != null) {
            LOG.warn("Configuration property 'inverter.sendInterval' is deprecated. Please migrate to 'inverter.sendIntervalMillis' (milliseconds).");
            return Math.round(Double.parseDouble(legacySeconds) * 1000d);
        }

        throw new IllegalStateException("No inverter send interval configured (expected inverter.sendIntervalMillis)");
    }

}
