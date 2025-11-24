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

    // ====== EMULATION TOGGLES ======
    private static final boolean USE_EMULATOR_61 = false;   // true = use emulator 0x61 payload
    private static final boolean USE_EMULATOR_63 = false;   // true = use emulator 0x63 payload
    
    private static final byte[] EMU_PAYLOAD_61 = hexStringToByteArray("CB20000050006400C863620DB801010CBB01010BAA0BB701010B9D01010BAA0BB801010B9C01010BAA0BB601010B9E0101");

    private static final byte[] EMU_PAYLOAD_63 = hexStringToByteArray("D2F0ABE000FA00C8C0");
    
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

/*            byte[] responseData = null;

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
                case 0x61: // 0x61 Battery Information
                    responseData = createBatteryInformation(aggregatedPack);
                break;
                case 0x62:
                    responseData = createAlarms(aggregatedPack);
                break; // 0x62 Alarms
                case 0x63:
                    final byte[] info63 = createChargeDischargeIfno(aggregatedPack);
                    LOG.debug("Payload 63 length = {}", info63 != null ? info63.length : -1);
                    frames.add(prepareSendFrame(adr, (byte) 0x46, (byte) 0x63, info63, true)); // <-- binary INFO
                break;


                default:
                    // not supported
                    return frames;
            }

            final ByteBuffer responseFrame = prepareSendFrame(adr, cid1, (byte) 0x00, responseData);
            frames.add(responseFrame);
            LOG.info("TX ASCII: {}", toAsciiString(responseFrame));
            LOG.debug("Responding to inverter with: {}", Port.printBuffer(responseFrame));

*/

            byte[] responseData = null;

            switch (cid2) {
                case 0x4F:
                    responseData = createProtocolVersion(aggregatedPack);
                    LOG.debug("Payload 4F length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case 0x51:
                    responseData = createManufacturerCode(aggregatedPack);
                    LOG.debug("Payload 51 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case (byte) 0x92:
                    responseData = createChargeDischargeManagementInfo(aggregatedPack);
                    LOG.debug("Payload 92 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case 0x42:
                    responseData = createCellInformation(aggregatedPack);
                    LOG.debug("Payload 42 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case 0x47:
                    responseData = createVoltageCurrentLimits(aggregatedPack);
                    LOG.debug("Payload 47 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case 0x60:
                    responseData = createSystemInfo(aggregatedPack);
                    LOG.debug("Payload 60 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case 0x61:
                    responseData = createBatteryInformation(aggregatedPack);
                    LOG.debug("Payload 61 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                case 0x63:
                    responseData = createChargeDischargeIfno(aggregatedPack);
                    LOG.debug("Payload 63 length = {}", responseData != null ? responseData.length : -1);
                    break;
            
                default:
                    LOG.warn("Unsupported CID2 0x{}, not sending any frames",
                             String.format("%02X", cid2));
                    break;
            }

            // ⬇️ This is the important part
            if (responseData == null) {
                // Inverter asked for something we don't implement – just don't answer.
                return frames;
            }
            
            ByteBuffer frame = prepareSendFrame(adr, (byte) 0x46, cid2, responseData);
            if (frame != null) {
                frames.add(frame);
            }
            return frames;
                        
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
private byte[] createBatteryInformation(final BatteryPack aggregatedPack) {
    
if (USE_EMULATOR_61) {
    LOG.warn("0x61: Using EMULATOR payload.");
    return EMU_PAYLOAD_61;
}

    
    
    final ByteBuffer buffer = ByteBuffer.allocate(4096);

    LOG.debug("createBatteryInformation(): START for aggregatedPack = {}", aggregatedPack);

    // --------------------------------------------------------------------
    // Interpret tempAverage safely: if it's 0, approximate as midpoint
    // between tempMin and tempMax
    // aggregatedPack.* temps are in 0.1°C units
    // --------------------------------------------------------------------
    int tempAvgTenth = aggregatedPack.tempAverage;
    if (tempAvgTenth == 0) {
        tempAvgTenth = (aggregatedPack.tempMin + aggregatedPack.tempMax) / 2;
        LOG.debug("tempAverage was 0; approximated as midpoint: rawMid={} (0.1°C) ≈ {} °C",
                tempAvgTenth, tempAvgTenth / 10.0);
    }

    // ========== PACK VOLTAGE ==========
    // aggregatedPack.packVoltage seems to be in 0.1 V units (e.g. 536 => 53.6 V)
    int startPos = buffer.position();
    double packVoltageRealV = aggregatedPack.packVoltage / 10.0; // human-readable volts
    int packVoltageScaled = (int) (packVoltageRealV * 10);        // 0.1 V resolution for Pylon
    byte[] packVoltageBytes = ByteAsciiConverter.convertCharToAsciiBytes((char) packVoltageScaled);

    LOG.debug(
        "packVoltage: raw={} (0.1V units) -> approxReal={} V, encodedScaled={} (0.1V units), " +
        "encodedHex=0x{} asciiBytes={} (len={}, startPos={})",
        aggregatedPack.packVoltage,
        packVoltageRealV,
        packVoltageScaled,
        String.format("%04X", packVoltageScaled),
        bytesToHex(packVoltageBytes),
        packVoltageBytes.length,
        startPos
    );
    buffer.put(packVoltageBytes);

    // ========== PACK CURRENT ==========
    // aggregatedPack.packCurrent appears to be in 0.1 A units (e.g. 178 => 17.8 A)
    startPos = buffer.position();
    double packCurrentRealA = aggregatedPack.packCurrent / 10.0; // human-readable amps
    // For now we encode in 0.1 A units as a short (Pylon expects signed current)
    short packCurrentScaled = (short) aggregatedPack.packCurrent;
    byte[] packCurrentBytes = ByteAsciiConverter.convertShortToAsciiBytes(packCurrentScaled);

    LOG.debug(
        "packCurrent: raw={} (0.1A units) -> approxReal={} A, encodedScaled={} (0.1A), " +
        "encodedHex=0x{} asciiBytes={} (len={}, startPos={})",
        aggregatedPack.packCurrent,
        packCurrentRealA,
        packCurrentScaled,
        String.format("%04X", packCurrentScaled & 0xFFFF),
        bytesToHex(packCurrentBytes),
        packCurrentBytes.length,
        startPos
    );
    buffer.put(packCurrentBytes);

    // ========== PACK SOC ==========
    // aggregatedPack.packSOC seems to be in 0.1 % units (e.g. 893 => 89.3%)
    startPos = buffer.position();
    double socRealPct = aggregatedPack.packSOC / 10.0;
    byte packSocScaled = (byte) (aggregatedPack.packSOC / 10); // integer %
    byte[] packSocBytes = ByteAsciiConverter.convertByteToAsciiBytes(packSocScaled);

    LOG.debug(
        "packSOC: raw={} (0.1%% units) -> approxReal={} %%, encodedScaled={} (1%%), asciiBytes={} (len={}, startPos={})",
        aggregatedPack.packSOC,
        socRealPct,
        packSocScaled,
        bytesToHex(packSocBytes),
        packSocBytes.length,
        startPos
    );
    buffer.put(packSocBytes);

    // ========== BMS CYCLES (AVERAGE) ==========
    startPos = buffer.position();
    short avgCycles = (short) aggregatedPack.bmsCycles;
    byte[] avgCyclesBytes = ByteAsciiConverter.convertShortToAsciiBytes(avgCycles);
    LOG.debug("bmsCycles (avg): raw={} cycles, asciiBytes={} (len={}, startPos={})",
            aggregatedPack.bmsCycles, bytesToHex(avgCyclesBytes),
            avgCyclesBytes.length, startPos);
    buffer.put(avgCyclesBytes);

    // ========== BMS CYCLES (MAX = 10000) ==========
    startPos = buffer.position();
    short maxCycles = (short) 10000;
    byte[] maxCyclesBytes = ByteAsciiConverter.convertShortToAsciiBytes(maxCycles);
    LOG.debug("bmsCycles (max): fixedRaw={} cycles, asciiBytes={} (len={}, startPos={})",
            maxCycles, bytesToHex(maxCyclesBytes),
            maxCyclesBytes.length, startPos);
    buffer.put(maxCyclesBytes);

    // ========== SOH (AVERAGE & LOWEST) ==========
    // aggregatedPack.packSOH is in 0.1 % units
    startPos = buffer.position();
    double sohRealPct = aggregatedPack.packSOH / 10.0;
    byte sohAvgScaled = (byte) Math.max(1, Math.min(100, aggregatedPack.packSOH / 10));
    byte[] sohAvgBytes = ByteAsciiConverter.convertByteToAsciiBytes(sohAvgScaled);
    LOG.debug(
        "packSOH (avg): raw={} (0.1%% units) -> approxReal={} %%, encodedScaled={} (1%%), asciiBytes={} (len={}, startPos={})",
        aggregatedPack.packSOH,
        sohRealPct,
        sohAvgScaled,
        bytesToHex(sohAvgBytes),
        sohAvgBytes.length,
        startPos
    );
    buffer.put(sohAvgBytes);

    startPos = buffer.position();
    byte sohLowScaled = (byte) (aggregatedPack.packSOH / 10); // same as avg in your code
    byte[] sohLowBytes = ByteAsciiConverter.convertByteToAsciiBytes(sohLowScaled);
    LOG.debug(
        "packSOH (low): raw={} (0.1%% units) -> approxReal={} %%, encodedScaled={} (1%%), asciiBytes={} (len={}, startPos={})",
        aggregatedPack.packSOH,
        sohRealPct,
        sohLowScaled,
        bytesToHex(sohLowBytes),
        sohLowBytes.length,
        startPos
    );
    buffer.put(sohLowBytes);

    // ========== FIND PACK WITH MAX/MIN CELL VOLTAGE ==========
    int maxPack = 0;
    int minPack = 0;

    LOG.debug("Searching packs for max/min cell mV. aggregatedPack.maxCellmV={} mV, minCellmV={} mV",
            aggregatedPack.maxCellmV, aggregatedPack.minCellmV);

    for (int i = 0; i < getEnergyStorage().getBatteryPacks().size(); i++) {
        final BatteryPack pack = getEnergyStorage().getBatteryPack(i);
        LOG.debug("Pack[{}]: maxCellmV={} mV, minCellmV={} mV", i, pack.maxCellmV, pack.minCellmV);

        if (pack.maxCellmV == aggregatedPack.maxCellmV) {
            maxPack = i;
        }
        if (pack.minCellmV == aggregatedPack.minCellmV) {
            minPack = i;
        }
    }

    LOG.debug("maxPack index={} (maxCellmV), minPack index={} (minCellmV)", maxPack, minPack);

    // ========== MAX CELL VOLTAGE & LOCATION ==========
    startPos = buffer.position();
    short maxCellmV = (short) aggregatedPack.maxCellmV;
    byte[] maxCellBytes = ByteAsciiConverter.convertShortToAsciiBytes(maxCellmV);
    LOG.debug(
        "maxCellmV: raw={} mV -> approxReal={} V, encodedHex=0x{}, asciiBytes={} (len={}, startPos={})",
        aggregatedPack.maxCellmV,
        aggregatedPack.maxCellmV / 1000.0,
        String.format("%04X", maxCellmV & 0xFFFF),
        bytesToHex(maxCellBytes),
        maxCellBytes.length,
        startPos
    );
    buffer.put(maxCellBytes);

    startPos = buffer.position();
    byte maxPackByte = (byte) maxPack;
    byte[] maxPackBytes = ByteAsciiConverter.convertByteToAsciiBytes(maxPackByte);
    LOG.debug("maxCellV packIndex: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            maxPackByte, bytesToHex(maxPackBytes),
            maxPackBytes.length, startPos);
    buffer.put(maxPackBytes);

    startPos = buffer.position();
    byte maxCellVNum = (byte) aggregatedPack.maxCellVNum;
    byte[] maxCellVNumBytes = ByteAsciiConverter.convertByteToAsciiBytes(maxCellVNum);
    LOG.debug("maxCellV cellNum: rawCellIndex={}, asciiBytes={} (len={}, startPos={})",
            aggregatedPack.maxCellVNum, bytesToHex(maxCellVNumBytes),
            maxCellVNumBytes.length, startPos);
    buffer.put(maxCellVNumBytes);

    // ========== MIN CELL VOLTAGE & LOCATION ==========
    startPos = buffer.position();
    short minCellmV = (short) aggregatedPack.minCellmV;
    byte[] minCellBytes = ByteAsciiConverter.convertShortToAsciiBytes(minCellmV);
    LOG.debug(
        "minCellmV: raw={} mV -> approxReal={} V, encodedHex=0x{}, asciiBytes={} (len={}, startPos={})",
        aggregatedPack.minCellmV,
        aggregatedPack.minCellmV / 1000.0,
        String.format("%04X", minCellmV & 0xFFFF),
        bytesToHex(minCellBytes),
        minCellBytes.length,
        startPos
    );
    buffer.put(minCellBytes);

    startPos = buffer.position();
    byte minPackByte = (byte) minPack;
    byte[] minPackBytes = ByteAsciiConverter.convertByteToAsciiBytes(minPackByte);
    LOG.debug("minCellV packIndex: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            minPackByte, bytesToHex(minPackBytes),
            minPackBytes.length, startPos);
    buffer.put(minPackBytes);

    startPos = buffer.position();
    byte minCellVNum = (byte) aggregatedPack.minCellVNum;
    byte[] minCellVNumBytes = ByteAsciiConverter.convertByteToAsciiBytes(minCellVNum);
    LOG.debug("minCellV cellNum: rawCellIndex={}, asciiBytes={} (len={}, startPos={})",
            aggregatedPack.minCellVNum, bytesToHex(minCellVNumBytes),
            minCellVNumBytes.length, startPos);
    buffer.put(minCellVNumBytes);

    // ========== AVERAGE TEMPERATURE ==========
    // Temps are in 0.1°C units; we shift +273.1°C in 0.1°C units => +2731
    startPos = buffer.position();
    short tempAverageK = (short) (tempAvgTenth + 2731);
    byte[] tempAverageBytes = ByteAsciiConverter.convertShortToAsciiBytes(tempAverageK);
    LOG.debug(
        "tempAverage: raw={} (0.1°C) -> approxReal={} °C, shiftedRaw={} (+2731), asciiBytes={} (len={}, startPos={})",
        aggregatedPack.tempAverage,
        tempAvgTenth / 10.0,
        tempAverageK,
        bytesToHex(tempAverageBytes),
        tempAverageBytes.length,
        startPos
    );
    buffer.put(tempAverageBytes);

    // ========== FIND PACK WITH MAX/MIN TEMPERATURE ==========
    maxPack = 0;
    minPack = 0;

    LOG.debug("Searching packs for max/min temp. aggregatedPack.tempMax={} (0.1°C), tempMin={} (0.1°C)",
            aggregatedPack.tempMax, aggregatedPack.tempMin);

    for (int i = 0; i < getEnergyStorage().getBatteryPacks().size(); i++) {
        final BatteryPack pack = getEnergyStorage().getBatteryPack(i);
        LOG.debug("Pack[{}]: tempMax={} (0.1°C) ≈ {} °C, tempMin={} (0.1°C) ≈ {} °C",
                i, pack.tempMax, pack.tempMax / 10.0,
                pack.tempMin, pack.tempMin / 10.0);

        if (pack.tempMax == aggregatedPack.tempMax) {
            maxPack = i;
        }
        if (pack.tempMin == aggregatedPack.tempMin) {
            minPack = i;
        }
    }

    LOG.debug("temp maxPack index={} (tempMax), minPack index={} (tempMin)", maxPack, minPack);

    // ========== MAX TEMP & LOCATION ==========
    startPos = buffer.position();
    short tempMaxK = (short) (aggregatedPack.tempMax + 2731);
    byte[] tempMaxBytes = ByteAsciiConverter.convertShortToAsciiBytes(tempMaxK);
    LOG.debug(
        "tempMax: raw={} (0.1°C) -> approxReal={} °C, shiftedRaw={} (+2731), asciiBytes={} (len={}, startPos={})",
        aggregatedPack.tempMax,
        aggregatedPack.tempMax / 10.0,
        tempMaxK,
        bytesToHex(tempMaxBytes),
        tempMaxBytes.length,
        startPos
    );
    buffer.put(tempMaxBytes);

    startPos = buffer.position();
    byte tempMaxPackByte = (byte) maxPack;
    byte[] tempMaxPackBytes = ByteAsciiConverter.convertByteToAsciiBytes(tempMaxPackByte);
    LOG.debug("tempMax packIndex: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            tempMaxPackByte, bytesToHex(tempMaxPackBytes),
            tempMaxPackBytes.length, startPos);
    buffer.put(tempMaxPackBytes);

    startPos = buffer.position();
    byte tempMaxCellNum = (byte) aggregatedPack.tempMaxCellNum;
    byte[] tempMaxCellNumBytes = ByteAsciiConverter.convertByteToAsciiBytes(tempMaxCellNum);
    LOG.debug("tempMax cellNum: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            aggregatedPack.tempMaxCellNum, bytesToHex(tempMaxCellNumBytes),
            tempMaxCellNumBytes.length, startPos);
    buffer.put(tempMaxCellNumBytes);

    // ========== MIN TEMP & LOCATION ==========
    startPos = buffer.position();
    short tempMinK = (short) (aggregatedPack.tempMin + 2731);
    byte[] tempMinBytes = ByteAsciiConverter.convertShortToAsciiBytes(tempMinK);
    LOG.debug(
        "tempMin: raw={} (0.1°C) -> approxReal={} °C, shiftedRaw={} (+2731), asciiBytes={} (len={}, startPos={})",
        aggregatedPack.tempMin,
        aggregatedPack.tempMin / 10.0,
        tempMinK,
        bytesToHex(tempMinBytes),
        tempMinBytes.length,
        startPos
    );
    buffer.put(tempMinBytes);

    startPos = buffer.position();
    byte tempMinPackByte = (byte) minPack;
    byte[] tempMinPackBytes = ByteAsciiConverter.convertByteToAsciiBytes(tempMinPackByte);
    LOG.debug("tempMin packIndex: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            tempMinPackByte, bytesToHex(tempMinPackBytes),
            tempMinPackBytes.length, startPos);
    buffer.put(tempMinPackBytes);

    startPos = buffer.position();
    byte tempMinCellNum = (byte) aggregatedPack.tempMinCellNum;
    byte[] tempMinCellNumBytes = ByteAsciiConverter.convertByteToAsciiBytes(tempMinCellNum);
    LOG.debug("tempMin cellNum: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            aggregatedPack.tempMinCellNum, bytesToHex(tempMinCellNumBytes),
            tempMinCellNumBytes.length, startPos);
    buffer.put(tempMinCellNumBytes);

    // ========== MOSFET TEMPERATURES ==========
    short mosfetAvgK = (short) (tempAvgTenth + 2731);
    short mosfetMaxK = mosfetAvgK;
    short mosfetMinK = mosfetAvgK;
    short mosfetPackIdx = 0;

    startPos = buffer.position();
    byte[] mosfetAvgBytes = ByteAsciiConverter.convertShortToAsciiBytes(mosfetAvgK);
    LOG.debug("MOSFET tempAverage: rawMid={} (0.1°C) -> approxReal={} °C, shiftedRaw={}, asciiBytes={} (len={}, startPos={})",
            tempAvgTenth,
            tempAvgTenth / 10.0,
            mosfetAvgK,
            bytesToHex(mosfetAvgBytes),
            mosfetAvgBytes.length,
            startPos);
    buffer.put(mosfetAvgBytes);

    startPos = buffer.position();
    byte[] mosfetMaxBytes = ByteAsciiConverter.convertShortToAsciiBytes(mosfetMaxK);
    LOG.debug("MOSFET tempMax: same as avg, shiftedRaw={}, asciiBytes={} (len={}, startPos={})",
            mosfetMaxK, bytesToHex(mosfetMaxBytes),
            mosfetMaxBytes.length, startPos);
    buffer.put(mosfetMaxBytes);

    startPos = buffer.position();
    byte[] mosfetMaxPackBytes = ByteAsciiConverter.convertShortToAsciiBytes(mosfetPackIdx);
    LOG.debug("MOSFET tempMax pack: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            mosfetPackIdx, bytesToHex(mosfetMaxPackBytes),
            mosfetMaxPackBytes.length, startPos);
    buffer.put(mosfetMaxPackBytes);

    startPos = buffer.position();
    byte[] mosfetMinBytes = ByteAsciiConverter.convertShortToAsciiBytes(mosfetMinK);
    LOG.debug("MOSFET tempMin: same as avg, shiftedRaw={}, asciiBytes={} (len={}, startPos={})",
            mosfetMinK, bytesToHex(mosfetMinBytes),
            mosfetMinBytes.length, startPos);
    buffer.put(mosfetMinBytes);

    startPos = buffer.position();
    byte[] mosfetMinPackBytes = ByteAsciiConverter.convertShortToAsciiBytes(mosfetPackIdx);
    LOG.debug("MOSFET tempMin pack: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            mosfetPackIdx, bytesToHex(mosfetMinPackBytes),
            mosfetMinPackBytes.length, startPos);
    buffer.put(mosfetMinPackBytes);

    // ========== BMS TEMPERATURES ==========
    short bmsAvgK = mosfetAvgK;
    short bmsMaxK = bmsAvgK;
    short bmsMinK = bmsAvgK;
    short bmsPackIdx = 0;

    startPos = buffer.position();
    byte[] bmsAvgBytes = ByteAsciiConverter.convertShortToAsciiBytes(bmsAvgK);
    LOG.debug("BMS tempAverage: shiftedRaw={}, asciiBytes={} (len={}, startPos={})",
            bmsAvgK, bytesToHex(bmsAvgBytes),
            bmsAvgBytes.length, startPos);
    buffer.put(bmsAvgBytes);

    startPos = buffer.position();
    byte[] bmsMaxBytes = ByteAsciiConverter.convertShortToAsciiBytes(bmsMaxK);
    LOG.debug("BMS tempMax: same as avg, shiftedRaw={}, asciiBytes={} (len={}, startPos={})",
            bmsMaxK, bytesToHex(bmsMaxBytes),
            bmsMaxBytes.length, startPos);
    buffer.put(bmsMaxBytes);

    startPos = buffer.position();
    byte[] bmsMaxPackBytes = ByteAsciiConverter.convertShortToAsciiBytes(bmsPackIdx);
    LOG.debug("BMS tempMax pack: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            bmsPackIdx, bytesToHex(bmsMaxPackBytes),
            bmsMaxPackBytes.length, startPos);
    buffer.put(bmsMaxPackBytes);

    startPos = buffer.position();
    byte[] bmsMinBytes = ByteAsciiConverter.convertShortToAsciiBytes(bmsMinK);
    LOG.debug("BMS tempMin: same as avg, shiftedRaw={}, asciiBytes={} (len={}, startPos={})",
            bmsMinK, bytesToHex(bmsMinBytes),
            bmsMinBytes.length, startPos);
    buffer.put(bmsMinBytes);

    startPos = buffer.position();
    byte[] bmsMinPackBytes = ByteAsciiConverter.convertShortToAsciiBytes(bmsPackIdx);
    LOG.debug("BMS tempMin pack: rawIndex={}, asciiBytes={} (len={}, startPos={})",
            bmsPackIdx, bytesToHex(bmsMinPackBytes),
            bmsMinPackBytes.length, startPos);
    buffer.put(bmsMinPackBytes);

    // ---------- Finalize ----------
    final int length = buffer.position();
    buffer.flip();

    final byte[] data = new byte[length];
    buffer.get(data);

    LOG.debug("createBatteryInformation(): payload length = {}", length);
    LOG.debug("createBatteryInformation(): final payload hex = {}", bytesToHex(data));

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

// 0x63 – Get System charge/discharge control info (BINARY INFO, like emulator)
private byte[] createChargeDischargeIfno(final BatteryPack aggregatedPack) {
        
    if (USE_EMULATOR_63) {
        LOG.warn("0x63: Using EMULATOR payload.");
        return EMU_PAYLOAD_63;
    }

    
    
    
    // 4 shorts + 1 byte = 9 bytes
    final ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);

    LOG.debug("createChargeDischargeIfno(): START for aggregatedPack = {}", aggregatedPack);

    // ----- 1) Max system charge voltage -----
    // aggregatedPack.maxPackVoltageLimit appears to be in 0.1 V units (e.g. 573 => 57.3 V)
    double maxVoltRealV = aggregatedPack.maxPackVoltageLimit / 10.0;
    // Empirical trick you discovered: divide by 3 to get a HV-ish value the inverter likes
    double maxVoltScaledD = aggregatedPack.maxPackVoltageLimit / 3.0;
    short  maxVoltScaled  = (short) maxVoltScaledD;

    LOG.debug(
        "maxPackVoltageLimit: raw={} (0.1V units) -> approxReal={} V, encodedScaled={} (raw/3), encodedHex=0x{}",
        aggregatedPack.maxPackVoltageLimit,
        maxVoltRealV,
        maxVoltScaled,
        String.format("%04X", maxVoltScaled & 0xFFFF)
    );
    buffer.putShort(maxVoltScaled);

    // ----- 2) Min system discharge voltage -----
    double minVoltRealV = aggregatedPack.minPackVoltageLimit / 10.0;
    double minVoltScaledD = aggregatedPack.minPackVoltageLimit / 3.0;
    short  minVoltScaled  = (short) minVoltScaledD;

    LOG.debug(
        "minPackVoltageLimit: raw={} (0.1V units) -> approxReal={} V, encodedScaled={} (raw/3), encodedHex=0x{}",
        aggregatedPack.minPackVoltageLimit,
        minVoltRealV,
        minVoltScaled,
        String.format("%04X", minVoltScaled & 0xFFFF)
    );
    buffer.putShort(minVoltScaled);

    // ----- 3) Max charge current -----
    // aggregatedPack.maxPackChargeCurrent in A (or 0.1A depending on upstream).
    // Here we assume it is in amps and we encode in 0.1A units (×10).
    double maxChargeCurrentRealA = aggregatedPack.maxPackChargeCurrent;
    double maxChargeCurrentScaledD = maxChargeCurrentRealA * 10.0;
    short  maxChargeCurrentScaled  = (short) maxChargeCurrentScaledD;

    LOG.debug(
        "maxPackChargeCurrent: raw={} A -> encodedScaled={} (0.1A units), encodedHex=0x{}",
        aggregatedPack.maxPackChargeCurrent,
        maxChargeCurrentScaled,
        String.format("%04X", maxChargeCurrentScaled & 0xFFFF)
    );
    buffer.putShort(maxChargeCurrentScaled);

    // ----- 4) Max discharge current -----
    double maxDischargeCurrentRealA = aggregatedPack.maxPackDischargeCurrent;
    double maxDischargeCurrentScaledD = maxDischargeCurrentRealA * 10.0;
    short  maxDischargeCurrentScaled  = (short) maxDischargeCurrentScaledD;

    LOG.debug(
        "maxPackDischargeCurrent: raw={} A -> encodedScaled={} (0.1A units), encodedHex=0x{}",
        aggregatedPack.maxPackDischargeCurrent,
        maxDischargeCurrentScaled,
        String.format("%04X", maxDischargeCurrentScaled & 0xFFFF)
    );
    buffer.putShort(maxDischargeCurrentScaled);

    // ----- 5) MOSFET / force-charge flags -----
    LOG.debug(
        "charge/discharge MOS flags BEFORE bit-pack: chargeMOSState={}, dischargeMOSState={}, forceCharge={}",
        aggregatedPack.chargeMOSState,
        aggregatedPack.dischargeMOSState,
        aggregatedPack.forceCharge
    );

    byte flags = 0x00;
    flags = BitUtil.setBit(flags, 7, aggregatedPack.chargeMOSState);
    flags = BitUtil.setBit(flags, 6, aggregatedPack.dischargeMOSState);
    flags = BitUtil.setBit(flags, 5, aggregatedPack.forceCharge);

    LOG.debug(
        "chargeDischargeMOSStates: finalByte=0x{}, bits=[charge(bit7)={}, discharge(bit6)={}, force(bit5)={}]",
        String.format("%02X", flags),
        aggregatedPack.chargeMOSState,
        aggregatedPack.dischargeMOSState,
        aggregatedPack.forceCharge
    );
    buffer.put(flags);

    // ----- Finalize -----
    buffer.flip();
    final byte[] data = new byte[buffer.remaining()];
    buffer.get(data);

    LOG.debug("createChargeDischargeIfno(): payload length = {}", data.length);
    LOG.debug("createChargeDischargeIfno(): final payload hex = {}", bytesToHex(data));

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

/**
 * Helper to log byte arrays as hex.
 */
private static String bytesToHex(byte[] bytes) {
    if (bytes == null) {
        return "null";
    }
    StringBuilder sb = new StringBuilder(bytes.length * 3);
    for (int i = 0; i < bytes.length; i++) {
        sb.append(String.format("%02X", bytes[i]));
        if (i < bytes.length - 1) {
            sb.append(' ');
        }
    }
    return sb.toString();
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

// Wrapper: default is ASCII-encoded INFO
ByteBuffer prepareSendFrame(final byte address,
                            final byte cid1,
                            final byte cid2,
                            final byte[] data) {
    if (data == null) {
        LOG.error("prepareSendFrame(adr=0x{}, cid1=0x{}, cid2=0x{}): data is NULL",
                  String.format("%02X", address),
                  String.format("%02X", cid1),
                  String.format("%02X", cid2));
        return null;
    }

    // Call the real one
    return prepareSendFrame(address, cid1, cid2, data, false);
}

// Real implementation: can handle ASCII or BINARY INFO
ByteBuffer prepareSendFrame(final byte address,
                            final byte cid1,
                            final byte cid2,
                            final byte[] data,
                            final boolean binaryMode) {

    if (data == null) {
        LOG.error("prepareSendFrame(adr=0x{}, cid1=0x{}, cid2=0x{}): data is NULL",
                String.format("%02X", address),
                String.format("%02X", cid1),
                String.format("%02X", cid2));
        return null;
    }

    final int dataLen = data.length;
    final ByteBuffer sendFrame = ByteBuffer
            .allocate(18 + dataLen)
            .order(ByteOrder.BIG_ENDIAN);

    sendFrame.put((byte) 0x7E); // SOI
    sendFrame.put((byte) 0x32);
    sendFrame.put((byte) 0x30);

    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(address));
    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(cid1));
    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(cid2));

    sendFrame.put(createLengthCheckSum(dataLen * 2));

    if (dataLen > 0) {
        sendFrame.put(data);
    }

    //sendFrame.put(createChecksum(sendFrame, sendFrame.position()));
    sendFrame.put(createChecksum(sendFrame));
    sendFrame.put((byte) 0x0D);

    return sendFrame;
}


private byte[] createChecksum(final ByteBuffer frame) {
    final int len = frame.position();  // everything written so far

    int sum = 0;

    // Skip byte 0 (SOI)
    for (int i = 1; i < len; i++) {
        sum += (frame.get(i) & 0xFF);
    }

    // Two’s complement
    final int checksum = (~sum + 1) & 0xFFFF;

    // Convert to ASCII hex (4 bytes)
    byte[] out = new byte[4];
    byte[] hi = ByteAsciiConverter.convertByteToAsciiBytes((byte) ((checksum >> 8) & 0xFF));
    byte[] lo = ByteAsciiConverter.convertByteToAsciiBytes((byte) (checksum & 0xFF));

    out[0] = hi[0];
    out[1] = hi[1];
    out[2] = lo[0];
    out[3] = lo[1];

    return out;
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
