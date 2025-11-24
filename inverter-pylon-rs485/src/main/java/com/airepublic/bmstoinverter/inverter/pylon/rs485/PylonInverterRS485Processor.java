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
package com.airepublic.bmstoinverter.inverter.pylon.rs485;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.airepublic.bmstoinverter.core.AlarmLevel;
import com.airepublic.bmstoinverter.core.Inverter;
import com.airepublic.bmstoinverter.core.Port;
import com.airepublic.bmstoinverter.core.bms.data.Alarm;
import com.airepublic.bmstoinverter.core.bms.data.BatteryPack;
import com.airepublic.bmstoinverter.core.bms.data.EnergyStorage;
import com.airepublic.bmstoinverter.core.util.BitUtil;
import com.airepublic.bmstoinverter.core.util.ByteAsciiConverter;

/**
 * The class to handle RS485 messages for Pylontech {@link Inverter}.
 */
@ApplicationScoped
public class PylonInverterRS485Processor extends Inverter {
    private final static Logger LOG = LoggerFactory.getLogger(PylonInverterRS485Processor.class);
    private static final double DEFAULT_CURRENT_LIMIT_A = 20.0;
    private static final int DEFAULT_SOC_TENTHS = 800;
    private static final int DEFAULT_TEMPERATURE_TENTHS = 250;
    private long startupTime = System.currentTimeMillis();

    public PylonInverterRS485Processor() {
        super();
    }


    protected PylonInverterRS485Processor(final EnergyStorage energyStorage) {
        super(energyStorage);
    }


    @Override
    protected List<ByteBuffer> createSendFrames(final ByteBuffer requestFrame, final BatteryPack aggregatedPack) {
        final List<ByteBuffer> frames = new ArrayList<>();
// Warm-up period for the first 5 seconds after boot
if (System.currentTimeMillis() - startupTime < 5000) {
    LOG.debug("Warm-up: sending fake startup frame to trigger inverter polling");

    final byte adr = (byte)0x02;

    ByteBuffer warm = prepareSendFrame(
        adr,
        (byte)0x63,   // CID2
        (byte)0x00,   // RTN
        createChargeDischargeIfno(aggregatedPack) // ASCII HEX
    );

    frames.add(warm);
    return frames;
}

        // check if the inverter is actively requesting data
        if (requestFrame != null) {
            LOG.debug("Inverter actively requesting frames from BMS");
            LOG.info("RX ASCII: {}", toAsciiString(requestFrame));
            requestFrame.position(3);
            final byte adr = ByteAsciiConverter.convertAsciiBytesToByte(requestFrame.get(), requestFrame.get());
            final byte cid1 = ByteAsciiConverter.convertAsciiBytesToByte(requestFrame.get(), requestFrame.get());
            final byte cid2 = ByteAsciiConverter.convertAsciiBytesToByte(requestFrame.get(), requestFrame.get());
            final byte[] lengthBytes = new byte[4];
            requestFrame.get(lengthBytes);
            final int length = ByteAsciiConverter.convertAsciiBytesToShort(lengthBytes) & 0x0FFF;
            final byte[] data = new byte[length];
            requestFrame.get(data);

            if (cid1 != 0x46) {
                // not supported
                return frames;
            }

            byte[] responseData = null;

            switch (cid2) {
                case 0x4F: // 0x4F Protocol Version
                    responseData = createProtocolVersion(aggregatedPack);
                break;
                case 0x51: // 0x51 Manufacturer Code
                    responseData = createManufacturerCode(aggregatedPack);
                break;
                case (byte) 0x92: // 0x92 Charge/Discharge Management Info
                    responseData = createChargeDischargeManagementInfo(aggregatedPack);
                break;
                case 0x42: // 0x42 Cell Information
                    responseData = createCellInformation(aggregatedPack);
                break;
                case 0x47: // 0x47 Voltage/Current Limits
                    responseData = createVoltageCurrentLimits(aggregatedPack);
                break;
                case 0x60: // 0x60 System Info
                    responseData = createSystemInfo(aggregatedPack);
                break;


    case 0x61:
        frames.add(prepareSendFrame(adr, (byte) 0x46, (byte) 0x61,
            createBatteryInformation(aggregatedPack)));
        break;
                case 0x62:
                    responseData = createAlarms(aggregatedPack);
                break; // 0x62 Alarms
    case 0x63:
        frames.add(prepareSendFrame(adr, (byte) 0x46, (byte) 0x63,
            createChargeDischargeIfno(aggregatedPack)));
        break;

                default:
                    // not supported
                    return frames;
            }

            final ByteBuffer responseFrame = prepareSendFrame(adr, cid1, (byte) 0x00, responseData);
            frames.add(responseFrame);
            LOG.info("TX ASCII: {}", toAsciiString(responseFrame));
            LOG.debug("Responding to inverter with: {}", Port.printBuffer(responseFrame));
    } else {
            LOG.debug("Inverter is not requesting data, no frames to send");
            // try to send data actively
            try {
            Thread.sleep(5); // 5 ms is usually plenty; you can try 2–10 ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


            
            //final byte adr = 0x12; // this is wrong anyway as the CID1 should be 0x46 for responses
            //frames.add(prepareSendFrame(adr, (byte) 0x4F, (byte) 0x00, createProtocolVersion(aggregatedPack)));
            
            // frames.add(prepareSendFrame(adr, (byte) 0x51, (byte) 0x00,
            // createManufacturerCode(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x92, (byte) 0x00,
            // createChargeDischargeManagementInfo(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x42, (byte) 0x00,
            // createCellInformation(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x47, (byte) 0x00,
            // createVoltageCurrentLimits(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x60, (byte) 0x00, createSystemInfo(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x61, (byte) 0x00,
            // createBatteryInformation(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x62, (byte) 0x00, createAlarms(aggregatedPack)));
            // frames.add(prepareSendFrame(adr, (byte) 0x63, (byte) 0x00,
            // createChargeDischargeIfno(aggregatedPack)));

            LOG.debug("Actively sending {} frames to inverter", frames.size());
            //frames.stream().forEach(f -> System.out.println(Port.printBuffer(f)));
    }


        return frames;
    }

    
    private String toAsciiString(final ByteBuffer buffer) {
        final ByteBuffer copy = buffer.asReadOnlyBuffer();
        copy.rewind();
        final byte[] data = new byte[copy.remaining()];
        copy.get(data);
        return new String(data, StandardCharsets.US_ASCII);
    }


    // 0x4F
    private byte[] createProtocolVersion(final BatteryPack pack) {
        final ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes(pack.softwareVersion, 1));

        final byte[] data = new byte[buffer.position()];
        buffer.get(data, 0, buffer.position());

        return data;
    }


