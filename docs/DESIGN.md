# Design

The mark, its icons, and the interface rules that hold across the app. The
rules are here because they are decisions, not accidents, and a screen added
later should follow them.

## The mark

The app mark is a **candlestick chart**: seven candles, three red and four
green, climbing left to right on a black card with a green frame. Every icon is
generated from one description of that geometry by `tools/build_icons.py`,
which writes the SVG favicon, the PWA and apple-touch PNGs and the maskable
PWA icon. Edit the script, not the outputs; the script needs Pillow and runs
from the repository root.

A launcher that installs the app to a home screen may crop the icon and mask it
to a circle, so the PWA ships a separate maskable variant: full bleed, no
frame — which no mask would preserve — and the candles scaled to stay inside
the safe zone, since the leftmost and rightmost are the ones that would be cut.
The apple-touch icon is opaque to the edge because iOS rounds the corners
itself.

Empty states reuse the symbol of the thing that is missing: the simulation
list shows the same bank mark its tab carries.

The mark was first the Chilean flag in a circle with the UF series climbing
across it, and before that a copihue. Two lessons from those iterations are
worth keeping if the mark is ever revisited: an outline flower reads as a
scribble at list sizes, and any mark has to be compared at real sizes before it
is adopted, because below about 48 px a silhouette that reads at 500 px can
stop reading at all.

## Interface rules

**The headline never lies.** Scrubbing a chart reports the explored day
separately and leaves the headline alone. A future value has its own card and
its own label. A date the app cannot answer gets "Sin valor" and a reason, not
a neighbouring day's figure.

**Every figure has a source and a date.** The UF card carries a badge naming
its source and whether it is official. The bitcoin card names the exchange and
the day of the dollar it used. The peso field of a converter that passes
through a rate says which day's rate.

**Every figure is punctuated.** `$40.885,63`, `1.770,23 UF`, `13.397 días`.
Counts get their separators too, which is why day spans and row counts go
through the formatter rather than being interpolated.

**Numeric fields group when they lose focus**, never while the caret is in
them, so nothing moves under the fingers of someone still typing. They accept
either "." or "," as the decimal separator, because which one a phone's keypad
offers depends on its locale, not the app's. The separator is always supplied
by the display and never typed.

**A card shares itself, and only itself.** The share button on a card sends
the card as text: the same figures, the same source line, nothing from the
cards around it. The equivalence leads on the inflation card because that is
what gets quoted in a chat; the arithmetic follows for anyone who wants to
check it. Emphasis uses the marks chat apps understand.

**Share on a handheld, copy on a computer.** The rule is by device, not by
what the browser happens to implement, and the button says which it is about
to do rather than promising to share and quietly copying.

**Install on a handheld, bookmark on a computer.** The install card offers the
home screen on a phone or a tablet, with the steps that platform actually
needs, and asks for a bookmark on a laptop or a desktop, naming the shortcut.
Dismissing it is remembered; a prompt that keeps coming back after a "no" is a
nag. The "Acerca de" sheet keeps the offer permanently, so dismissing it on the
main screen never loses it for good.

**The theme is a two-way switch.** Light or dark, starting from whatever the
system prefers, so the first tap always changes something visible.

**Consulting and creating are different jobs.** A saved simulation opens on
its result and its payment table, with the form below; a new one opens on the
form. The day-by-day list is collapsed by default because it runs to hundreds
of rows and is a reference, not the main event.

**Nothing interrupts.** The independence notice, the source attribution the
CMF requires and the financial disclaimer live in "Acerca de", reached from a
quiet line at the foot of the screen or by tapping the source badge. There is
no launch dialog and nothing to acknowledge.

**Say where the data goes.** The simulation list ends with a line saying that
simulations are anonymous, stay on the device and are never sent or shared.
Anyone typing in what they earn and what they owe deserves to be told.

## Navigation

Six destinations in two groups, and the groups are not equals. The indicators
— resumen, UF, dólar, bitcoin — are what the app is opened for, and all four
sit in the bottom bar where a thumb reaches them. The tools — inflación,
créditos — are behind one more tap, in a menu the bar's fifth slot opens.

Five slots is the most a bottom bar can hold and still be tapped accurately.
That is the constraint, and the way to spend it is on the things people check
daily, not to divide it evenly among everything that exists. **Anything added
from here goes inside a destination or into the tools menu, never beside
them.**

Hiding the tools costs something, and it is paid for in two places. The menu's
button becomes the tool you are on — its icon, its name, marked as the current
page — so depth never costs you knowing where you are. And on a wide screen
there is no five-slot problem at all, so the rail shows both groups in full,
with their names above them, collapsible to icons. It is the same navigation
with the same two groups, sized to the room available.

**The overview is a doorway, not a screen to work on.** It carries what
someone checking in the morning wants — the figure, how it moved, where it came
from, the shape of the last few months — and nothing to interact with. Its
charts take no pointer events, so a tap anywhere on a card opens the screen
that has the converter, the date lookup and the chart you can actually scrub.
Every card is a link, so it can be middle-clicked and opened in a tab like any
other.

**Every screen has an address.** `#/uf`, `#/bitcoin`, `#/creditos`; the
overview is the bare one. A screen that cannot be linked to cannot be
recommended, and the back button leaving the app entirely is a bug people
blame on themselves. A hash that no longer resolves opens the overview rather
than nothing, which is the same promise as everywhere else here.
