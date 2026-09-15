package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.muradgalayev.brainbuddy.R

// how much of a row a tile wants
enum class WidgetSpan { Full, Half }

// everything that can sit on the home screen. the order here is the starting order, and the
// ranking behind it still matters (what's happening, what's next, then the tools) so an
// untouched home screen already answers a question rather than just displaying data. from
// there the user rearranges and resizes, because what stays put is what makes a home screen
// reflexive, and 'put' means where they left it. span is likewise a starting width
enum class HomeWidget(
    val id: String,
    @androidx.annotation.StringRes val labelRes: Int,
    @androidx.annotation.StringRes val blurbRes: Int,
    val icon: ImageVector,
    val span: WidgetSpan,
    val optIn: Boolean = false,
    // true for a tile with a text field in it. holding one of those already means
    // select-and-paste, which belongs to the field, so hold-to-customise doesn't arm over it
    val holdsTextInput: Boolean = false,
) {
    // the starting screen, top to bottom: what today looks like, what's next, then the two ways to act on
    // it (focus alone, or add someone to do it with), then capture and health side by side
    Today(
        id = "today",
        labelRes = R.string.common_today,
        blurbRes = R.string.widget_today_blurb,
        icon = Icons.Rounded.Today,
        span = WidgetSpan.Full,
    ),
    NextUp(
        id = "next_up",
        labelRes = R.string.widget_next_up,
        blurbRes = R.string.widget_next_up_blurb,
        icon = Icons.Rounded.Bolt,
        span = WidgetSpan.Full,
    ),
    Focus(
        id = "focus",
        labelRes = R.string.home_focus_widget,
        blurbRes = R.string.widget_focus_blurb,
        icon = Icons.Rounded.Timer,
        span = WidgetSpan.Half,
    ),
    // right after Focus so the two pair up into one row: the other half of doing things with someone
    NearbyAdd(
        id = "nearby_add",
        labelRes = R.string.widget_nearby_add,
        blurbRes = R.string.widget_nearby_add_blurb,
        icon = Icons.Rounded.Bluetooth,
        span = WidgetSpan.Half,
    ),
    QuickAdd(
        id = "quick_add",
        labelRes = R.string.widget_quick_add,
        blurbRes = R.string.widget_quick_add_blurb,
        icon = Icons.Rounded.NoteAdd,
        span = WidgetSpan.Half,
    ),
    Wellness(
        id = "wellness",
        labelRes = R.string.widget_health,
        blurbRes = R.string.widget_health_blurb,
        icon = Icons.Rounded.FavoriteBorder,
        span = WidgetSpan.Half,
    ),

    // everything below starts switched off and waits in customise
    Mode(
        id = "mode",
        labelRes = R.string.widget_mode,
        blurbRes = R.string.widget_mode_blurb,
        icon = Icons.Rounded.Tune,
        span = WidgetSpan.Full,
        optIn = true,
    ),
    Wins(
        id = "wins",
        labelRes = R.string.widget_progress,
        blurbRes = R.string.widget_progress_blurb,
        icon = Icons.Rounded.EmojiEmotions,
        span = WidgetSpan.Half,
        optIn = true,
    ),
    Tree(
        id = "tree",
        labelRes = R.string.survey_focus_tree_short,
        blurbRes = R.string.widget_tree_blurb,
        icon = Icons.Rounded.Park,
        span = WidgetSpan.Full,
        optIn = true,
    ),
    AiPrompt(
        id = "ai_prompt",
        labelRes = R.string.widget_ask,
        blurbRes = R.string.widget_ask_blurb,
        icon = Icons.Rounded.AutoAwesome,
        span = WidgetSpan.Full,
        holdsTextInput = true,
        optIn = true,
    ),
    ;

    companion object {
        fun fromId(id: String): HomeWidget? = entries.firstOrNull { it.id == id }

        // opt-in tiles start hidden, everything else is on until it's turned off
        val defaultHidden: Set<String> = entries.filter { it.optIn }.map { it.id }.toSet()
    }
}

// one tile as it will actually be drawn: at its current width, on or off
data class HomeTile(
    val widget: HomeWidget,
    val span: WidgetSpan,
    val hidden: Boolean = false,
)

