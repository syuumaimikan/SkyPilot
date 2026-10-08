package dev.skypilot.mod.core;

/** Small optional read-only HUD widgets. */
public final class UtilitySettings {
    public boolean coordinates,fps,ping,clock,speed,durability,slots,focus,session,worldTime;
    public boolean right=true,background=true;
    public boolean armor,effects,memory,gc,inventoryWarning,durabilityWarning,itemComparison;
    public void clear(){coordinates=fps=ping=clock=speed=durability=slots=focus=session=worldTime=armor=effects=memory=gc=inventoryWarning=durabilityWarning=itemComparison=false;}
}