    // 0x51
    private byte[] createManufacturerCode(final BatteryPack pack) {
        final ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes("PYLON", 10));
        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes(pack.softwareVersion, 1));
        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes(pack.manufacturerCode, 20));

        final byte[] data = new byte[buffer.position()];
        buffer.get(data, 0, buffer.position());

        return data;
    }


    // 0x92
    private byte[] createChargeDischargeManagementInfo(final BatteryPack pack) {
        final ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.maxPackVoltageLimit * 100)));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.minPackVoltageLimit * 100)));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) resolveCurrentLimitA10(pack.maxPackChargeCurrent, "maxCharge", pack)));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) resolveCurrentLimitA10(pack.maxPackDischargeCurrent, "maxDischarge", pack)));
        byte chargeDischargeMOSStates = 0x00;
        chargeDischargeMOSStates = BitUtil.setBit(chargeDischargeMOSStates, 7, pack.chargeMOSState);
        chargeDischargeMOSStates = BitUtil.setBit(chargeDischargeMOSStates, 6, pack.dischargeMOSState);
        chargeDischargeMOSStates = BitUtil.setBit(chargeDischargeMOSStates, 5, pack.forceCharge);
        buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(chargeDischargeMOSStates));

        final byte[] data = new byte[buffer.position()];
        buffer.get(data, 0, buffer.position());

        return data;
    }


    // 0x42
    private byte[] createCellInformation(final BatteryPack pack) {
        final ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) pack.numberOfCells));

        for (int cellNo = 0; cellNo < pack.numberOfCells; cellNo++) {
            buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) pack.cellVmV[cellNo]));
        }

        buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) pack.numOfTempSensors));

        for (int tempNo = 0; tempNo < pack.numOfTempSensors; tempNo++) {
            buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.cellTemperature[tempNo] + 2731)));
        }

        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) pack.packCurrent));
        buffer.put(ByteAsciiConverter.convertCharToAsciiBytes((char) pack.packVoltage));
        buffer.put(ByteAsciiConverter.convertCharToAsciiBytes((char) (pack.remainingCapacitymAh / 100)));
        buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) (pack.ratedCapacitymAh * 1000 > 65 ? 4 : 2)));
        buffer.put(ByteAsciiConverter.convertCharToAsciiBytes((char) (pack.ratedCapacitymAh / 100)));
        buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) pack.bmsCycles));
        buffer.put(new byte[] { 0, 0, 0, 0, 0, 0 }); // old compatibility

        final byte[] data = new byte[buffer.position()];
        buffer.get(data, 0, buffer.position());

        return data;
    }


    // 0x47
    private byte[] createVoltageCurrentLimits(final BatteryPack pack) {
        final ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) pack.maxCellVoltageLimit));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) pack.minCellVoltageLimit)); // warning
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) pack.minCellVoltageLimit)); // protect
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (50 * 10 + 2731))); // max charge temp
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (-40 * 10 + 2731))); // min charge temp
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) resolveCurrentLimitA10(pack.maxPackChargeCurrent, "maxCharge", pack)));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.maxPackVoltageLimit * 100)));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.minPackVoltageLimit * 100))); // warning
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.minPackVoltageLimit * 100))); // protect
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (50 * 10 + 2731))); // max discharge temp
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (-40 * 10 + 2731))); // min discharge temp
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) resolveCurrentLimitA10(pack.maxPackDischargeCurrent, "maxDischarge", pack)));

        final byte[] data = new byte[buffer.position()];
        buffer.get(data, 0, buffer.position());

        return data;
    }


    // 0x60
    private byte[] createSystemInfo(final BatteryPack aggregatedPack) {
        final ByteBuffer buffer = ByteBuffer.allocate(4096);

        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes("Battery", 10));
        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes(aggregatedPack.manufacturerCode, 20));
        buffer.put(ByteAsciiConverter.convertStringToAsciiBytes(aggregatedPack.softwareVersion, 2));
        buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) aggregatedPack.numberOfCells));

        for (int i = 0; i < aggregatedPack.numberOfCells; i++) {
            buffer.put(ByteAsciiConverter.convertStringToAsciiBytes("Battery S/N #" + i, 16));
        }

        final byte[] data = new byte[buffer.position()];
        buffer.get(data, 0, buffer.position());

        return data;
    }

