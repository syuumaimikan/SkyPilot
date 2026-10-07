package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticCatalogTest {
    @Test void collectionHasUniqueIdsAndBoundedFiniteGeometry(){
        var ids=new HashSet<String>();
        for(var item:CosmeticCatalog.ITEMS){
            assertTrue(ids.add(item.id()));var a=new SkyPilotConfig.Appearance();item.equip(a);assertTrue(item.equipped(a));
            var voxels=CosmeticMesh.build(a);assertFalse(voxels.isEmpty());assertTrue(voxels.size()<800);
            for(var v:voxels){assertTrue(Double.isFinite(v.x()+v.y()+v.z()));assertTrue(v.size()>0&&v.size()<.2);assertTrue(Math.abs(v.x())<1.5&&v.y()>0&&v.y()<2.5);}
        }
    }
    @Test void slotsCombineAndLegacySettingsMigrateWithoutReplacingNewSlots(){
        var c=new SkyPilotConfig();c.appearance.cape="eclipse";c.appearance.hat="horns";c.appearance.accessory="crown";c.appearance.back="dragon";
        int combined=CosmeticMesh.build(c.sanitize().appearance).size();assertEquals("horns",c.appearance.hat);assertEquals("none",c.appearance.accessory);
        c.appearance.hat="none";assertTrue(combined>CosmeticMesh.build(c.appearance).size());
        c.appearance.accessory="halo";c.sanitize();assertEquals("halo",c.appearance.hat);
        c.appearance.hat=null;c.appearance.back="invalid";c.appearance.cape=null;c.sanitize();assertTrue(CosmeticMesh.build(c.appearance).isEmpty());
    }
}
