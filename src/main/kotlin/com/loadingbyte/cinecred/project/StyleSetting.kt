package com.loadingbyte.cinecred.project

import kotlinx.collections.immutable.ImmutableList
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


private val settingsCache = HashMap<Class<*>, List<StyleSetting<*, *>>>()

fun <S : Style> getStyleSettings(styleClass: Class<S>): List<StyleSetting<S, *>> {
    val cached = settingsCache[styleClass]
    return if (cached == null)
        styleClass.declaredFields
            .map { field ->
                when {
                    Opt::class.java == field.type ->
                        ReflectedOptStyleSetting(styleClass, field.name)
                    ImmutableList::class.java.isAssignableFrom(field.type) ->
                        ReflectedListStyleSetting(styleClass, field.name)
                    else ->
                        ReflectedDirectStyleSetting(styleClass, field.name)
                }
            }
            .also { settingsCache[styleClass] = it }
    else
        @Suppress("UNCHECKED_CAST")
        cached as List<StyleSetting<S, *>>
}


fun <S : Style, SUBJ : Any> KProperty1<S, SUBJ>.st(): DirectStyleSetting<S, SUBJ> =
    KProperty1DirectStyleSetting(this)

fun <S : Style, SUBJ : Any> KProperty1<S, Opt<SUBJ>>.st(): OptStyleSetting<S, SUBJ> =
    KProperty1OptStyleSetting(this)

fun <S : Style, SUBJ : Any> KProperty1<S, ImmutableList<SUBJ>>.st(): ListStyleSetting<S, SUBJ> =
    KProperty1ListStyleSetting(this)


fun <S : Style> newStyle(styleClass: Class<S>, settingValues: List<*>): S =
    styleClass
        .getDeclaredConstructor(*styleClass.declaredFields.map(Field::getType).toTypedArray())
        .newInstance(*settingValues.toTypedArray())


sealed class StyleSetting<S : Style, SUBJ : Any>(styleClass: Class<S>, val name: String, isNested: Boolean) {

    /** The upmost class/interface in the hierarchy which first defines the setting. */
    val declaringClass: Class<*> = findDeclaringClass(styleClass)!!

    val type: Class<SUBJ>

    init {
        var baseType = styleClass.getGetter(name).genericReturnType
        if (isNested)
            baseType = (baseType as ParameterizedType).actualTypeArguments[0]
        @Suppress("UNCHECKED_CAST")
        type = (if (baseType is ParameterizedType) baseType.rawType else baseType) as Class<SUBJ>
    }

    private fun findDeclaringClass(curClass: Class<*>): Class<*>? {
        for (inter in curClass.interfaces)
            findDeclaringClass(inter)?.let { return it }
        return try {
            curClass.getGetter(name)
            curClass
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    abstract fun get(style: S): Any
    abstract fun extractSubjects(style: S): List<SUBJ>

    override fun equals(other: Any?) =
        this === other || other is StyleSetting<*, *> && declaringClass == other.declaringClass && name == other.name

    override fun hashCode(): Int {
        var result = declaringClass.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString() =
        "StyleSetting(${declaringClass.simpleName}.$name: ${type.simpleName})"

}


abstract class DirectStyleSetting<S : Style, SUBJ : Any>(styleClass: Class<S>, name: String) :
    StyleSetting<S, SUBJ>(styleClass, name, isNested = false) {
    abstract override fun get(style: S): SUBJ
    override fun extractSubjects(style: S): List<SUBJ> = listOf(get(style))
}


abstract class OptStyleSetting<S : Style, SUBJ : Any>(styleClass: Class<S>, name: String) :
    StyleSetting<S, SUBJ>(styleClass, name, isNested = true) {
    abstract override fun get(style: S): Opt<SUBJ>
    override fun extractSubjects(style: S): List<SUBJ> = get(style).run { if (isActive) listOf(value) else emptyList() }
}


abstract class ListStyleSetting<S : Style, SUBJ : Any>(styleClass: Class<S>, name: String) :
    StyleSetting<S, SUBJ>(styleClass, name, isNested = true) {
    abstract override fun get(style: S): ImmutableList<SUBJ>
    override fun extractSubjects(style: S): List<SUBJ> = get(style)
}


private class ReflectedDirectStyleSetting<S : Style>(styleClass: Class<S>, name: String) :
    DirectStyleSetting<S, Any>(styleClass, name) {
    private val getter = styleClass.getGetter(name)
    override fun get(style: S): Any = getter.invoke(style)
}


private class ReflectedOptStyleSetting<S : Style>(styleClass: Class<S>, name: String) :
    OptStyleSetting<S, Any>(styleClass, name) {
    private val getter = styleClass.getGetter(name)
    override fun get(style: S): Opt<Any> = getter.invoke(style) as Opt<Any>
}


private class ReflectedListStyleSetting<S : Style>(styleClass: Class<S>, name: String) :
    ListStyleSetting<S, Any>(styleClass, name) {
    private val getter = styleClass.getGetter(name)
    override fun get(style: S): ImmutableList<Any> =
        (getter.invoke(style) as List<*>).requireNoNulls() as ImmutableList<Any>
}


private class KProperty1DirectStyleSetting<S : Style, SUBJ : Any>(private val kProp: KProperty1<S, SUBJ>) :
    DirectStyleSetting<S, SUBJ>(kProp.getOwnerClass(), kProp.name) {
    override fun get(style: S): SUBJ = kProp.get(style)
}


private class KProperty1OptStyleSetting<S : Style, SUBJ : Any>(private val kProp: KProperty1<S, Opt<SUBJ>>) :
    OptStyleSetting<S, SUBJ>(kProp.getOwnerClass(), kProp.name) {
    override fun get(style: S): Opt<SUBJ> = kProp.get(style)
}


private class KProperty1ListStyleSetting<S : Style, SUBJ : Any>(private val kProp: KProperty1<S, ImmutableList<SUBJ>>) :
    ListStyleSetting<S, SUBJ>(kProp.getOwnerClass(), kProp.name) {
    override fun get(style: S): ImmutableList<SUBJ> = kProp.get(style)
}


private fun Class<*>.getGetter(fieldName: String) =
    getDeclaredMethod("get" + fieldName.replaceFirstChar(Char::uppercase))


@Suppress("UNCHECKED_CAST")
private fun <T> KProperty1<T, *>.getOwnerClass() =
    (javaClass.getMethod("getOwner").invoke(this) as KClass<*>).java as Class<T>
