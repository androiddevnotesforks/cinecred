package com.loadingbyte.cinecred.ui.styling

import java.awt.Component
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.*


/**
 * A tree used for managing the global settings and various style lists. Note that this class is pretty generic and
 * as such doesn't directly know about any of that stuff.
 */
class StylingTree : JTree(DefaultTreeModel(DefaultMutableTreeNode(), true)) {

    var onDeselect: (() -> Unit)? = null

    private val model = treeModel as DefaultTreeModel
    private val rootNode = model.root as DefaultMutableTreeNode

    private val singletonTypeInfos = HashMap<Class<*>, TypeInfo.Singleton>()
    private val listTypeInfos = HashMap<Class<*>, TypeInfo.List>()

    private var disableSelectionListener = false

    init {
        isRootVisible = false
        setShowsRootHandles(false)
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        // The page and content style "folders" must not be collapsed by the user.
        addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {}
            override fun treeWillCollapse(event: TreeExpansionEvent) {
                throw ExpandVetoException(event)
            }
        })

        // Each node gets an icon depending on which type it is part of.
        setCellRenderer(object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree, value: Any, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ): Component {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                val userObj = (value as DefaultMutableTreeNode).userObject
                if (userObj is StoredObj)
                    icon = userObj.typeInfo.icon
                return this
            }
        })

        // When the user selects a node that stores an object, notify the callback.
        addTreeSelectionListener {
            if (disableSelectionListener)
                return@addTreeSelectionListener
            triggerOnSelectOrOnDeselect()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> addSingletonType(initial: T, label: String, icon: Icon, onSelect: (T) -> Unit) {
        val node = DefaultMutableTreeNode(null, false)
        model.insertNodeInto(node, rootNode, rootNode.childCount)
        singletonTypeInfos[initial.javaClass] = TypeInfo.Singleton(
            icon, node, onSelect as (Any) -> Unit, label
        )
        setSingleton(initial)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> addListType(
        type: Class<T>, label: String, icon: Icon,
        onSelect: (T) -> Unit, objToString: (T) -> String, copyObj: (T) -> T
    ) {
        val node = DefaultMutableTreeNode(label, true)
        model.insertNodeInto(node, rootNode, rootNode.childCount)
        listTypeInfos[type] = TypeInfo.List(
            icon, node, onSelect as (Any) -> Unit, objToString as (Any) -> String, copyObj as (Any) -> Any
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getSingleton(type: Class<T>): T =
        (singletonTypeInfos.getValue(type).node.userObject as StoredObj).obj as T

    fun setSingleton(singleton: Any) {
        val typeInfo = singletonTypeInfos.getValue(singleton.javaClass)
        typeInfo.node.userObject = StoredObj(typeInfo, singleton)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getList(type: Class<T>): List<T> =
        listTypeInfos.getValue(type).node.children().asSequence()
            .map { leaf -> ((leaf as DefaultMutableTreeNode).userObject as StoredObj).obj as T }
            .toList()

    fun addListElement(element: Any, select: Boolean = false) {
        val typeInfo = listTypeInfos.getValue(element.javaClass)
        val newLeaf = insertSortedLeaf(typeInfo.node, StoredObj(typeInfo, element))
        if (select)
            selectNode(newLeaf)
    }

    fun <T : Any> updateListElement(oldElement: T, newElement: T) {
        val oldTypeInfo = listTypeInfos.getValue(oldElement.javaClass)
        val newTypeInfo = listTypeInfos.getValue(newElement.javaClass)
        if (oldTypeInfo != newTypeInfo)
            throw IllegalArgumentException("Both elements must belong to the same list type.")
        for (leaf in oldTypeInfo.node.children().asSequence()) {
            val leafUserObj = (leaf as DefaultMutableTreeNode).userObject as StoredObj
            if (leafUserObj.obj == oldElement) {
                leaf.userObject = StoredObj(leafUserObj.typeInfo, newElement)
                break
            }
        }
    }

    fun duplicateSelectedListElement(): Boolean {
        val selectedNodeUserObj = (selectedNode ?: return false).userObject
        if (selectedNodeUserObj is StoredObj && selectedNodeUserObj.typeInfo is TypeInfo.List) {
            addListElement(selectedNodeUserObj.typeInfo.copyObj(selectedNodeUserObj.obj))
            return true
        }
        return false
    }

    fun removeSelectedListElement(): Boolean {
        val selectedNode = selectedNode ?: return false
        val selectedNodeUserObj = selectedNode.userObject
        if (selectedNodeUserObj is StoredObj && selectedNodeUserObj.typeInfo is TypeInfo.List) {
            model.removeNodeFromParent(selectedNode)
            return true
        }
        return false
    }

    fun updateSelectedListElement(newElement: Any) {
        val selectedNode = selectedNode ?: throw IllegalStateException()
        val selectedNodeUserObj = selectedNode.userObject
        if (selectedNodeUserObj is StoredObj && selectedNodeUserObj.typeInfo is TypeInfo.List) {
            selectedNode.userObject = StoredObj(selectedNodeUserObj.typeInfo, newElement)
            sortNode(selectedNode)
        } else
            throw IllegalStateException()
    }

    fun replaceAllListElements(newElements: Iterable<Any>) {
        val selectedNodeUserObj = selectedNode?.userObject.let { if (it is StoredObj) it else null }

        // If a list node is currently selected, deselect it for now. For this, the onDeselect() function is disabled
        // because a call to a tree method should by contract never trigger such callbacks. Note that we if we wouldn't
        // explicitly deselect the node here, it would be deselected automatically when it is removed in a couple of
        // lines, however, then onDeselect() would be called, which we do not want.
        if (selectedNodeUserObj?.typeInfo is TypeInfo.List)
            withoutSelectionListener { selectionRows = intArrayOf() }

        for (typeInfo in listTypeInfos.values)
            for (node in typeInfo.node.children().toList())  // Using .toList() avoids concurrent modification.
                model.removeNodeFromParent(node as MutableTreeNode)

        for (element in newElements)
            addListElement(element)

        for (typeInfo in listTypeInfos.values)
            expandPath(TreePath(typeInfo.node.path))

        val typeInfo = selectedNodeUserObj?.typeInfo
        if (typeInfo is TypeInfo.List) {
            // If a list node has previously been select, select the best matching node now...
            val oldElem = selectedNodeUserObj.obj
            val oldElemName = typeInfo.objToString(oldElem)
            val candidateNodes = typeInfo.node.children().asSequence().map { it as DefaultMutableTreeNode }.toList()

            val bestMatchingNode =
                candidateNodes.find { (it.userObject as StoredObj).obj == oldElem }
                    ?: candidateNodes.find { typeInfo.objToString((it.userObject as StoredObj).obj) == oldElemName }

            // If some matching node has been found, select it. Once again, don't notify onSelect().
            if (bestMatchingNode != null)
                withoutSelectionListener { selectionPath = TreePath(bestMatchingNode.path) }
        }
    }

    fun triggerOnSelectOrOnDeselect() {
        val selectedNodeUserObj = selectedNode?.userObject
        if (selectedNodeUserObj is StoredObj)
            selectedNodeUserObj.typeInfo.onSelect(selectedNodeUserObj.obj)
        else
            onDeselect?.invoke()
    }

    private fun sortNode(node: DefaultMutableTreeNode) {
        // Note: We temporarily disable the tree selection listener because the node removal and subsequent insertion
        // at another place should not close and re-open (and thus reset) the right-hand editing panel.
        withoutSelectionListener {
            // We also remember the current selection path so that we can restore it later.
            val selectionPath = this.selectionPath

            val parent = node.parent as DefaultMutableTreeNode
            model.removeNodeFromParent(node)
            var idx = parent.children().asSequence().indexOfFirst {
                (it as DefaultMutableTreeNode).userObject.toString() > node.userObject.toString()
            }
            if (idx == -1)
                idx = parent.childCount
            model.insertNodeInto(node, parent, idx)

            this.selectionPath = selectionPath
        }
    }

    private fun insertSortedLeaf(parent: MutableTreeNode, userObject: Any): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(userObject, false)
        model.insertNodeInto(node, parent, 0)
        sortNode(node)
        return node
    }

    private val selectedNode
        get() = lastSelectedPathComponent as DefaultMutableTreeNode?

    private fun selectNode(node: DefaultMutableTreeNode) {
        val leafPath = TreePath(node.path)
        scrollPathToVisible(TreePath(node.path))
        selectionPath = leafPath
    }

    private inline fun withoutSelectionListener(block: () -> Unit) {
        disableSelectionListener = true
        try {
            block()
        } finally {
            disableSelectionListener = false
        }
    }


    private sealed class TypeInfo {

        abstract val icon: Icon
        abstract val node: DefaultMutableTreeNode
        abstract val onSelect: (Any) -> Unit

        class Singleton(
            override val icon: Icon,
            override val node: DefaultMutableTreeNode,
            override val onSelect: (Any) -> Unit,
            val label: String
        ) : TypeInfo()

        class List(
            override val icon: Icon,
            override val node: DefaultMutableTreeNode,
            override val onSelect: (Any) -> Unit,
            val objToString: (Any) -> String,
            val copyObj: (Any) -> Any
        ) : TypeInfo()

    }


    private inner class StoredObj(val typeInfo: TypeInfo, val obj: Any) {
        override fun toString() = when (typeInfo) {
            is TypeInfo.Singleton -> typeInfo.label
            is TypeInfo.List -> typeInfo.objToString(obj)
        }
    }

}
