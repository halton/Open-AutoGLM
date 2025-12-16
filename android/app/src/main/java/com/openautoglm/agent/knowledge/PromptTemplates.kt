package com.openautoglm.agent.knowledge

import com.openautoglm.agent.data.entities.ActionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Provides localized VLM prompt templates for the Android Agent.
 *
 * This object contains system prompts, action format templates, and utility functions
 * for generating prompts in both Chinese and English languages.
 */
object PromptTemplates {

    // Supported language codes
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_CHINESE = "zh"

    // Coordinate system range (normalized coordinates from 0 to 999)
    const val COORDINATE_MIN = 0
    const val COORDINATE_MAX = 999

    /**
     * Action format strings for VLM output parsing.
     * These define how the VLM should express each action type.
     */
    object ActionFormats {
        const val TAP = """do(action="Tap", element=[x,y])"""
        const val TAP_WITH_MESSAGE = """do(action="Tap", element=[x,y], message="description")"""
        const val DOUBLE_TAP = """do(action="Double Tap", element=[x,y])"""
        const val LONG_PRESS = """do(action="Long Press", element=[x,y])"""
        const val SWIPE = """do(action="Swipe", start=[x1,y1], end=[x2,y2])"""
        const val TYPE = """do(action="Type", text="xxx")"""
        const val TYPE_NAME = """do(action="Type_Name", text="xxx")"""
        const val LAUNCH = """do(action="Launch", app="xxx")"""
        const val BACK = """do(action="Back")"""
        const val HOME = """do(action="Home")"""
        const val WAIT = """do(action="Wait", duration="x seconds")"""
        const val TAKE_OVER = """do(action="Take_over", message="xxx")"""
        const val ENTER = """do(action="Enter")"""
        const val INTERACT = """do(action="Interact")"""
        const val NOTE = """do(action="Note", message="True")"""
        const val CALL_API = """do(action="Call_API", instruction="xxx")"""
        const val FINISH = """finish(message="xxx")"""
    }

    /**
     * Response format tags used by the VLM.
     */
    object ResponseTags {
        const val THINK_OPEN = "<think>"
        const val THINK_CLOSE = "</think>"
        const val ANSWER_OPEN = "<answer>"
        const val ANSWER_CLOSE = "</answer>"
    }

    /**
     * Maps ActionType enum to the corresponding action string used in prompts.
     */
    fun getActionString(actionType: ActionType): String {
        return when (actionType) {
            ActionType.TAP -> "Tap"
            ActionType.DOUBLE_TAP -> "Double Tap"
            ActionType.LONG_PRESS -> "Long Press"
            ActionType.SWIPE -> "Swipe"
            ActionType.TYPE -> "Type"
            ActionType.BACK -> "Back"
            ActionType.HOME -> "Home"
            ActionType.LAUNCH -> "Launch"
            ActionType.WAIT -> "Wait"
            ActionType.TAKE_OVER -> "Take_over"
            ActionType.ENTER -> "Enter"
            ActionType.FINISH -> "finish"
        }
    }

    /**
     * Gets the current formatted date string for the specified language.
     */
    private fun getFormattedDate(language: String): String {
        val calendar = Calendar.getInstance()
        return when (language) {
            LANGUAGE_CHINESE -> {
                val weekdayNames = arrayOf(
                    "星期日", "星期一", "星期二", "星期三",
                    "星期四", "星期五", "星期六"
                )
                val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
                val weekday = weekdayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
                "${dateFormat.format(calendar.time)} $weekday"
            }
            else -> {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd, EEEE", Locale.ENGLISH)
                dateFormat.format(calendar.time)
            }
        }
    }

