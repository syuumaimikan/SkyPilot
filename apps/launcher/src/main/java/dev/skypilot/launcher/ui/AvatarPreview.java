package dev.skypilot.launcher.ui;

import dev.skypilot.mod.core.CosmeticMesh;
import dev.skypilot.mod.core.SkyPilotConfig;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Supplier;

/** Depth-sorted, shaded cuboid studio; all cosmetic geometry comes from the in-game mesh. */
final class AvatarPreview extends JPanel {
    private final Supplier<SkyPilotConfig.Appearance> appearance;
    private double yaw=Math.PI*.85,zoom=1;
    private int dragX;
    private final boolean studio;
    AvatarPreview(Supplier<SkyPilotConfig.Appearance> appearance,boolean studio) {
        this.appearance=appearance;this.studio=studio;
        setOpaque(false);setFocusable(studio);
        getAccessibleContext().setAccessibleName("3D cosmetic preview");
        if(studio) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){dragX=e.getX();requestFocusInWindow();}});
            addMouseMotionListener(new MouseMotionAdapter(){public void mouseDragged(MouseEvent e){yaw+=(e.getX()-dragX)*.013;dragX=e.getX();repaint();}});
            addMouseWheelListener(e->{zoom=Math.max(.7,Math.min(1.35,zoom-e.getPreciseWheelRotation()*.07));repaint();});
            bind("LEFT",-.15);bind("RIGHT",.15);
        }
    }
    private void bind(String key,double step){getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){yaw+=step;repaint();}});}
    void front(){yaw=.2;repaint();} void back(){yaw=Math.PI*.95;repaint();}
    private record Face(double[] x,double[] y,double depth,int color) {}
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=Widgets.smooth(graphics);
        int w=getWidth(),h=getHeight();
        if(studio) {
            g.setPaint(new GradientPaint(0,0,Theme.SURFACE,w,h,Theme.HERO_B));g.fillRoundRect(0,0,w,h,24,24);
            g.setColor(Theme.alpha(Theme.ACCENT,16));g.fillOval(w/2-w/3,h/2-w/3,w*2/3,w*2/3);
            g.setColor(Theme.alpha(Theme.ACCENT_2,35));g.drawOval(w/2-w/3,h-60,w*2/3,24);
            g.setFont(Theme.font(Font.BOLD,10));g.setColor(Theme.MUTED);g.drawString("STUDIO / 3D PREVIEW",20,26);
        }
        double scale=(studio?Math.min(w*.38,Math.max(20,(h-105)/2.3)):Math.min(w*.41,h*.39))*zoom,cx=w/2.,base=studio?h-65:h-10;
        g.setColor(new Color(0,0,0,45));g.fillOval((int)(cx-scale*.6),(int)(base-5),(int)(scale*1.2),12);
        ArrayList<Face> faces=new ArrayList<>();
        // Original neutral mannequin; never presented as the user's account skin.
        box(faces,0,1.65,0,.49,.49,.49,0x9CAABC);
        box(faces,0,1.08,0,.50,.65,.25,0x394961);
        box(faces,-.39,1.08,0,.24,.65,.25,0x455673);box(faces,.39,1.08,0,.24,.65,.25,0x455673);
        box(faces,-.13,.43,0,.24,.65,.25,0x253247);box(faces,.13,.43,0,.24,.65,.25,0x253247);
        box(faces,-.13,.07,.04,.25,.13,.33,0x172031);box(faces,.13,.07,.04,.25,.13,.33,0x172031);
        // Front-facing inset eyes and a studio hoodie accent.
        box(faces,-.12,1.68,.249,.065,.045,.009,0x253247);box(faces,.12,1.68,.249,.065,.045,.009,0x253247);
        box(faces,0,1.27,.13,.10,.12,.015,0x55D6D2);
        for(var v:CosmeticMesh.build(appearance.get()))box(faces,v.x(),v.y(),v.z(),v.size(),v.size(),v.size(),v.color());
        faces.sort(Comparator.comparingDouble(Face::depth));
        for(Face f:faces) {
            Path2D p=new Path2D.Double();p.moveTo(cx+f.x[0]*scale,base-f.y[0]*scale);
            for(int i=1;i<4;i++)p.lineTo(cx+f.x[i]*scale,base-f.y[i]*scale);p.closePath();
            g.setColor(new Color(f.color));g.fill(p);
        }
        if(studio) {
            g.setColor(Theme.MUTED);g.setFont(Theme.font(Font.PLAIN,11));
            String label="DRAG TO ROTATE  ·  SCROLL TO ZOOM";
            g.drawString(label,(w-g.getFontMetrics().stringWidth(label))/2,h-20);
        }
        g.dispose();
    }
    private void box(ArrayList<Face> faces,double x,double y,double z,double w,double h,double d,int color) {
        double[][] points=new double[8][3];
        for(int i=0;i<8;i++) {double px=x+((i&1)==0?-w:w)/2,py=y+((i&2)==0?-h:h)/2,pz=z+((i&4)==0?-d:d)/2;
            double rx=px*Math.cos(yaw)+pz*Math.sin(yaw),rz=-px*Math.sin(yaw)+pz*Math.cos(yaw);
            points[i]=new double[]{rx,py*.98-rz*.20,rz*.98+py*.20};}
        int[][] indices={{0,2,3,1},{4,5,7,6},{0,4,6,2},{1,3,7,5},{2,6,7,3},{0,1,5,4}};
        double[] shade={.63,1,.74,.87,1.15,.52};
        for(int f=0;f<6;f++) {
            var ids=indices[f];double[] ax=new double[4],ay=new double[4];double depth=0;
            for(int j=0;j<4;j++){ax[j]=points[ids[j]][0];ay[j]=points[ids[j]][1];depth+=points[ids[j]][2]/4;}
            // Only outward faces visible from the camera; avoids overdraw between front/back faces.
            double cross=(ax[1]-ax[0])*(ay[2]-ay[0])-(ay[1]-ay[0])*(ax[2]-ax[0]);
            if(cross<=0)continue;
            int r=Math.min(255,(int)(((color>>16)&255)*shade[f])),gr=Math.min(255,(int)(((color>>8)&255)*shade[f])),b=Math.min(255,(int)((color&255)*shade[f]));
            faces.add(new Face(ax,ay,depth,(r<<16)|(gr<<8)|b));
        }
    }
}
