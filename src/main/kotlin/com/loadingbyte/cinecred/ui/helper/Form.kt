package com.loadingbyte.cinecred.ui.helper

import com.loadingbyte.cinecred.common.Severity
import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.*


open class Form : JPanel(MigLayout("hidemode 3", "[align right][grow]")) {

    class VerifyResult(val severity: Severity, msg: String) : Exception(msg)

    private class FormRow(val isVisibleFunc: (() -> Boolean)?) {
        val components = mutableListOf<JComponent>()
        var doVerify: (() -> Unit)? = null

        // We keep track of the form rows which are visible and have a verification error (not a warning).
        // One can use this information to determine whether the form is error-free.
        var isVisible = true
            set(value) {
                field = value
                for (comp in components)
                    comp.isVisible = value
            }
        var isErroneous = false
    }

    private val formRows = mutableListOf<FormRow>()
    private val componentToFormRow = mutableMapOf<Component, FormRow>()
    private var submitButton: JButton? = null
    private var isSuspendingChangeEvents = true

    var changeListener: ((Component) -> Unit)? = null

    fun addFormRow(
        label: String,
        fields: List<JComponent>,
        constraints: List<String>,
        isVisible: (() -> Boolean)? = null,
        verify: (() -> Unit)? = null
    ) {
        require(fields.size == constraints.size)

        val formRow = FormRow(isVisible)

        val jLabel = JLabel(label)
        formRow.components.add(jLabel)
        add(jLabel, "newline")

        formRow.components.addAll(fields)
        val endlineGroupId = "g" + System.identityHashCode(jLabel)
        val endlineFieldIds = mutableListOf<String>()
        for ((fieldIdx, field) in fields.withIndex()) {
            val fieldConstraints = mutableListOf(constraints[fieldIdx])
            // If the field ends a line, assign it a unique ID. For this, we just use its location in memory. Also add
            // it to the "endlineGroup". These IDs will be used later when positioning the verification components.
            if (fieldIdx == fields.lastIndex || "newline" in constraints.getOrElse(fieldIdx + 1) { "" }) {
                val id = "f" + System.identityHashCode(field).toString()
                fieldConstraints.add("id $endlineGroupId.$id")
                endlineFieldIds.add(id)
            }
            // If this field starts a new line, add a skip constraint to skip the label column.
            if ("newline" in fieldConstraints[0])
                fieldConstraints.add("skip 1")
            add(field, fieldConstraints.joinToString())
        }

        if (verify != null) {
            val verifyIconLabel = JLabel()
            val verifyMsgArea = newLabelTextArea()
            formRow.components.addAll(arrayOf(verifyIconLabel, verifyMsgArea))

            // Position the verification components using coordinates relative to the fields that are at the line ends.
            val iconLabelId = "c${System.identityHashCode(verifyIconLabel)}"
            val startYExpr = "${endlineFieldIds[0]}.y"
            add(verifyIconLabel, "id $iconLabelId, pos ($endlineGroupId.x2 + 3*rel) ($startYExpr + 3)")
            add(verifyMsgArea, "pos $iconLabelId.x2 $startYExpr visual.x2 null")

            formRow.doVerify = {
                formRow.isErroneous = false
                // Remove FlatLaf outlines.
                for (comp in formRow.components)
                    comp.putClientProperty("JComponent.outline", null)
                try {
                    verify()
                    verifyIconLabel.icon = null
                    verifyMsgArea.text = null
                } catch (e: VerifyResult) {
                    verifyIconLabel.icon = SEVERITY_ICON[e.severity]
                    verifyMsgArea.text = e.message
                    if (e.severity == Severity.WARN || e.severity == Severity.ERROR) {
                        // Add FlatLaf outlines.
                        val outline = if (e.severity == Severity.WARN) "warning" else "error"
                        for (comp in formRow.components)
                            comp.putClientProperty("JComponent.outline", outline)
                    }
                    if (e.severity == Severity.ERROR)
                        formRow.isErroneous = true
                }
            }
        }

        formRows.add(formRow)
        for (comp in formRow.components)
            componentToFormRow[comp] = formRow
    }

    fun addSeparator() {
        add(JSeparator(), "newline, span, growx")
    }

    fun addSubmitButton(label: String) = JButton(label).also { button ->
        submitButton = button
        add(button, "newline, skip 1, span, align left")
    }

    fun onChange(component: Component) {
        if (!isSuspendingChangeEvents) {
            updateVerifyAndVisible()
            // Notify the change listener, if it is set.
            changeListener?.invoke(component)
        }
    }

    protected fun finishInit() {
        isSuspendingChangeEvents = false
        updateVerifyAndVisible()
    }

    fun withSuspendedChangeEvents(block: () -> Unit) {
        isSuspendingChangeEvents = true
        block()
        isSuspendingChangeEvents = false
        updateVerifyAndVisible()
    }

    private fun updateVerifyAndVisible() {
        for (formRow in formRows) {
            formRow.doVerify?.invoke()
            formRow.isVisible = formRow.isVisibleFunc?.invoke() ?: true
        }
        submitButton?.isEnabled = isErrorFree
    }

    val isErrorFree: Boolean
        get() = formRows.all { !it.isVisible || !it.isErroneous }

}
