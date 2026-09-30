package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class VoxStreamAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoxStreamAccessService"
        var instance: VoxStreamAccessibilityService? = null
            private set
    }

    private var lastFocusedEditableNode: AccessibilityNodeInfo? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val delayedCheckRunnable = Runnable {
        checkAndNotifyKeyboard()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FloatingBubbleManager.setAccessibilityConnected(true)
        Log.d(TAG, "VoxStreamAccessibilityService connected and active")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 30
        serviceInfo = info

        // Check initial keyboard state
        checkAndNotifyKeyboard()

        // Ensure floating bubble is using accessibility overlay layer for highest z-order
        FloatingBubbleService.instance?.attachToAccessibilityService(this)
    }

    private var lastReportedKeyboardVisible: Boolean? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val detectedPkg = getActivePackageName() ?: run {
            val eventPkg = event.packageName?.toString()
            if (!AppContextResolver.isIgnoredPackage(this, eventPkg)) eventPkg else null
        }

        if (detectedPkg != null) {
            FloatingBubbleManager.updateCurrentForegroundPackage(detectedPkg)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source
                if (source != null && isEditableNode(source)) {
                    lastFocusedEditableNode = source
                }
                checkAndNotifyKeyboard()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val inputFocused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (inputFocused != null && isEditableNode(inputFocused)) {
                    lastFocusedEditableNode = inputFocused
                }
                checkAndNotifyKeyboard()
            }
        }
    }

    private fun checkAndNotifyKeyboard() {
        val currentWindows = try {
            windows ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Error querying windows", e)
            emptyList()
        }

        var isImePresent = false
        val windowTypesSeen = StringBuilder()

        for (window in currentWindows) {
            val typeStr = when (window.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "IME"
                AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "A11Y_OVERLAY"
                else -> "OTHER(${window.type})"
            }
            if (windowTypesSeen.isNotEmpty()) windowTypesSeen.append(", ")
            windowTypesSeen.append(typeStr)

            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                isImePresent = true
            }
        }

        if (lastReportedKeyboardVisible != isImePresent) {
            Log.d(TAG, "Keyboard visibility changed -> $isImePresent | Seen windows: [$windowTypesSeen]")
            lastReportedKeyboardVisible = isImePresent
            FloatingBubbleManager.notifyKeyboardVisibility(isImePresent)
        }
    }

    private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText", ignoreCase = true) ||
            className.contains("AutoCompleteTextView", ignoreCase = true) ||
            className.contains("NoteEditText", ignoreCase = true) ||
            className.contains("TextInputEditText", ignoreCase = true)
        ) {
            return true
        }
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoxStreamAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            FloatingBubbleManager.setAccessibilityConnected(false)
        }
        lastFocusedEditableNode = null
        Log.d(TAG, "VoxStreamAccessibilityService destroyed")
    }

    /**
     * Determines whether the given node contains real, genuine user text versus an empty
     * field or a placeholder/hint string.
     */
    fun extractGenuineText(node: AccessibilityNodeInfo): String {
        val rawText = node.text?.toString() ?: ""
        if (rawText.isBlank()) {
            return ""
        }

        // 1. Safety override: If the rawText is longer than 15 characters, it is genuine user content / article / text.
        // WebViews (Chrome), custom editors, and chat apps often erroneously report isShowingHintText = true.
        // A hint is never a long sentence or paragraph.
        if (rawText.length > 15) {
            return rawText
        }

        // 2. Android 8.0+ API check: if the system explicitly flags that the node is showing hint text (for short strings <= 15 chars)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                if (node.isShowingHintText) {
                    return ""
                }
            } catch (_: Throwable) {
                // Ignore fallback
            }
        }

        // 3. Direct hint text comparison
        val hintText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                node.hintText?.toString()
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        if (!hintText.isNullOrBlank() && rawText.trim() == hintText.trim()) {
            return ""
        }

        // 4. Known app-specific placeholder strings for short text (e.g. Google Search, WhatsApp, Google Keep)
        val trimmed = rawText.trim()
        val knownPlaceholders = setOf(
            "note",
            "note…",
            "note...",
            "take a note",
            "take a note…",
            "take a note...",
            "title",
            "title…",
            "title...",
            "ask google…",
            "ask google...",
            "search…",
            "search...",
            "search or type url",
            "search or type web address",
            "type a message",
            "message",
            "send a message",
            "write a message",
            "write a comment…",
            "write a comment...",
            "add a comment…",
            "add a comment..."
        )
        if (knownPlaceholders.contains(trimmed.lowercase())) {
            return ""
        }

        return rawText
    }

    /**
     * Injects transcribed text into the target active editable field:
     * - Uses ACTION_SET_TEXT as the primary method with zero clipboard interaction.
     * - If cursor position (selStart/selEnd) is valid, inserts new text at the cursor.
     * - If cursor position is -1 but genuineExistingText is NOT blank, appends new text to the end with a space.
     * - If genuineExistingText is blank, sets the field to the new text.
     * - Only falls back to performPasteInjection if ACTION_SET_TEXT explicitly fails or returns false.
     * - Leaves user clipboard completely untouched whenever field injection succeeds.
     */
    fun injectText(newText: String): Boolean {
        if (newText.isEmpty()) return false
        val targetNode = getActiveEditableNode() ?: return false
        val targetPkg = targetNode.packageName?.toString() ?: ""

        // Primary Injection: Direct ACTION_SET_TEXT at cursor without touching system clipboard
        try {
            val genuineExistingText = extractGenuineText(targetNode)
            val selStart = try { targetNode.textSelectionStart } catch (_: Throwable) { -1 }
            val selEnd = try { targetNode.textSelectionEnd } catch (_: Throwable) { -1 }
            val fullRawText = targetNode.text?.toString() ?: ""

            val combinedText: String
            val newCursorPos: Int

            if (genuineExistingText.isBlank()) {
                combinedText = newText
                newCursorPos = newText.length
            } else if (selStart in 0..fullRawText.length && selEnd in selStart..fullRawText.length) {
                // Valid selection / cursor position: insert at cursor
                val before = fullRawText.substring(0, selStart)
                val after = fullRawText.substring(selEnd)
                combinedText = before + newText + after
                newCursorPos = selStart + newText.length
            } else {
                // Cursor position is invalid (-1) but genuine text exists: append to end with space
                val spacer = if (genuineExistingText.endsWith(" ") || genuineExistingText.endsWith("\n")) "" else " "
                combinedText = genuineExistingText + spacer + newText
                newCursorPos = combinedText.length
            }

            if (combinedText.isNotBlank()) {
                try {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                } catch (e: Exception) {
                    Log.w(TAG, "Notice requesting focus: ${e.message}")
                }

                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combinedText)
                }
                val injectionSucceeded = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (injectionSucceeded) {
                    try {
                        val selArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
                        }
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                    } catch (e: Exception) {
                        Log.w(TAG, "Notice setting selection after injection: ${e.message}")
                    }
                    Log.d(TAG, "Direct field insertion succeeded via ACTION_SET_TEXT (zero clipboard touch, target=$targetPkg)")
                    return true
                } else {
                    Log.w(TAG, "ACTION_SET_TEXT returned false on $targetPkg, falling back to performPasteInjection")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct field insertion error: ${e.message}, falling back to performPasteInjection")
        }

        // Secondary fallback if ACTION_SET_TEXT explicitly fails or is rejected by custom editor
        Log.d(TAG, "Attempting secondary fallback paste injection for $targetPkg")
        return performPasteInjection(targetNode, newText)
    }

    /**
     * Executes clipboard-based ACTION_PASTE injection:
     * - Saves the user's current clipboard.
     * - Sets clipboard to ONLY newly dictated text.
     * - Dispatches ACTION_PASTE.
     * - Verifies by polling node.refresh() and node.text after 300-400ms.
     * - Retries the paste once if not yet verified.
     * - Restores previous clipboard content ONLY AFTER verification confirms paste landed.
     * - Updates selection to end of text to ensure Keep commits the document model.
     */
    private fun performPasteInjection(targetNode: AccessibilityNodeInfo, newText: String): Boolean {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val originalClip = try {
            clipboard?.primaryClip
        } catch (e: Exception) {
            Log.w(TAG, "Could not read existing clipboard: ${e.message}")
            null
        }

        Log.d(TAG, "Executing paste-injection for pkg=${targetNode.packageName}, textLen=${newText.length}")

        val dictationClip = ClipData.newPlainText("VoxStream Dictation", newText)
        try {
            clipboard?.setPrimaryClip(dictationClip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting dictation clip", e)
            return false
        }

        try {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        } catch (e: Exception) {
            Log.w(TAG, "Notice requesting focus before paste: ${e.message}")
        }

        // Primary paste attempt
        val initialPasteResult = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        Log.d(TAG, "Initial ACTION_PASTE dispatch: $initialPasteResult")

        // Verification & clipboard restoration routine
        var attempts = 0
        val maxAttempts = 3
        val verifyDelayMs = 350L

        val verifyRunnable = object : Runnable {
            override fun run() {
                attempts++
                var isVerified = false
                try {
                    if (targetNode.refresh()) {
                        val currentText = targetNode.text?.toString() ?: ""
                        // Check if node text contains the new text or the snippet
                        val checkSnippet = if (newText.length > 20) newText.take(20) else newText
                        if (currentText.contains(checkSnippet)) {
                            isVerified = true
                            Log.d(TAG, "Paste verified on attempt $attempts! Node now contains dictated text (len=${currentText.length})")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error refreshing node during paste verification: ${e.message}")
                }

                if (isVerified) {
                    // Paste landed successfully -> safely restore user's original clipboard
                    safeRestoreOriginalClipboard(clipboard, originalClip, newText)
                } else if (attempts < maxAttempts) {
                    Log.w(TAG, "Paste not yet verified on attempt $attempts. Retrying ACTION_PASTE...")
                    try {
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    } catch (e: Exception) {
                        Log.w(TAG, "Retry paste error: ${e.message}")
                    }
                    mainHandler.postDelayed(this, verifyDelayMs)
                } else {
                    Log.w(TAG, "Paste verification exhausted after $maxAttempts attempts. Restoring clipboard.")
                    safeRestoreOriginalClipboard(clipboard, originalClip, newText)
                }
            }
        }

        mainHandler.postDelayed(verifyRunnable, verifyDelayMs)
        return true
    }

    /**
     * Safely restores the original clipboard ONLY IF the current clipboard is STILL
     * the temporary dictation clip set by VoxStream.
     * Prevents race conditions where a user copied new data during the verification window.
     */
    private fun safeRestoreOriginalClipboard(clipboard: ClipboardManager?, originalClip: ClipData?, expectedDictationText: String) {
        try {
            val currentClip = try { clipboard?.primaryClip } catch (e: Exception) { null }

            val isStillOurDictationClip = if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0)?.text?.toString() ?: ""
                val label = currentClip.description?.label?.toString() ?: ""
                currentText == expectedDictationText || label == "VoxStream Dictation"
            } else {
                false
            }

            if (isStillOurDictationClip) {
                if (originalClip != null) {
                    clipboard?.setPrimaryClip(originalClip)
                    Log.d(TAG, "Restored original user clipboard safely (no collision detected)")
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard?.clearPrimaryClip()
                    } else {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    Log.d(TAG, "Cleared temporary dictation clip from clipboard")
                }
            } else {
                Log.i(TAG, "User or external app copied new data during paste verification window. Preserving user's new clipboard content without overwriting!")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in safeRestoreOriginalClipboard: ${e.message}")
        }
    }

    private fun getActiveEditableNode(): AccessibilityNodeInfo? {
        // 1. Try system input focus directly
        try {
            val inputFocused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (inputFocused != null && isEditableNode(inputFocused)) {
                Log.d(TAG, "Found target editable via findFocus(FOCUS_INPUT)")
                return inputFocused
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding FOCUS_INPUT: ${e.message}")
        }

        // 2. Try rootInActiveWindow
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val rootFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (rootFocused != null && isEditableNode(rootFocused)) {
                    Log.d(TAG, "Found target editable via rootInActiveWindow FOCUS_INPUT")
                    return rootFocused
                }
                val found = findFocusedEditableInTree(root)
                if (found != null) {
                    Log.d(TAG, "Found target editable via rootInActiveWindow tree scan")
                    return found
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking rootInActiveWindow: ${e.message}")
        }

        // 3. Search across all interactive application windows (e.g. Google Keep, WhatsApp, Chrome)
        try {
            val currentWindows = windows
            if (!currentWindows.isNullOrEmpty()) {
                // Pass A: Look for focused editable in application windows
                for (w in currentWindows) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val wRoot = w.root ?: continue
                        val inputF = wRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (inputF != null && isEditableNode(inputF)) {
                            Log.d(TAG, "Found target editable in APP window via FOCUS_INPUT")
                            return inputF
                        }
                        val focusedInTree = findFocusedEditableInTree(wRoot)
                        if (focusedInTree != null) {
                            Log.d(TAG, "Found target editable in APP window tree scan (focused)")
                            return focusedInTree
                        }
                    }
                }

                // Pass B: If floating overlay held focus, look for any active visible editable node in APP windows
                for (w in currentWindows) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val wRoot = w.root ?: continue
                        val anyEditable = findBestEditableInTree(wRoot)
                        if (anyEditable != null) {
                            Log.d(TAG, "Found target editable in APP window tree scan (active editable fallback)")
                            return anyEditable
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching interactive windows: ${e.message}")
        }

        // 4. Fallback to last recorded node if still valid and editable
        val last = lastFocusedEditableNode
        if (last != null) {
            try {
                if (last.refresh() && isEditableNode(last)) {
                    Log.d(TAG, "Found target editable via refreshed lastFocusedEditableNode")
                    return last
                }
            } catch (e: Exception) {
                Log.w(TAG, "Last focused node refresh failed", e)
            }
        }
        return null
    }

    private fun findFocusedEditableInTree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && isEditableNode(node)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableInTree(child)
            if (result != null) return result
        }
        return null
    }

    private fun findBestEditableInTree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (isEditableNode(node) && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findBestEditableInTree(child)
            if (result != null) return result
        }
        return null
    }

    fun getActiveApplicationWindow(): AccessibilityWindowInfo? {
        val currentWindows = try { windows } catch (e: Throwable) { null }
        if (!currentWindows.isNullOrEmpty()) {
            for (window in currentWindows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && (window.isFocused || window.isActive)) {
                    return window
                }
            }
            for (window in currentWindows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return window
                }
            }
        }
        return null
    }

    fun getActivePackageName(): String? {
        // 1. Inspect windows for real TYPE_APPLICATION window
        val currentWindows = try { windows } catch (e: Throwable) { null }
        if (!currentWindows.isNullOrEmpty()) {
            // Check focused or active application window first
            for (window in currentWindows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && (window.isFocused || window.isActive)) {
                    val pkg = window.root?.packageName?.toString()
                    if (!AppContextResolver.isIgnoredPackage(this, pkg)) {
                        return pkg
                    }
                }
            }
            // Check any application window in z-order
            for (window in currentWindows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val pkg = window.root?.packageName?.toString()
                    if (!AppContextResolver.isIgnoredPackage(this, pkg)) {
                        return pkg
                    }
                }
            }
        }

        // 2. Check last focused editable node if it belongs to a valid target app
        val lastNodePkg = lastFocusedEditableNode?.packageName?.toString()
        if (!AppContextResolver.isIgnoredPackage(this, lastNodePkg)) {
            return lastNodePkg
        }

        // 3. Check root in active window
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        if (!AppContextResolver.isIgnoredPackage(this, rootPkg)) {
            return rootPkg
        }

        return null
    }
}