    /**
     * Gets detailed date context including this week's Sunday for better date understanding.
     */
    private fun getDetailedDateContext(language: String): String {
        val calendar = Calendar.getInstance()
        val today = calendar.clone() as Calendar
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Calculate this week's Sunday (Sunday is day 1 in Calendar)
        val daysUntilSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (8 - dayOfWeek)
        calendar.add(Calendar.DAY_OF_MONTH, daysUntilSunday)
        val thisSunday = calendar.clone() as Calendar

        // Calculate this week's Saturday
        calendar.time = today.time
        val daysUntilSaturday = if (dayOfWeek == Calendar.SATURDAY) 0 else (Calendar.SATURDAY - dayOfWeek)
        calendar.add(Calendar.DAY_OF_MONTH, daysUntilSaturday)
        val thisSaturday = calendar.clone() as Calendar

        return when (language) {
            LANGUAGE_CHINESE -> {
                val weekdayNames = arrayOf(
                    "星期日", "星期一", "星期二", "星期三",
                    "星期四", "星期五", "星期六"
                )
                val dateFormat = SimpleDateFormat("M月d日", Locale.CHINESE)
                val fullDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
                val todayWeekday = weekdayNames[today.get(Calendar.DAY_OF_WEEK) - 1]

                """今天是: ${fullDateFormat.format(today.time)} ${todayWeekday}
本周六是: ${dateFormat.format(thisSaturday.time)}
本周日是: ${dateFormat.format(thisSunday.time)}
重要提示: 当用户说"本周日"时，指的是${dateFormat.format(thisSunday.time)}，不是其他日期！"""
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMMM d", Locale.ENGLISH)
                val fullDateFormat = SimpleDateFormat("yyyy-MM-dd, EEEE", Locale.ENGLISH)

                """Today is: ${fullDateFormat.format(today.time)}
This Saturday: ${dateFormat.format(thisSaturday.time)}
This Sunday: ${dateFormat.format(thisSunday.time)}
Important: When user says "this Sunday", it means ${dateFormat.format(thisSunday.time)}, not any other date!"""
            }
        }
    }

    /**
     * Gets the system prompt for the VLM agent.
     *
     * @param language Language code ("en" for English, "zh" for Chinese)
     * @param appContext Optional context about the current app being used
     * @return The complete system prompt string
     */
    fun getSystemPrompt(language: String, appContext: String? = null): String {
        val formattedDate = getFormattedDate(language)
        val basePrompt = when (language) {
            LANGUAGE_CHINESE -> getChineseSystemPrompt(formattedDate)
            else -> getEnglishSystemPrompt(formattedDate)
        }

        return if (appContext != null) {
            "$basePrompt\n\n$appContext"
        } else {
            basePrompt
        }
    }

    /**
     * Formats a user message containing the task and screenshot for the VLM.
     *
     * @param task The task description from the user
     * @param screenshotBase64 Base64-encoded screenshot of the current screen
     * @return Formatted user message for the VLM
     */
    fun formatUserMessage(task: String, screenshotBase64: String): String {
        return buildString {
            append("Task: $task\n\n")
            append("Current screen state:\n")
            append("[Screenshot attached as base64 image]\n")
            append("data:image/png;base64,$screenshotBase64")
        }
    }

    /**
     * Formats a user message with task history for multi-turn conversations.
     *
     * @param task The original task description
     * @param currentStep The current step number
     * @param previousActions List of previous action descriptions
     * @param screenshotBase64 Base64-encoded screenshot of the current screen
     * @return Formatted user message with history context
     */
    fun formatUserMessageWithHistory(
        task: String,
        currentStep: Int,
        previousActions: List<String>,
        screenshotBase64: String
    ): String {
        return buildString {
            append("Task: $task\n\n")
            if (previousActions.isNotEmpty()) {
                append("Previous actions (Step ${currentStep - 1}):\n")
                previousActions.takeLast(5).forEachIndexed { index, action ->
                    append("${index + 1}. $action\n")
                }
                append("\n")
            }
            append("Current screen state (Step $currentStep):\n")
            append("[Screenshot attached as base64 image]\n")
            append("data:image/png;base64,$screenshotBase64")
        }
    }

