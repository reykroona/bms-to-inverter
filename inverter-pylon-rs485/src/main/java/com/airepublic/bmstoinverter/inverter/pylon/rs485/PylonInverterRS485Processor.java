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

final byte adr = (byte) 0x02;

// Warm-up frame: mimic the Python emulator's 0x63 reply
ByteBuffer warm = prepareSendFrame(
    adr,
    (byte) 0x46,            // CID1 = battery group, like Python
    (byte) 0x63,            // CID2 = 0x63 (used only for routing in our code, not encoded)
    createChargeDischargeIfno(aggregatedPack) // BINARY INFO (9 bytes)
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
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.maxPackVoltageLimit * .3)));
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.minPackVoltageLimit * .3))); // warning
        buffer.put(ByteAsciiConverter.convertShortToAsciiBytes((short) (pack.minPackVoltageLimit * .3))); // protect
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

// 0x61 – System analog / battery information (BINARY INFO, Daly-based)
private byte[] createBatteryInformation(final BatteryPack aggregatedPack) {

    if (USE_EMULATOR_61) {
        LOG.warn("0x61: Using EMULATOR payload.");
        return EMU_PAYLOAD_61;   // known-good template from emulator
    }

    LOG.debug("createBatteryInformation(): START for aggregatedPack = {}", aggregatedPack);

    final ByteBuffer buf = ByteBuffer
            .allocate(64) // slightly over; we'll trim to actual length
            .order(ByteOrder.BIG_ENDIAN);

    // Helper for Kelvin*10 encoding: T_raw = T_C*10 + 2731
    java.util.function.IntUnaryOperator kelvinX10 = cTenth -> {
        // cTenth is in 0.1°C units
        int raw = cTenth + 2731;
        if (raw > Short.MAX_VALUE) raw = Short.MAX_VALUE;
        if (raw < Short.MIN_VALUE) raw = Short.MIN_VALUE;
        return raw;
    };

    // --- Safe average temp (0.1°C) ---
    int tempAvgTenth = aggregatedPack.tempAverage;
    if (tempAvgTenth == 0) {
        tempAvgTenth = (aggregatedPack.tempMin + aggregatedPack.tempMax) / 2;
        LOG.debug("tempAverage was 0; approximated midpoint: {} (0.1°C) ≈ {} °C",
                  tempAvgTenth, tempAvgTenth / 10.0);
    }

    // =====================================================================
    // 1) Total average voltage (2 bytes, V * 1000)
    // aggregatedPack.packVoltage: 0.1 V units (e.g. 536 => 53.6 V)
    // =====================================================================
    double packV_real = aggregatedPack.packVoltage / 10.0;
    int    packV_mV   = (int) Math.round(packV_real * 1000.0);
    if (packV_mV < 0) packV_mV = 0;
    if (packV_mV > 0xFFFF) packV_mV = 0xFFFF;
    buf.putShort((short) packV_mV);
    LOG.debug("61: totalV = raw0.1V={} -> {} V -> {} mV (0x{})",
              aggregatedPack.packVoltage, packV_real, packV_mV,
              String.format("%04X", packV_mV));

    // =====================================================================
    // 2) Total current (2 bytes, 0.01 A, signed; + = charge)
    // packCurrent: 0.1 A units
    // =====================================================================
    double packI_real = aggregatedPack.packCurrent / 10.0;
    int    packI_0_01 = (int) Math.round(packI_real * 100.0); // 0.01A
    if (packI_0_01 < Short.MIN_VALUE) packI_0_01 = Short.MIN_VALUE;
    if (packI_0_01 > Short.MAX_VALUE) packI_0_01 = Short.MAX_VALUE;
    buf.putShort((short) packI_0_01);
    LOG.debug("61: totalI = raw0.1A={} -> {} A -> {} (0.01A, 0x{})",
              aggregatedPack.packCurrent, packI_real, packI_0_01,
              String.format("%04X", packI_0_01 & 0xFFFF));

    // =====================================================================
    // 3) SOC (1 byte, %)
    // packSOC: 0.1% units
    // =====================================================================
    int socPct = aggregatedPack.packSOC / 10;
    if (socPct < 0) socPct = 0;
    if (socPct > 100) socPct = 100;
    buf.put((byte) socPct);
    LOG.debug("61: SOC = raw0.1%={} -> {} %", aggregatedPack.packSOC, socPct);

    // =====================================================================
    // 4) Average cycles (2 bytes)
    // =====================================================================
    int avgCycles = aggregatedPack.bmsCycles;
    if (avgCycles < 0) avgCycles = 0;
    if (avgCycles > 0xFFFF) avgCycles = 0xFFFF;
    buf.putShort((short) avgCycles);
    LOG.debug("61: avgCycles = {}", avgCycles);

    // =====================================================================
    // 5) Max cycles (2 bytes) – use 10000 as in your ASCII version
    // =====================================================================
    int maxCycles = 10000;
    buf.putShort((short) maxCycles);
    LOG.debug("61: maxCycles = {}", maxCycles);

    // =====================================================================
    // 6) Avg SOH (1 byte, %), 7) Min SOH (1 byte, %)
    // packSOH: 0.1% units
    // =====================================================================
    int sohPct = aggregatedPack.packSOH / 10;
    if (sohPct < 1) sohPct = 1;
    if (sohPct > 100) sohPct = 100;
    byte sohAvg = (byte) sohPct;
    byte sohMin = (byte) sohPct; // if you have a separate min SOH, use that
    buf.put(sohAvg);
    buf.put(sohMin);
    LOG.debug("61: SOH avg/min = {} %", sohPct);

    // =====================================================================
    // 8) Highest cell voltage (2 bytes, mV)
    // =====================================================================
    int maxCellmV = aggregatedPack.maxCellmV;
    buf.putShort((short) maxCellmV);
    LOG.debug("61: maxCellV = {} mV ({:.3f} V)", maxCellmV, maxCellmV / 1000.0);

    // Determine which pack has that max cell
    int maxPackIdx = 0;
    int minPackIdx = 0;

    LOG.debug("61: searching packs for max/min cell mV: maxCellmV={}, minCellmV={}",
              aggregatedPack.maxCellmV, aggregatedPack.minCellmV);

    for (int i = 0; i < getEnergyStorage().getBatteryPacks().size(); i++) {
        final BatteryPack p = getEnergyStorage().getBatteryPack(i);
        if (p.maxCellmV == aggregatedPack.maxCellmV) {
            maxPackIdx = i;
        }
        if (p.minCellmV == aggregatedPack.minCellmV) {
            minPackIdx = i;
        }
    }

    // 9) Module index for max cell voltage (2 bytes)
    buf.putShort((short) maxPackIdx);

    // =====================================================================
    // 10) Lowest cell voltage (2 bytes, mV)
    // 11) Module index for min cell voltage (2 bytes)
    // =====================================================================
    int minCellmV = aggregatedPack.minCellmV;
    buf.putShort((short) minCellmV);
    buf.putShort((short) minPackIdx);
    LOG.debug("61: minCellV = {} mV (~{} V), maxPackIdx={}, minPackIdx={}",
              minCellmV, minCellmV / 1000.0, maxPackIdx, minPackIdx);

    // =====================================================================
    // 12–16) Cell temperature stats (Kelvin*10)
    // ---------------------------------------------------------------------
    // 12) avg cell temp (2)
    // 13) max cell temp (2)
    // 14) module idx for max cell temp (2)
    // 15) min cell temp (2)
    // 16) module idx for min cell temp (2)
    // =====================================================================
    int tAvgKx10 = kelvinX10.applyAsInt(tempAvgTenth);
    int tMaxKx10 = kelvinX10.applyAsInt(aggregatedPack.tempMax);
    int tMinKx10 = kelvinX10.applyAsInt(aggregatedPack.tempMin);
    short tMaxPack = (short) maxPackIdx;
    short tMinPack = (short) minPackIdx;

    buf.putShort((short) tAvgKx10);
    buf.putShort((short) tMaxKx10);
    buf.putShort(tMaxPack);
    buf.putShort((short) tMinKx10);
    buf.putShort(tMinPack);

    LOG.debug("61: temps cell avg={} (0.1°C)->{}, max={} -> {}, min={} -> {}",
              tempAvgTenth, tAvgKx10,
              aggregatedPack.tempMax, tMaxKx10,
              aggregatedPack.tempMin, tMinKx10);

    // =====================================================================
    // 17–21) MOSFET temperatures (same encoding)
    // 17) MOS avg, 18) MOS max, 19) pack idx max,
    // 20) MOS min, 21) pack idx min
    // =====================================================================
    // For now, copy cell temps – you can refine if you have specific MOS data.
    buf.putShort((short) tAvgKx10);
    buf.putShort((short) tMaxKx10);
    buf.putShort(tMaxPack);
    buf.putShort((short) tMinKx10);
    buf.putShort(tMinPack);

    // =====================================================================
    // 22–26) BMS temperatures (same encoding)
    // 22) BMS avg, 23) BMS max, 24) pack idx max,
    // 25) BMS min, 26) pack idx min
    // =====================================================================
    buf.putShort((short) tAvgKx10);
    buf.putShort((short) tMaxKx10);
    buf.putShort(tMaxPack);
    buf.putShort((short) tMinKx10);
    buf.putShort(tMinPack);

    // =====================================================================
    // Finalize
    // =====================================================================
    int length = buf.position();
    byte[] data = new byte[length];
    buf.flip();
    buf.get(data);

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


// ----- 1) Max system charge voltage (pack-level, mV) -----
final int cellsInSeries       = 14;     // your pack is 14s
final double chargePerCell_V  = 4.20;   // typical Li-ion charge limit
final double dischargePerCell_V = 3.00; // typical discharge lower limit

// Daly raw values (likely module-level, 0.1V units)
double maxVoltRealV_daly = aggregatedPack.maxPackVoltageLimit * 0.3;
double minVoltRealV_daly = aggregatedPack.minPackVoltageLimit * 0.3;

int chargeUpper_mV = (int) Math.round(chargePerCell_V * 1000.0 * cellsInSeries);   // e.g. 4.20 * 1000 * 14 = 58800
int dischargeLower_mV = (int) Math.round(dischargePerCell_V * 1000.0 * cellsInSeries); // 3.00 * 1000 * 14 = 44800

LOG.debug(
    "createChargeDischargeIfno(): Daly raw limits: " +
    "maxPackVoltageLimit={} (0.1V -> {} V), minPackVoltageLimit={} (0.1V -> {} V)",
    aggregatedPack.maxPackVoltageLimit,
    maxVoltRealV_daly,
    aggregatedPack.minPackVoltageLimit,
    minVoltRealV_daly
);

LOG.debug(
    "createChargeDischargeIfno(): Using fixed pack limits for 0x63: " +
    "cellsInSeries={} => chargeUpper={} V ({} mV, 0x{}), dischargeLower={} V ({} mV, 0x{})",
    cellsInSeries,
    String.format("%.2f", chargePerCell_V * cellsInSeries),
    chargeUpper_mV,
    String.format("%04X", chargeUpper_mV & 0xFFFF),
    String.format("%.2f", dischargePerCell_V * cellsInSeries),
    dischargeLower_mV,
    String.format("%04X", dischargeLower_mV & 0xFFFF)
);

// Put as shorts (big-endian) in mV, like the emulator
buffer.putShort((short) chargeUpper_mV);
buffer.putShort((short) dischargeLower_mV);

// ----- 3) Max charge current -----
double maxChargeCurrentRealA       = aggregatedPack.maxPackChargeCurrent;
double maxChargeCurrentScaledD     = maxChargeCurrentRealA * 10.0; // 0.1A units
short  maxChargeCurrentScaled      = (short) maxChargeCurrentScaledD;

LOG.debug(
    "maxPackChargeCurrent: raw={} A -> encodedScaled={} (0.1A units), encodedHex=0x{}",
    aggregatedPack.maxPackChargeCurrent,
    maxChargeCurrentScaled,
    String.format("%04X", maxChargeCurrentScaled & 0xFFFF)
);
buffer.putShort(maxChargeCurrentScaled);

// ----- 4) Max discharge current -----
double maxDischargeCurrentRealA    = aggregatedPack.maxPackDischargeCurrent;
double maxDischargeCurrentScaledD  = maxDischargeCurrentRealA * 10.0; // 0.1A units
short  maxDischargeCurrentScaled   = (short) maxDischargeCurrentScaledD;

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

public static byte[] hexStringToByteArray(String s) {
    if (s == null) {
        return null;
    }

    // Remove spaces or 0x prefixes
    s = s.replaceAll("[^0-9A-Fa-f]", "");

    // If odd number of characters, prepend a zero
    if (s.length() % 2 != 0) {
        s = "0" + s;
    }

    int len = s.length();
    byte[] data = new byte[len / 2];

    for (int i = 0; i < len; i += 2) {
        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i+1), 16));
    }

    return data;
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

