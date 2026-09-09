# Design

The mark, its icons, and the interface rules that hold on both channels. The
rules are here because they are decisions, not accidents, and a screen added
later should follow them.

## The mark

The app mark is a **candlestick chart**: seven candles, three red and four
green, climbing left to right on a black card with a green frame. Every icon is
generated from one description of that geometry by `tools/build_icons.py`,
which writes the SVG favicon, the PWA and apple-touch PNGs, the maskable PWA
icon and the Android vector drawables. Edit the script, not the outputs; the
script needs Pillow and runs from the repository root.

A launcher crops the central 72×72 of a 108×108 adaptive icon and may mask it
to a circle, so the Android foreground scales the candles to fit the 66 dp safe
zone and drops the frame, which no mask would preserve. The PWA ships a
separate maskable icon for the same reason: full bleed, no frame, candles
inside the safe zone. The apple-touch icon is opaque to the edge because iOS
rounds the corners itself. The themed (monochrome) Android variant keeps the
same candle silhouettes in one tone.

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

**Numeric fields group as you type** and accept either "." or "," as the
decimal separator, because which one the phone's keypad offers depends on its
locale, not the app's.

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

Five destinations in two groups: the indicators (UF, dólar, bitcoin) and the
tools (inflación, créditos). On a phone they are a bottom bar with a hairline
between the groups; on a wide screen they are a rail with the group names
above each section, collapsible to icons. Five is the most a bottom bar can
hold and still be tapped accurately, so it is the ceiling: anything further has
to live inside one of these rather than beside them.