    /**
     * Generates the English system prompt.
     */
    private fun getEnglishSystemPrompt(formattedDate: String): String {
        val detailedDateContext = getDetailedDateContext(LANGUAGE_ENGLISH)
        return """$detailedDateContext

# Setup
You are a professional Android operation agent assistant that can fulfill the user's high-level instructions. Given a screenshot of the Android interface at each step, you first analyze the situation, then plan the best course of action using Python-style pseudo-code.

# More details about the code
Your response format must be structured as follows:

Think first: Use <think>...</think> to analyze the current screen, identify key elements, and determine the most efficient action.
Provide the action: Use <answer>...</answer> to return a single line of pseudo-code representing the operation.

Your output should STRICTLY follow the format:
<think>
[Your thought]
</think>
<answer>
[Your operation code]
</answer>

## Available Actions

- **Tap**
  Perform a tap action on a specified screen area. The element is a list of 2 integers, representing the coordinates of the tap point. The coordinate system starts from top-left (0,0) to bottom-right (999,999).
  **Example**:
  <answer>
  do(action="Tap", element=[x,y])
  </answer>

- **Type**
  Enter text into the currently focused input field. Make sure the input field is focused first (tap on it). The phone may use ADB keyboard which doesn't visually show on screen. Check for 'ADB Keyboard {ON}' text at the bottom or if the input field is highlighted/active. When using Type, existing text in the input field is automatically cleared before typing.
  **Example**:
  <answer>
  do(action="Type", text="Hello World")
  </answer>

- **Swipe**
  Perform a swipe action with start point and end point. Useful for scrolling content, navigating between screens, or gesture-based navigation. The coordinate system is (0,0) to (999,999).
  **Example**:
  <answer>
  do(action="Swipe", start=[x1,y1], end=[x2,y2])
  </answer>

- **Long Press**
  Perform a long press action on a specified screen area. Useful for triggering context menus, selecting text, or activating long-press interactions.
  **Example**:
  <answer>
  do(action="Long Press", element=[x,y])
  </answer>

- **Double Tap**
  Perform a double tap on a specified screen area. Useful for zooming, selecting text, or opening items.
  **Example**:
  <answer>
  do(action="Double Tap", element=[x,y])
  </answer>

- **Launch**
  Launch an app. Use this action when you need to open a specific app - it's faster than navigating from home screen.
  **Example**:
  <answer>
  do(action="Launch", app="Settings")
  </answer>

- **Back**
  Press the Back button to navigate to the previous screen or close current dialog.
  **Example**:
  <answer>
  do(action="Back")
  </answer>

- **Home**
  Press the Home button to return to the launcher/home screen.
  **Example**:
  <answer>
  do(action="Home")
  </answer>

- **Wait**
  Wait for the page to load for specified seconds.
  **Example**:
  <answer>
  do(action="Wait", duration="3 seconds")
  </answer>

- **Enter**
  Press Enter/Submit to confirm input, submit search queries, or send messages. Use this after typing text in a search box or input field to submit.
  **Example**:
  <answer>
  do(action="Enter")
  </answer>

- **Take Over**
  Request user assistance, typically for login or verification steps.
  **Example**:
  <answer>
  do(action="Take_over", message="Please complete login verification")
  </answer>

- **Finish**
  Terminate the task and provide a completion message.
  **Example**:
  <answer>
  finish(message="Task completed.")
  </answer>

## Important Rules

1. Before any action, check if you're in the correct app. If not, use Launch first.
2. If you entered an irrelevant page, use Back. If Back doesn't work, tap the back arrow (usually top-left) or close button (usually top-right).
3. If page content hasn't loaded, Wait up to 3 times, then use Back to retry.
4. If the page shows network issues, tap reload.
5. If you can't find the target item, try Swipe to scroll and search.
6. **STRICT REQUIREMENT MATCHING**: User requirements (time, duration, price, date, etc.) MUST be strictly followed. Do NOT relax or lower standards. For example: if user requests "within 3 hours", NEVER select options over 3 hours; if user requests "arrive before 11am", ensure arrival time is before 11am. If no matching option exists, continue scrolling to search or inform the user, but NEVER select non-compliant options.
7. Always verify the previous action took effect before proceeding.
8. If taps don't register, wait briefly or adjust tap position.
9. If swipes don't work, adjust start position or increase swipe distance.
10. **DATE UNDERSTANDING**: When user says "this Sunday" or similar, refer to the date information provided above. Do NOT guess dates.
11. **COMPLETE ORDER FLOW**: For booking/shopping tasks, complete the ENTIRE flow: select item -> select passenger/recipient -> submit order. Do NOT stop midway for user to complete. Only use Take_over when sensitive information (payment password, verification code) is required.
12. **VERIFY BEFORE SUBMIT**: Before submitting any order, carefully verify all selections match user requirements (date, time, duration, price, etc.). If not matching, go back and re-select.
13. Before finishing, carefully verify the task is fully completed.

REMEMBER:
- Think before you act: Always analyze the current UI and the best course of action before executing any step.
- Only ONE LINE of action in <answer> part per response.
- Generate execution code strictly according to format requirements."""
    }

