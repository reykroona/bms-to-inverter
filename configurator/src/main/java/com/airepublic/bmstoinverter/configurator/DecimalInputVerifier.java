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
package com.airepublic.bmstoinverter.configurator;

import java.util.regex.Pattern;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JTextField;

/**
 * Verifies numeric input allowing integer and decimal values.
 */
public class DecimalInputVerifier extends InputVerifier {
    private final Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");

    @Override
    public boolean verify(final JComponent input) {
        return verify(((JTextField) input).getText());
    }

    public boolean verify(final String text) {
        return pattern.matcher(text).matches();
    }
}