// Wrapper: ignore binaryMode now; data is always BINARY INFO
ByteBuffer prepareSendFrame(final byte address,
                            final byte cid1,
                            final byte cid2,
                            final byte[] data) {
    return prepareSendFrame(address, cid1, cid2, data, false);
}


ByteBuffer prepareSendFrame(final byte address,
                            final byte cid1,
                            final byte cid2,   // not encoded; used only for logging/routing
                            final byte[] data,
                            final boolean binaryMode /*ignored*/) {

    if (data == null) {
        LOG.error("prepareSendFrame(adr=0x{}, cid1=0x{}, cid2=0x{}): data is NULL",
                  String.format("%02X", address),
                  String.format("%02X", cid1),
                  String.format("%02X", cid2));
        return null;
    }

    // INFO is BINARY; we will encode it as ASCII hex in the frame.
    final int infoLen       = data.length;      // number of binary bytes
    final int asciiInfoLen  = infoLen * 2;      // number of ASCII bytes in INFO
    final byte ver          = 0x20;             // protocol 2.0, as in Python

    // LENGTH field is computed over ASCII INFO length (bytes)
    final byte[] lengthAscii = createLengthCheckSum(asciiInfoLen); // returns 4 ASCII bytes

    // Frame structure (all ASCII except SOI/EOI):
    //  SOI         : 1
    //  VER         : 2 ("20")
    //  ADR         : 2
    //  CID1        : 2
    //  RTN=00      : 2
    //  LENGTH      : 4 (LEN_H, LEN_L as ASCII)
    //  INFO        : asciiInfoLen
    //  CHKSUM      : 4 ASCII
    //  EOI         : 1
    final int frameCapacity = 1 + 2 + 2 + 2 + 2 + 4 + asciiInfoLen + 4 + 1;

    final ByteBuffer sendFrame = ByteBuffer
            .allocate(frameCapacity)
            .order(ByteOrder.BIG_ENDIAN);

    // SOI
    sendFrame.put((byte) 0x7E);     // '~'

    // VER = 0x20 -> "20"
    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(ver));

    // ADR
    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(address));

    // CID1 (for Pylon LV this is always 0x46)
    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(cid1));

    // RTN = 0x00 (OK) – this is the 4th field in the header, matching Python
    sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes((byte) 0x00));

    // LENGTH (already ASCII from createLengthCheckSum)
    sendFrame.put(lengthAscii);

    // INFO: each binary byte as 2 ASCII hex chars
    for (byte b : data) {
        sendFrame.put(ByteAsciiConverter.convertByteToAsciiBytes(b));
    }

    // CHKSUM over all bytes from VER (index 1) through last INFO byte
    sendFrame.put(createChecksum(sendFrame));

    // EOI
    sendFrame.put((byte) 0x0D);

    sendFrame.flip();
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