// 0x61 – Battery information for Pylon 3.5
// 0x61 – Get System analog data
private byte[] createBatteryInformation(final BatteryPack aggregatedPack) {
    final ByteBuffer buffer = ByteBuffer.allocate(4096);

    // 1  Battery system average voltage (V, 0.01 or 0.001 depending on packVoltage units)
    buffer.put(ByteAsciiConverter.convertCharToAsciiBytes(
            (char) (aggregatedPack.packVoltage * 100)));

    // 2  Battery system current (A, 0.1)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.packCurrent * 10)));

    // 3  Battery system SOC (%)
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) (aggregatedPack.packSOC / 10)));

    // 4  Average charge/discharge cycles
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) aggregatedPack.bmsCycles));

    // 5  Max design cycles
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) 10000));

    // 6  Average SOH (%)
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) (aggregatedPack.packSOH / 10)));

    // 7  Lowest SOH (%)
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) (aggregatedPack.packSOH / 10)));

    // ---- Cell voltage info ----
    int maxPack = 0;
    int minPack = 0;

    for (int i = 0; i < getEnergyStorage().getBatteryPacks().size(); i++) {
        final BatteryPack pack = getEnergyStorage().getBatteryPack(i);

        if (pack.maxCellmV == aggregatedPack.maxCellmV) {
            maxPack = i;
        }
        if (pack.minCellmV == aggregatedPack.minCellmV) {
            minPack = i;
        }
    }

    // 8  Highest cell voltage (mV)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) aggregatedPack.maxCellmV));
    // 9  Pack with highest cell voltage
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) maxPack));
    // 10 Cell number with highest voltage
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) aggregatedPack.maxCellVNum));

    // 11 Lowest cell voltage (mV)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) aggregatedPack.minCellmV));
    // 12 Pack with lowest cell voltage
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) minPack));
    // 13 Cell number with lowest voltage
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) aggregatedPack.minCellVNum));

    // ---- Cell temperatures ----
    // 12 in the table – average cell temperature (°C + 273.1*10)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));

    maxPack = 0;
    minPack = 0;

    for (int i = 0; i < getEnergyStorage().getBatteryPacks().size(); i++) {
        final BatteryPack pack = getEnergyStorage().getBatteryPack(i);

        if (pack.tempMax == aggregatedPack.tempMax) {
            maxPack = i;
        }
        if (pack.tempMin == aggregatedPack.tempMin) {
            minPack = i;
        }
    }

    // 13  Max cell temperature (K*10)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempMax + 2731)));
    // 14  Pack of max cell temperature
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) maxPack));
    // 15  Probe number of max temp
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) aggregatedPack.tempMaxCellNum));

    // 16  Min cell temperature (K*10)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempMin + 2731)));
    // 17  Pack of min cell temperature
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) minPack));
    // 18  Probe number of min temp
    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(
            (byte) aggregatedPack.tempMinCellNum));

    // ---- MOSFET temperatures (using pack average as a proxy) ----
    // 19 MOSFET average temperature
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));
    // 20 MOSFET max temperature
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));
    // 21 MOSFET max temp pack
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) 0));
    // 22 MOSFET min temperature
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));
    // 23 MOSFET min temp pack
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) 0));

    // ---- BMS temperatures (again using average as proxy) ----
    // 24 BMS average temperature
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));
    // 25 BMS max temperature
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));
    // 26 BMS max temp pack
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) 0));
    // 27 BMS min temperature
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.tempAverage + 2731)));
    // 28 BMS min temp pack
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) 0));

    final byte[] data = new byte[buffer.position()];
    buffer.flip();
    buffer.get(data, 0, data.length);

    return data;
}


    // 0x62
    private byte[] createAlarms(final BatteryPack pack) {
        final byte[] alarms = new byte[8];

        // warning alarms 1
        byte value = 0;
        value = BitUtil.setBit(value, 7, pack.getAlarmLevel(Alarm.PACK_VOLTAGE_HIGH) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 6, pack.getAlarmLevel(Alarm.PACK_VOLTAGE_LOW) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 5, pack.getAlarmLevel(Alarm.CELL_VOLTAGE_HIGH) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 4, pack.getAlarmLevel(Alarm.CELL_VOLTAGE_LOW) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 3, pack.getAlarmLevel(Alarm.CELL_TEMPERATURE_HIGH) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 2, pack.getAlarmLevel(Alarm.CELL_TEMPERATURE_LOW) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 1, false);
        value = BitUtil.setBit(value, 0, pack.getAlarmLevel(Alarm.CELL_VOLTAGE_DIFFERENCE_HIGH) == AlarmLevel.WARNING);
        byte[] bytes = ByteAsciiConverter.convertByteToAsciiBytes(value);
        alarms[0] = bytes[0];
        alarms[1] = bytes[1];

        // warning alarms 2
        value = 0;
        value = BitUtil.setBit(value, 7, pack.getAlarmLevel(Alarm.TEMPERATURE_SENSOR_DIFFERENCE_HIGH) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 6, pack.getAlarmLevel(Alarm.CHARGE_CURRENT_HIGH) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 5, pack.getAlarmLevel(Alarm.DISCHARGE_CURRENT_HIGH) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 4, pack.getAlarmLevel(Alarm.FAILURE_COMMUNICATION_INTERNAL) == AlarmLevel.WARNING);
        value = BitUtil.setBit(value, 3, false);
        value = BitUtil.setBit(value, 2, false);
        value = BitUtil.setBit(value, 1, false);
        value = BitUtil.setBit(value, 0, false);
        bytes = ByteAsciiConverter.convertByteToAsciiBytes(value);
        alarms[2] = bytes[0];
        alarms[3] = bytes[1];

        // protection alarms 1
        value = 0;
        value = BitUtil.setBit(value, 7, pack.getAlarmLevel(Alarm.PACK_VOLTAGE_HIGH) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 6, pack.getAlarmLevel(Alarm.PACK_VOLTAGE_LOW) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 5, pack.getAlarmLevel(Alarm.CELL_VOLTAGE_HIGH) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 4, pack.getAlarmLevel(Alarm.CELL_VOLTAGE_LOW) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 3, pack.getAlarmLevel(Alarm.CELL_TEMPERATURE_HIGH) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 2, pack.getAlarmLevel(Alarm.CELL_TEMPERATURE_LOW) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 1, false);
        value = BitUtil.setBit(value, 0, false);
        bytes = ByteAsciiConverter.convertByteToAsciiBytes(value);
        alarms[4] = bytes[0];
        alarms[5] = bytes[1];

        // protection alarms 2
        value = 0;
        value = BitUtil.setBit(value, 7, false);
        value = BitUtil.setBit(value, 6, pack.getAlarmLevel(Alarm.CHARGE_CURRENT_HIGH) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 5, pack.getAlarmLevel(Alarm.DISCHARGE_CURRENT_HIGH) == AlarmLevel.ALARM);
        value = BitUtil.setBit(value, 4, false);
        value = BitUtil.setBit(value, 3, pack.getAlarmLevel(Alarm.FAILURE_OTHER) == AlarmLevel.ALARM);
        BitUtil.setBit(value, 2, false);
        value = BitUtil.setBit(value, 1, false);
        value = BitUtil.setBit(value, 0, false);
        bytes = ByteAsciiConverter.convertByteToAsciiBytes(value);
        alarms[6] = bytes[0];
        alarms[7] = bytes[1];

        return alarms;
    }

