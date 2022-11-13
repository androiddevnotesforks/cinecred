package com.loadingbyte.cinecred.ui.styling

import com.loadingbyte.cinecred.common.FPS
import com.loadingbyte.cinecred.common.Severity
import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.project.*
import com.loadingbyte.cinecred.ui.helper.*
import kotlinx.collections.immutable.ImmutableList
import java.awt.Color
import java.util.*
import javax.swing.Icon
import javax.swing.SpinnerNumberModel


class StyleForm<S : Style>(
    private val styleClass: Class<S>,
    insets: Boolean = true
) : Form.Storable<S>(insets) {

    private val valueWidgets = LinkedHashMap<StyleSetting<S, *>, Widget<*>>() // must retain order
    private val rootFormRows = HashMap<StyleSetting<S, *>, FormRow>()

    private var disableOnChange = false

    init {
        val widgetSpecs = getStyleWidgetSpecs(styleClass)
        val unionSpecs = widgetSpecs.filterIsInstance<UnionWidgetSpec<S>>()

        for (setting in getStyleSettings(styleClass)) {
            val unionSpec = unionSpecs.find { setting in it.settings }
            if (unionSpec == null || unionSpec.settings.first() == setting) {
                if (widgetSpecs.any { setting in it.settings && it is NewSectionWidgetSpec<S> })
                    addSeparator()
                if (unionSpec == null)
                    addSingleSettingWidget(setting)
                else
                    addSettingUnionWidget(unionSpec)
            }
        }
    }

    private fun addSingleSettingWidget(setting: StyleSetting<S, *>) {
        val valueWidget = makeSettingWidget(setting)
        val formRow = makeFormRow(setting.name, valueWidget)
        valueWidgets[setting] = valueWidget
        rootFormRows[setting] = formRow
        addFormRow(formRow)
    }

    private fun addSettingUnionWidget(spec: UnionWidgetSpec<S>) {
        val wrappedWidgets = mutableListOf<Widget<*>>()
        for (setting in spec.settings) {
            val valueWidget = makeSettingWidget(setting)
            wrappedWidgets.add(valueWidget)
            valueWidgets[setting] = valueWidget
        }
        val unionWidget = UnionWidget(wrappedWidgets, spec.settingIcons)
        val formRow = makeFormRow(spec.unionName, unionWidget)
        for (setting in spec.settings)
            rootFormRows[setting] = formRow
        addFormRow(formRow)
    }

    private fun makeSettingWidget(setting: StyleSetting<S, *>): Widget<*> {
        val settingConstraints = getStyleConstraints(styleClass).filter { c -> setting in c.settings }
        val settingWidgetSpecs = getStyleWidgetSpecs(styleClass).filter { s -> setting in s.settings }

        return when (setting) {
            is DirectStyleSetting ->
                makeBackingSettingWidget(setting, settingConstraints, settingWidgetSpecs)
            is OptStyleSetting ->
                OptWidget(makeBackingSettingWidget(setting, settingConstraints, settingWidgetSpecs))
            is ListStyleSetting ->
                makeListWidget(setting, settingConstraints, settingWidgetSpecs)
        }
    }

    private fun <E : Any /* non-null */> makeListWidget(
        setting: ListStyleSetting<S, E>,
        settingConstraints: List<StyleConstraint<S, *>>,
        settingWidgetSpecs: List<StyleWidgetSpec<S>>
    ): Widget<ImmutableList<E>> {
        val dynChoiceConstr = settingConstraints.oneOf<DynChoiceConstr<S, *>>()
        val minSizeConstr = settingConstraints.oneOf<MinSizeConstr<S>>()
        val widthWidgetSpec = settingWidgetSpecs.oneOf<WidthWidgetSpec<S>>()
        val listWidgetSpec = settingWidgetSpecs.oneOf<ListWidgetSpec<S, E>>()
        val choiceWidgetSpec = settingWidgetSpecs.oneOf<ChoiceWidgetSpec<S>>()

        val widthSpec = widthWidgetSpec?.widthSpec

        if (setting.type == String::class.java) {
            val widget = when {
                dynChoiceConstr != null -> MultiComboBoxWidget(
                    emptyList(), naturalOrder(), widthSpec = widthSpec, inconsistent = true,
                    noItemsMessage = choiceWidgetSpec?.getNoItemsMsg?.invoke()
                )
                else -> TextListWidget(widthSpec)
            }
            @Suppress("UNCHECKED_CAST")
            return widget as Widget<ImmutableList<E>>
        }

        return ListWidget(
            newElemWidget = { makeBackingSettingWidget(setting, settingConstraints, settingWidgetSpecs) },
            listWidgetSpec?.newElem, listWidgetSpec?.newElemIsLastElem ?: false,
            listWidgetSpec?.elemsPerRow ?: 1, listWidgetSpec?.rowSeparators ?: false,
            minSizeConstr?.minSize ?: 0
        )
    }

    private fun <V : Any /* non-null */> makeBackingSettingWidget(
        setting: StyleSetting<S, V>,
        settingConstraints: List<StyleConstraint<S, *>>,
        settingWidgetSpecs: List<StyleWidgetSpec<S>>
    ): Widget<V> {
        val intConstr = settingConstraints.oneOf<IntConstr<S>>()
        val floatConstr = settingConstraints.oneOf<FloatConstr<S>>()
        val dynChoiceConstr = settingConstraints.oneOf<DynChoiceConstr<S, V>>()
        val colorConstr = settingConstraints.oneOf<ColorConstr<S>>()
        val fontNameConstr = settingConstraints.oneOf<FontNameConstr<S>>()
        val widthWidgetSpec = settingWidgetSpecs.oneOf<WidthWidgetSpec<S>>()
        val numberWidgetSpec = settingWidgetSpecs.oneOf<NumberWidgetSpec<S>>()
        val toggleButtonGroupWidgetSpec = settingWidgetSpecs.oneOf<ToggleButtonGroupWidgetSpec<S, *>>()
        val timecodeWidgetSpec = settingWidgetSpecs.oneOf<TimecodeWidgetSpec<S>>()

        val widthSpec = widthWidgetSpec?.widthSpec

        val widget = when (setting.type) {
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> {
                val min = intConstr?.min
                val max = intConstr?.max
                val step = numberWidgetSpec?.step ?: 1
                val model = SpinnerNumberModel(min ?: max ?: 0, min, max, step)
                if (timecodeWidgetSpec != null)
                    TimecodeWidget(model, FPS(1, 1), TimecodeFormat.values()[0], widthSpec)
                else
                    SpinnerWidget(Int::class.javaObjectType, model, widthSpec)
            }
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> {
                val min = floatConstr?.let { if (it.minInclusive) it.min else it.min?.plus(0.01f) }
                val max = floatConstr?.let { if (it.maxInclusive) it.max else it.max?.minus(0.01f) }
                val step = numberWidgetSpec?.step ?: 1f
                val model = SpinnerNumberModel(min ?: max ?: 0f, min, max, step)
                SpinnerWidget(Float::class.javaObjectType, model, widthSpec)
            }
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> CheckBoxWidget()
            String::class.java -> when {
                dynChoiceConstr != null -> InconsistentComboBoxWidget(
                    String::class.java, emptyList(), widthSpec = widthSpec
                )
                fontNameConstr != null -> FontChooserWidget(widthSpec)
                else -> TextWidget(widthSpec)
            }
            Locale::class.java -> InconsistentComboBoxWidget(
                Locale::class.java, Locale.getAvailableLocales().sortedBy(Locale::getDisplayName),
                toString = Locale::getDisplayName, widthSpec
            )
            Color::class.java -> ColorWellWidget(allowAlpha = colorConstr?.allowAlpha ?: true, widthSpec)
            FPS::class.java -> FPSWidget(widthSpec)
            FontFeature::class.java -> FontFeatureWidget()
            else -> when {
                Enum::class.java.isAssignableFrom(setting.type) -> when {
                    toggleButtonGroupWidgetSpec != null -> makeEnumToggleButtonGroupWidget(
                        setting.type.asSubclass(Enum::class.java), toggleButtonGroupWidgetSpec.show,
                        custIcons = toggleButtonGroupWidgetSpec.getIcon != null, inconsistent = dynChoiceConstr != null
                    )
                    dynChoiceConstr != null -> InconsistentComboBoxWidget(
                        setting.type, emptyList(), toString = { l10nEnum(it as Enum<*>) }, widthSpec
                    )
                    else -> ComboBoxWidget(
                        setting.type, setting.type.enumConstants.asList(), toString = { l10nEnum(it as Enum<*>) },
                        widthSpec
                    )
                }
                Style::class.java.isAssignableFrom(setting.type) ->
                    NestedFormWidget(StyleForm(setting.type.asSubclass(Style::class.java), insets = false))
                else -> throw UnsupportedOperationException("UI unsupported for objects of type ${setting.type.name}.")
            }
        }

        @Suppress("UNCHECKED_CAST")
        return widget as Widget<V>
    }

    private fun <V : Enum<*>> makeEnumToggleButtonGroupWidget(
        enumClass: Class<V>,
        show: ToggleButtonGroupWidgetSpec.Show,
        custIcons: Boolean,
        inconsistent: Boolean
    ): Widget<V> {
        // If a custom getIcon function is supplied, it will be applied later on via setToIconFun().
        var toIcon: ((V) -> Icon)? = if (custIcons) null else Enum<*>::icon
        var toLabel: ((V) -> String)? = ::l10nEnum
        var toTooltip: ((V) -> String)? = toLabel
        // @formatter:off
        when (show) {
            ToggleButtonGroupWidgetSpec.Show.LABEL -> { toIcon = null; toTooltip = null }
            ToggleButtonGroupWidgetSpec.Show.ICON -> toLabel = null
            ToggleButtonGroupWidgetSpec.Show.ICON_AND_LABEL -> toTooltip = null
        }
        // @formatter:on

        val items = if (inconsistent) emptyList() else enumClass.enumConstants.asList()
        return ToggleButtonGroupWidget(items, toIcon, toLabel, toTooltip, inconsistent)
    }

    private fun makeFormRow(name: String, widget: Widget<*>): FormRow {
        val l10nKey = "ui.styling." +
                styleClass.simpleName.removeSuffix("Style").replaceFirstChar(Char::lowercase) +
                ".$name"
        val formRow = FormRow(l10n(l10nKey), widget)
        try {
            formRow.notice = Notice(Severity.INFO, l10n("$l10nKey.desc"))
        } catch (_: MissingResourceException) {
        }
        return formRow
    }

    fun <T : Style> castToStyle(styleClass: Class<T>): StyleForm<T> {
        if (styleClass == this.styleClass)
            @Suppress("UNCHECKED_CAST")
            return this as StyleForm<T>
        throw ClassCastException("Cannot cast StyleForm<${this.styleClass.name}> to StyleForm<${styleClass.name}>")
    }

    override fun open(stored /* style */: S) {
        disableOnChange = true
        try {
            for ((setting, widget) in valueWidgets)
                @Suppress("UNCHECKED_CAST")
                (widget as Widget<Any>).value = setting.get(stored)
        } finally {
            disableOnChange = false
        }
    }

    override fun save(): S =
        newStyle(styleClass, valueWidgets.map { (_, widget) -> widget.value })

    fun getNestedFormsAndStyles(style: S): List<Pair<StyleForm<*>, Style>> {
        val nested = mutableListOf<Pair<StyleForm<*>, Style>>()
        for (setting in getStyleSettings(styleClass))
            if (Style::class.java.isAssignableFrom(setting.type))
                when (setting) {
                    is DirectStyleSetting -> {
                        val formWidget = valueWidgets[setting] as NestedFormWidget
                        nested.add(Pair(formWidget.form as StyleForm, setting.get(style) as Style))
                    }
                    is OptStyleSetting -> {
                        val formWidget = (valueWidgets[setting] as OptWidget<*>).wrapped as NestedFormWidget
                        nested.add(Pair(formWidget.form as StyleForm, setting.get(style).value as Style))
                    }
                    is ListStyleSetting -> {
                        val formWidgets = (valueWidgets[setting] as ListWidget<*>).elementWidgets
                        for ((formWidget, nestedStyle) in formWidgets.zip(setting.get(style)))
                            nested.add(Pair((formWidget as NestedFormWidget).form as StyleForm, nestedStyle as Style))
                    }
                }
        return nested
    }

    fun setIneffectiveSettings(ineffectiveSettings: Map<StyleSetting<S, *>, Effectivity>) {
        for ((setting, formRow) in rootFormRows) {
            val effectivity = ineffectiveSettings.getOrDefault(setting, Effectivity.EFFECTIVE)
            formRow.isVisible = effectivity >= Effectivity.ALMOST_EFFECTIVE
            formRow.isEnabled = effectivity == Effectivity.EFFECTIVE
        }
    }

    fun clearIssues() {
        for (formRow in rootFormRows.values) {
            formRow.noticeOverride = null
            formRow.widget.applySeverity(-1, null)
        }
    }

    fun showIssueIfMoreSevere(setting: StyleSetting<*, *>, subjectIndex: Int, issue: Notice) {
        val formRow = rootFormRows[setting]!!
        val prevNoticeOverride = formRow.noticeOverride
        // Only show the notice message if there isn't already a notice with the same or a higher severity.
        if (prevNoticeOverride == null || issue.severity > prevNoticeOverride.severity) {
            formRow.noticeOverride = issue
            valueWidgets[setting]!!.applySeverity(subjectIndex, issue.severity)
        }
    }

    fun setProjectFontFamilies(projectFamilies: FontFamilies) {
        val configurator = { w: Widget<*> -> if (w is FontChooserWidget) w.projectFamilies = projectFamilies }
        for (widget in valueWidgets.values)
            widget.applyConfigurator(configurator)
    }

    fun setChoices(setting: StyleSetting<S, *>, choices: List<Any>, unique: Boolean = false) {
        val remaining = if (unique) choices.toMutableList() else choices
        valueWidgets.getValue(setting).applyConfigurator { widget ->
            if (widget is Choice<*>) {
                @Suppress("UNCHECKED_CAST")
                (widget as Choice<Any>).updateChoices(remaining)
                if (unique)
                    (remaining as MutableList).remove(widget.value)
            }
        }
    }

    fun setToIconFun(setting: StyleSetting<S, *>, toIcon: ((Nothing) -> Icon)?) {
        valueWidgets.getValue(setting).applyConfigurator { widget ->
            if (widget is ToggleButtonGroupWidget)
                @Suppress("UNCHECKED_CAST")
                (widget as ToggleButtonGroupWidget<Nothing>).toIcon = toIcon
        }
    }

    fun setTimecodeFPSAndFormat(setting: StyleSetting<S, *>, fps: FPS, timecodeFormat: TimecodeFormat) {
        valueWidgets.getValue(setting).applyConfigurator { widget ->
            if (widget is TimecodeWidget) {
                widget.fps = fps
                widget.timecodeFormat = timecodeFormat
            }
        }
    }

    override fun onChange(widget: Widget<*>) {
        if (!disableOnChange)
            super.onChange(widget)
    }


    companion object {

        private inline fun <reified T : Any> Iterable<*>.oneOf(): T? {
            var found: T? = null
            for (elem in this)
                if (elem is T)
                    if (found != null)
                        throw IllegalArgumentException("Collection has more than one element.")
                    else
                        found = elem
            return found
        }

        private fun l10nEnum(enumElem: Enum<*>) =
            l10n("project.${enumElem.javaClass.simpleName}.${enumElem.name}")

    }

}