    /**
     * Generates the Chinese system prompt.
     */
    private fun getChineseSystemPrompt(formattedDate: String): String {
        val detailedDateContext = getDetailedDateContext(LANGUAGE_CHINESE)
        return """$detailedDateContext

你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
你必须严格按照要求输出以下格式：
<think>{think}</think>
<answer>{action}</answer>

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式。

操作指令及其作用如下：
- do(action="Launch", app="xxx")
    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y])
    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y], message="重要操作")
    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
- do(action="Type", text="xxx")
    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。操作完成后，你将自动收到结果状态的截图。
- do(action="Type_Name", text="xxx")
    Type_Name是输入人名的操作，基本功能同Type。
- do(action="Interact")
    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
- do(action="Swipe", start=[x1,y1], end=[x2,y2])
    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
- do(action="Note", message="True")
    记录当前页面内容以便后续总结。
- do(action="Call_API", instruction="xxx")
    总结或评论当前页面或已记录的内容。
- do(action="Long Press", element=[x,y])
    Long Press是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
- do(action="Double Tap", element=[x,y])
    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Take_over", message="xxx")
    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
- do(action="Back")
    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
- do(action="Home")
    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
- do(action="Wait", duration="x seconds")
    等待页面加载，x为需要等待多少秒。
- do(action="Enter")
    Enter是确认输入操作，在输入框输入文本后按回车/搜索键提交。在搜索框输入后使用此操作来提交搜索，或在聊天输入框中发送消息。
- finish(message="xxx")
    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。

必须遵循的规则：
1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
4. 如果页面显示网络问题，需要重新加载，请点击重新加载。
5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
6. 【严格要求匹配】用户提出的具体要求（如时间、时长、价格等）必须严格遵守，不能放宽或降低标准。例如：用户要求"3小时内"的行程，绝对不能选择超过3小时的选项；用户要求"11点之前到达"，必须确保到达时间在11点之前。如果找不到完全符合的选项，应该继续滑动查找或向用户说明情况，而不是选择不符合要求的选项。
7. 在做小红书总结类任务时一定要筛选图文笔记。
8. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
9. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
10. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
11. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
12. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。【重要】用户说"本周日"时，请参考上方的日期信息确定具体日期，不要自己猜测。
13. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因")。
18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。
19. 【完整执行订单流程】在做订票、购物等任务时，必须完成整个流程：选择商品/票务 -> 选择乘车人/收货人 -> 提交订单。不要在中途停止让用户自己完成。如果需要选择乘车人/联系人，请点击选择并勾选；如果需要提交订单，请点击提交按钮。只有在遇到支付密码、验证码等需要用户敏感信息时，才使用Take_over让用户介入。
20. 【验证选择正确性】在提交订单前，必须仔细核对所选项目是否符合用户的所有要求（日期、时间、时长、价格等）。如果发现不符合，应该返回重新选择，而不是继续提交。"""
    }

    /**
     * Gets a simplified/condensed system prompt for resource-constrained environments.
     * Useful for on-device inference where context length is limited.
     *
     * @param language Language code ("en" for English, "zh" for Chinese)
     * @return Condensed system prompt string
     */
    fun getCondensedSystemPrompt(language: String): String {
        val formattedDate = getFormattedDate(language)
        return when (language) {
            LANGUAGE_CHINESE -> getCondensedChinesePrompt(formattedDate)
            else -> getCondensedEnglishPrompt(formattedDate)
        }
    }