// 0x63 – Get System charge/discharge control info
private byte[] createChargeDischargeIfno(final BatteryPack aggregatedPack) {
    final ByteBuffer buffer = ByteBuffer.allocate(4096);

    // 1  Max system charge voltage (V * 0.01)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.maxPackVoltageLimit * 100)));

    // 2  Min system discharge voltage (V * 0.01)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.minPackVoltageLimit * 100)));

    // 3  Max charge current (A * 0.1)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.maxPackChargeCurrent * 10)));

    // 4  Max discharge current (A * 0.1)
    buffer.put(ByteAsciiConverter.convertShortToAsciiBytes(
            (short) (aggregatedPack.maxPackDischargeCurrent * 10)));

    // 5  MOSFET / force-charge flags (same as emulator)
    byte chargeDischargeMOSStates = 0x00;
    chargeDischargeMOSStates = BitUtil.setBit(chargeDischargeMOSStates, 7, aggregatedPack.chargeMOSState);
    chargeDischargeMOSStates = BitUtil.setBit(chargeDischargeMOSStates, 6, aggregatedPack.dischargeMOSState);
    chargeDischargeMOSStates = BitUtil.setBit(chargeDischargeMOSStates, 5, aggregatedPack.forceCharge);

    buffer.put(ByteAsciiConverter.convertByteToAsciiBytes(chargeDischargeMOSStates));

    final byte[] data = new byte[buffer.position()];
    buffer.flip();
    buffer.get(data, 0, data.length);

    return data;
}


    
@Override
protected ByteBuffer readRequest(final Port port) throws IOException {
    try {
        return port.receiveFrame();
    } catch (IOException e) {
        // Inverter didn't send a full frame in time; log and treat as "no request".
        if (LOG.isDebugEnabled()) {
            LOG.debug("Pylon P3.5 readRequest: no complete frame available yet: {}", e.getMessage());
        }
        return null;  // <- this is key: "no frame", not a fatal error
    }
}




    @Override
    protected void sendFrame(final Port port, final ByteBuffer frame) throws IOException {
        port.sendFrame(frame);
    }


    private int resolveCurrentLimitA10(final int rawLimit01A, final String label, final BatteryPack pack) {
        double amps = Math.abs(rawLimit01A) / 10.0;

        if (Double.isNaN(amps) || amps <= 0.0) {
            amps = DEFAULT_CURRENT_LIMIT_A;
        }

        final int encoded = encodeCurrentLimitA10(amps);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Pylon P3.5 frame: using {}={}A ({} A×10) for pack {}", label, String.format("%.1f", amps), encoded,
                    pack.serialnumber == null || pack.serialnumber.isEmpty() ? "n/a" : pack.serialnumber);
        }

        return encoded;
    }


    private int sanitizeSoc(final BatteryPack pack) {
        if (pack.packSOC > 0) {
            return pack.packSOC;
        }

        if (pack.packVoltage > 0 && pack.numberOfCells > 0) {
            final double perCellVoltage = pack.packVoltage / 10.0 / pack.numberOfCells;
            final double estimatedSoc = (perCellVoltage - 3.0) / 0.45;
            final int soc = (int) Math.round(Math.min(1.0, Math.max(0.0, estimatedSoc)) * 1000);

            if (soc > 0) {
                return soc;
            }

            if (perCellVoltage < 2.8) {
                return 0;
            }
        }

        return DEFAULT_SOC_TENTHS;
    }


    private int sanitizeTemperature(final int temperature) {
        if (temperature != 0 && temperature > -400 && temperature < 1000) {
            return temperature;
        }

        return DEFAULT_TEMPERATURE_TENTHS;
    }


    private int encodeCurrentLimitA10(final double amps) {
        if (Double.isNaN(amps) || amps <= 0) {
            return 0;
        }

        long raw = Math.round(Math.abs(amps) * 10.0);

        if (raw > 0xFFFFL) {
            raw = 0xFFFFL;
        }

        return (int) raw;
    }


    private String padRight(final String value, final int length, final char padChar) {
        if (length <= 0) {
            return value;
        }

        final StringBuilder builder = new StringBuilder(value);

        final int padCount = Math.max(0, length - value.length());
        for (int i = 0; i < padCount; i++) {
            builder.append(padChar);
        }

        return builder.toString();
    }


    ByteBuffer prepareSendFrame(final byte address, final byte cid1, final byte cid2, final byte[] data) {
        final ByteBuffer sendFrame = ByteBuffer.allocate(18 + data.length).order(ByteOrder.BIG_ENDIAN);
        sendFrame.put((byte) 0x7E); // Start flag
        sendFrame.put((byte) 0x32); // version
        sendFrame.put((byte) 0x30); // version
        sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(address)); // address
        sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(cid1)); // command CID1
        sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(cid2)); // command CID2
        // Frame Length Byte
        sendFrame.put(createLengthCheckSum(data.length));
        // data
        sendFrame.put(data);
        // checksum
        sendFrame.put(createChecksum(sendFrame, sendFrame.position()));
        sendFrame.put((byte) 0x0D); // End flag

        return sendFrame;
    }


    private byte[] createChecksum(final ByteBuffer sendFrame, final int bodyLength) {
        int sum = 0;

        // We assume the first byte is the SOI (0x7E) and is NOT included in checksum.
        // Only sum the ASCII bytes from VER through the last INFO byte.
        for (int i = 1; i < bodyLength; i++) {
            sum += sendFrame.get(i);
        }

        // Two’s complement
        final int checksum = ~sum + 1 & 0xFFFF;

        // Return as two bytes (big-endian)
        final byte[] result = new byte[2];
        result[0] = (byte) (checksum >> 8 & 0xFF);
        result[1] = (byte) (checksum & 0xFF);

        // convert them to ascii
        final byte[] highBytes = ByteAsciiConverter.convertByteToAsciiBytes(result[0]);
        final byte[] lowBytes = ByteAsciiConverter.convertByteToAsciiBytes(result[1]);
        final byte[] data = new byte[4];
        data[0] = highBytes[0];
        data[1] = highBytes[1];
        data[2] = lowBytes[0];
        data[3] = lowBytes[1];

        return data;

    }


    private byte[] createLengthCheckSum(final int infoAsciiLength) {
        // LENID is the number of ASCII bytes in the INFO field (already ASCII-encoded).
        final int lenId = infoAsciiLength & 0x0FFF;

        // Calculate LCHKSUM nibble using complement-of-sum-of-nibbles + 1
        final int d11_8 = lenId >> 8 & 0x0F;
        final int d7_4 = lenId >> 4 & 0x0F;
        final int d3_0 = lenId & 0x0F;

        final int sum = d11_8 + d7_4 + d3_0;
        final int lchk = ~(sum & 0x0F) + 1 & 0x0F;

        // Combine into 16-bit LENGTH value
        final int lengthField = (lchk << 12 | lenId) & 0xFFFF;

        // Convert to 2 bytes (big-endian)
        final byte[] result = new byte[2];
        result[0] = (byte) (lengthField >> 8 & 0xFF); // high byte
        result[1] = (byte) (lengthField & 0xFF); // low byte

        // convert them to ascii
        final byte[] highBytes = ByteAsciiConverter.convertByteToAsciiBytes(result[0]);
        final byte[] lowBytes = ByteAsciiConverter.convertByteToAsciiBytes(result[1]);
        final byte[] data = new byte[4];
        data[0] = highBytes[0];
        data[1] = highBytes[1];
        data[2] = lowBytes[0];
        data[3] = lowBytes[1];

        return data;
    }


    public static void main(final String[] args) {
        final PylonInverterRS485Processor processor = new PylonInverterRS485Processor();
        final EnergyStorage energyStorage = new EnergyStorage();
        energyStorage.fromJson(
                "{\"batteryPacks\":[{\"alarms\":{\"DISCHARGE_MODULE_TEMPERATURE_HIGH\":\"NONE\",\"FAILURE_OTHER\":\"NONE\",\"DISCHARGE_VOLTAGE_LOW\":\"NONE\",\"PACK_TEMPERATURE_HIGH\":\"NONE\",\"PACK_VOLTAGE_HIGH\":\"NONE\",\"DISCHARGE_CURRENT_HIGH\":\"NONE\",\"CHARGE_MODULE_TEMPERATURE_HIGH\":\"NONE\",\"CHARGE_VOLTAGE_HIGH\":\"NONE\",\"PACK_VOLTAGE_LOW\":\"NONE\",\"SOC_LOW\":\"NONE\",\"PACK_TEMPERATURE_LOW\":\"NONE\",\"ENCASING_TEMPERATURE_HIGH\":\"NONE\",\"CELL_VOLTAGE_DIFFERENCE_HIGH\":\"NONE\",\"CHARGE_CURRENT_HIGH\":\"NONE\"},\"type\":0,\"ratedCapacitymAh\":280000,\"ratedCellmV\":0,\"maxPackVoltageLimit\":288,\"minPackVoltageLimit\":208,\"maxPackChargeCurrent\":1000,\"maxPackDischargeCurrent\":2000,\"packVoltage\":266,\"packCurrent\":0,\"packSOC\":1000,\"packSOH\":0,\"maxCellVoltageLimit\":3600,\"minCellVoltageLimit\":2600,\"maxCellmV\":3333,\"maxCellVNum\":1,\"minCellmV\":3332,\"minCellVNum\":0,\"cellDiffmV\":1,\"tempMax\":0,\"tempMin\":0,\"tempAverage\":180,\"chargeDischargeStatus\":0,\"chargeMOSState\":true,\"dischargeMOSState\":true,\"forceCharge\":false,\"forceDischarge\":false,\"bmsHeartBeat\":0,\"remainingCapacitymAh\":0,\"numberOfCells\":8,\"numOfTempSensors\":2,\"chargerState\":false,\"loadState\":false,\"dIO\":[false,false,false,false,false,false,false,false],\"bmsCycles\":0,\"cellVmV\":[3332,3333,3332,3332,3332,3332,3333,3332,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],\"cellTemperature\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],\"cellBalanceState\":[false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false],\"cellBalanceActive\":false,\"manufacturerCode\":\"Input Userda407272C2727\\u0000\",\"hardwareVersion\":\"\",\"softwareVersion\":\"NW\\u0000\\u0013\\u0000\\u0000\\u0000\\u0000\\u0006\\u0003\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000h\\u0000\\u0000\\u0001)\",\"tempMaxCellNum\":0,\"tempMinCellNum\":0,\"maxModulemV\":0,\"minModulemV\":0,\"maxModulemVNum\":0,\"minModulemVNum\":0,\"maxModuleTemp\":0,\"minModuleTemp\":0,\"maxModuleTempNum\":0,\"minModuleTempNum\":0,\"modulesInSeries\":8,\"moduleNumberOfCells\":0,\"moduleVoltage\":0,\"moduleRatedCapacityAh\":0}]}");

        final ByteBuffer request = ByteBuffer.wrap(new byte[] { (byte) 0x7E, (byte) 0x32, (byte) 0x30, (byte) 0x30, (byte) 0x32, (byte) 0x34, (byte) 0x36, (byte) 0x36, (byte) 0x31, (byte) 0x45, (byte) 0x30, (byte) 0x30, (byte) 0x32, (byte) 0x30, (byte) 0x31, (byte) 0x46, (byte) 0x44, (byte) 0x33, (byte) 0x33, (byte) 0x0D });
        final BatteryPack pack = energyStorage.getBatteryPack(0);

        processor.createSendFrames(request, pack);
    }
}