// a row of the home screen: one full-width tile, or a pair of half tiles. a half tile left on
// its own would sit next to a hole, so it widens to fill the row instead. every tile's insides
// are centred or space-between, so they survive both widths without a second layout
data class HomeRow(val tiles: List<HomeTile>) {
    val isPair: Boolean get() = tiles.size == 2
    val widgets: List<HomeWidget> get() = tiles.map { it.widget }
}

// the home screen as the user has arranged it. order and widths are stored separately from
// what's hidden, because they answer different questions and a tile that's off should come
// back where it was rather than at the end
data class HomeLayout(
    // every widget, hidden ones included, in the order the user put them
    val order: List<HomeWidget>,
    val hidden: Set<String>,
    val spans: Map<String, WidgetSpan>,
) {
    fun spanOf(widget: HomeWidget): WidgetSpan = spans[widget.id] ?: widget.span

    fun isHidden(widget: HomeWidget): Boolean = widget.id in hidden

    // rows to draw. while editing, hidden tiles stay in place as ghost slots, so the layout
    // doesn't reflow under the finger that just switched one off and a tile comes back where it went
    fun rows(includeHidden: Boolean): List<HomeRow> = packRows(
        order.filter { includeHidden || !isHidden(it) }
            .map { HomeTile(widget = it, span = spanOf(it), hidden = isHidden(it)) }
    )

    // the order after dragging the tile at `from` to `to`, ready to be stored
    fun reordered(from: Int, to: Int): List<HomeWidget> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}

// resolves what's stored into a layout. anything the stored order doesn't mention keeps its
// declared position rather than being appended, so a widget added in a later release turns up
// where it was designed to go instead of at the bottom of everyone's screen
fun homeLayout(
    storedOrder: List<String>?,
    hidden: Set<String>,
    storedSpans: Map<String, String>,
): HomeLayout {
    val known = HomeWidget.entries.associateBy { it.id }
    val ordered = storedOrder.orEmpty().mapNotNull { known[it] }
    val order = if (ordered.isEmpty()) {
        HomeWidget.entries.toList()
    } else {
        val placed = ordered.toMutableList()
        HomeWidget.entries.forEach { widget ->
            if (widget in placed) return@forEach
            // slot it in after the nearest tile it was declared to follow, rather than at its declared
            // index: that index means nothing once the user has rearranged, and using it would push their
            // tiles around to make room
            val anchor = HomeWidget.entries.takeWhile { it != widget }.lastOrNull { it in placed }
            placed.add(anchor?.let { placed.indexOf(it) + 1 } ?: 0, widget)
        }
        placed
    }
    val spans = storedSpans.mapNotNull { (id, raw) ->
        val span = WidgetSpan.entries.firstOrNull { it.name == raw } ?: return@mapNotNull null
        id to span
    }.toMap()
    return HomeLayout(order = order, hidden = hidden, spans = spans)
}

fun visibleHomeWidgets(hidden: Set<String>): List<HomeWidget> =
    HomeWidget.entries.filterNot { it.id in hidden }

// packs widgets at their declared widths, the layout before the user has changed anything
fun homeRows(widgets: List<HomeWidget>): List<HomeRow> =
    packRows(widgets.map { HomeTile(widget = it, span = it.span) })

// packs tiles into rows, pairing consecutive half tiles two at a time
fun packRows(tiles: List<HomeTile>): List<HomeRow> {
    val rows = mutableListOf<HomeRow>()
    var pending: HomeTile? = null

    for (tile in tiles) {
        if (tile.span == WidgetSpan.Full) {
            pending?.let { rows += HomeRow(listOf(it)) }
            pending = null
            rows += HomeRow(listOf(tile))
        } else {
            if (pending == null) {
                pending = tile
            } else {
                rows += HomeRow(listOf(pending, tile))
                pending = null
            }
        }
    }
    pending?.let { rows += HomeRow(listOf(it)) }
    return rows
}

// short tiles: a heading and a line of buttons. a row made only of these is drawn at their height
// instead of the tall paired-row height, which would leave an empty band under both
val HomeWidget.isCompact: Boolean
    get() = this == HomeWidget.Focus || this == HomeWidget.NearbyAdd

// quick add gets the bigger half of a pair. it's the tile whose whole value is being hit
// without aiming, and a wider target is a faster one. everything else splits evenly
fun widgetWeight(widget: HomeWidget): Float =
    if (widget == HomeWidget.QuickAdd) 1.25f else 1f
