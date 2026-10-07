package dev.skypilot.mod.core;

/** Small optional read-only HUD widgets. */
public final class UtilitySettings {
    public boolean coordinates,fps,ping,clock,speed,durability,slots,focus,session,worldTime;
    public boolean right=true,background=true;
    public void clear(){coordinates=fps=ping=clock=speed=durability=slots=focus=session=worldTime=false;}
}
