"""Demo data that only the 2.5 screens need (screens/v25.py). Same rules as lib/demo.py: nothing real, one dataset."""

# Dashboard settings (ControlsActivity, section «Dashboard»): the Android Auto window design, hide-bottom-bar row.
DASH = dict(design="window", crop_pct=25, crop_max=40, crop_default=25)   # AaWindowLayout.DEFAULT/MAX_BOTTOM_CROP_PCT
# Home «Dashboard» card (MainActivity.renderDashCard): the style whose preview is drawn, and its colours.
HOME_DASH = dict(design="sport", theme="night")
# Cuadro (HudViewActivity): the bike is connected and the Dashboard mode is lit.
CUADRO = dict(connected=True, mode="dash")
