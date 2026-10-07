package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.List;

/** Original voxel designs in player-local coordinates, with independently combinable slots. */
public final class CosmeticMesh {
    public record Voxel(double x, double y, double z, double size, int color) {}
    private CosmeticMesh() {}
    public static List<Voxel> build(SkyPilotConfig.Appearance a) {
        List<Voxel> out=new ArrayList<>();
        if (!"none".equals(a.cape)) {
            for(int row=0;row<16;row++)for(int col=0;col<10;col++) {
                int color=switch(a.cape) {
                    case "ember" -> row>10?0x8B3D42:(col+row*3)%7<2?0xFFD58A:0xD76C48;
                    case "midnight" -> (col*13+row*7)%23==0?0xDCE9FF:((row+col)%7==0?0x637ABD:0x27334F);
                    case "prism" -> new int[]{0x5FD6CD,0x6A9EE5,0x9C82DB,0xDC90BD}[(row+col)/4%4];
                    case "tide" -> (row+Math.round(Math.sin(col*.8)*2))%5<2?0x55B7D9:0x234566;
                    case "eclipse" -> Math.abs(Math.hypot(col-4.5,row-6)-3)<.65?0xEAC574:0x252639;
                    default -> (col+row/2)%6<2?0x55D6D2:(row+col)%4==0?0x6976CD:a.color;
                };
                out.add(new Voxel((col-4.5)*.052,1.46-row*.057,-.24-row*.008,.055,color));
            }
        }
        String hat="none".equals(a.hat)?a.accessory:a.hat;
        if ("crown".equals(hat)||"halo".equals(hat)) {
            int n="halo".equals(hat)?28:20;
            for(int i=0;i<n;i++) {
                double angle=i*Math.PI*2/n;
                out.add(new Voxel(Math.cos(angle)*.28,1.92+("crown".equals(hat)?(i%5==0?.12:0):.15),Math.sin(angle)*.28,.055,0xF5CD75));
                if("crown".equals(hat)&&i%5==0)out.add(new Voxel(Math.cos(angle)*.28,1.96,Math.sin(angle)*.28,.04,0x6DCFCB));
            }
        }
        if("ears".equals(hat))for(int side:new int[]{-1,1})for(int row=0;row<4;row++)for(int col=0;col<4-row;col++)
            out.add(new Voxel(side*(.12+col*.043),1.88+row*.048,0,.05,col==1?0xC799E6:0x30384F));
        if("horns".equals(hat))for(int side:new int[]{-1,1})for(int i=0;i<9;i++)
            out.add(new Voxel(side*(.22+Math.sin(i*.23)*.11),1.86+i*.035,-i*.018,.066-i*.004,i>5?0xB39CF5:0x454259));
        String back="none".equals(a.back)?a.accessory:a.back;
        if(List.of("wings","dragon","phoenix").contains(back))for(int side:new int[]{-1,1}) {
            for(int i=0;i<18;i++) {
                double x=.22+i*.04,y=1.4+Math.sin(i*Math.PI/22)*.5;
                int base="phoenix".equals(back)?0xF09055:a.color;
                out.add(new Voxel(side*x,y,-.29,.072,i%4==0?0xE0D4FC:base));
                int length="dragon".equals(back)?(int)(5+Math.sin(i*Math.PI/18)*8):5+(i%5);
                for(int j=1;j<=length;j++) {
                    if("dragon".equals(back)&&i>12&&j>length-(i-12))continue;
                    int color="dragon".equals(back)?(j==length?0xC7AFF6:0x61518A):("phoenix".equals(back)?(j>5?0xF9CB73:0xB95748):(j%3==0?0x8071B8:0xA38BFA));
                    out.add(new Voxel(side*(x+j*.015),y-j*.043,-.3-j*.003,.047,color));
                }
            }
        }
        return List.copyOf(out);
    }
}
