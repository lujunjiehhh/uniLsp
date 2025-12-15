package com.frenchef.intellijlsp.crosslang;

/**
 * Java utility class for cross-language call hierarchy testing.
 */
public class JavaUtility {

    /**
     * Format string - will be called from Kotlin.
     */
    public String formatString(String input) {
        return input.toUpperCase().trim();
    }

    /**
     * Process with Kotlin service - calls Kotlin method.
     */
    public String processWithKotlin(String input) {
        KotlinService service = new KotlinService();
        return service.processData(input);
    }

    /**
     * Multiple calls to Kotlin.
     */
    public void multipleKotlinCalls() {
        KotlinService service = new KotlinService();
        service.processData("test1");
        service.transformData("test2");
    }
}
