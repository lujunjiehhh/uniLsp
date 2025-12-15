package com.frenchef.intellijlsp.crosslang

/** Kotlin service class for cross-language call hierarchy testing. */
class KotlinService {

    /** Process data - will be called from Java. */
    fun processData(input: String): String {
        return "Processed: $input"
    }

    /** Validate input - internal Kotlin method. */
    private fun validateInput(input: String): Boolean {
        return input.isNotEmpty()
    }

    /** Transform data - calls Java utility. */
    fun transformData(input: String): String {
        // This will call Java method
        val javaUtil = JavaUtility()
        return javaUtil.formatString(input)
    }
}