    private fun getCondensedEnglishPrompt(formattedDate: String): String {
        return """Date: $formattedDate
You are an Android agent. Analyze screenshots and execute actions to complete tasks.

Output format:
<think>[reasoning]</think>
<answer>[action]</answer>

Actions (coordinates 0-999):
- do(action="Tap", element=[x,y]) - tap at position
- do(action="Type", text="...") - type text (auto-clears field)
- do(action="Enter") - press Enter/submit after typing
- do(action="Swipe", start=[x1,y1], end=[x2,y2]) - swipe gesture
- do(action="Long Press", element=[x,y]) - long press
- do(action="Launch", app="...") - launch app
- do(action="Back") - back button
- do(action="Home") - home button
- do(action="Wait", duration="x seconds") - wait
- finish(message="...") - complete task

Rules: Check app first, use Back for wrong pages, Wait if loading, Swipe to find items.
One action per response. Verify actions succeed before continuing."""
    }

    private fun getCondensedChinesePrompt(formattedDate: String): String {
        return """日期: $formattedDate
你是一个Android智能体。分析截图并执行操作来完成任务。

输出格式:
<think>[推理]</think>
<answer>[操作]</answer>

操作指令 (坐标范围 0-999):
- do(action="Tap", element=[x,y]) - 点击
- do(action="Type", text="...") - 输入文本（自动清除原文本）
- do(action="Enter") - 输入后按回车提交
- do(action="Swipe", start=[x1,y1], end=[x2,y2]) - 滑动
- do(action="Long Press", element=[x,y]) - 长按
- do(action="Launch", app="...") - 启动应用
- do(action="Back") - 返回
- do(action="Home") - 主屏幕
- do(action="Wait", duration="x seconds") - 等待
- finish(message="...") - 完成任务

规则: 先检查app，错误页面用Back，加载中用Wait，找不到用Swipe滑动查找。
每次响应只能有一个操作。继续前验证操作是否生效。"""
    }

    /**
     * Parses the VLM response to extract thinking and action components.
     *
     * @param response The raw VLM response string
     * @return Pair of (thinking, action) strings, or null if parsing fails
     */
    fun parseVlmResponse(response: String): Pair<String, String>? {
        val thinkPattern = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL)
        val answerPattern = Regex("""<answer>(.*?)</answer>""", RegexOption.DOT_MATCHES_ALL)

        val thinkMatch = thinkPattern.find(response)
        val answerMatch = answerPattern.find(response)

        return if (thinkMatch != null && answerMatch != null) {
            Pair(
                thinkMatch.groupValues[1].trim(),
                answerMatch.groupValues[1].trim()
            )
        } else {
            null
        }
    }

    /**
     * Validates that coordinates are within the valid range.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @return True if coordinates are valid
     */
    fun validateCoordinates(x: Int, y: Int): Boolean {
        return x in COORDINATE_MIN..COORDINATE_MAX && y in COORDINATE_MIN..COORDINATE_MAX
    }

    /**
     * Converts normalized coordinates (0-999) to actual screen coordinates.
     *
     * @param normalizedX Normalized X coordinate (0-999)
     * @param normalizedY Normalized Y coordinate (0-999)
     * @param screenWidth Actual screen width in pixels
     * @param screenHeight Actual screen height in pixels
     * @return Pair of actual (x, y) coordinates
     */
    fun normalizedToScreenCoordinates(
        normalizedX: Int,
        normalizedY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Int, Int> {
        val actualX = (normalizedX * screenWidth) / COORDINATE_MAX
        val actualY = (normalizedY * screenHeight) / COORDINATE_MAX
        return Pair(actualX, actualY)
    }

    /**
     * Converts actual screen coordinates to normalized coordinates (0-999).
     *
     * @param screenX Actual X coordinate in pixels
     * @param screenY Actual Y coordinate in pixels
     * @param screenWidth Actual screen width in pixels
     * @param screenHeight Actual screen height in pixels
     * @return Pair of normalized (x, y) coordinates (0-999)
     */
    fun screenToNormalizedCoordinates(
        screenX: Int,
        screenY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Int, Int> {
        val normalizedX = (screenX * COORDINATE_MAX) / screenWidth
        val normalizedY = (screenY * COORDINATE_MAX) / screenHeight
        return Pair(normalizedX, normalizedY)
    }
}
