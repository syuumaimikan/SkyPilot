package dev.skypilot.mod.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UtilitySettingsTest {
    @Test void oldConfigsRestoreOptInWidgets(){var cfg=new SkyPilotConfig();cfg.utility=null;cfg.sanitize();var u=cfg.utility;assertNotNull(u);assertFalse(u.coordinates||u.fps||u.ping||u.clock||u.speed||u.durability||u.slots||u.focus||u.session||u.worldTime);}
    @Test void clearingWidgetsKeepsLayoutPreferences(){var u=new UtilitySettings();u.coordinates=u.fps=u.ping=u.clock=u.speed=u.durability=u.slots=u.focus=u.session=u.worldTime=true;u.right=false;u.background=false;u.clear();assertFalse(u.coordinates||u.fps||u.ping||u.clock||u.speed||u.durability||u.slots||u.focus||u.session||u.worldTime);assertFalse(u.right);assertFalse(u.background);}
}
