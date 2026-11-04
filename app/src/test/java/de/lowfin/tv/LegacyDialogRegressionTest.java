package de.lowfin.tv;

import org.robolectric.annotation.Config;

/** Run the shared native dialog behavior checks against the older TV framework too. */
@Config(sdk=28,qualifiers="w960dp-h540dp-land-tvdpi")
public class LegacyDialogRegressionTest extends DialogActionsTest {}
