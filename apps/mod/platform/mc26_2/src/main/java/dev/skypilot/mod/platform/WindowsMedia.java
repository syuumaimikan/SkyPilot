package dev.skypilot.mod.platform;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.skypilot.mod.core.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bundled WinRT bridge. Polling and image decoding never block the render thread. */
final class WindowsMedia {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SkyPilot media");t.setDaemon(true);return t;});
    private static final Identifier ART=Identifier.fromNamespaceAndPath("skypilot","media_cover");
    private static volatile Process process;
    private static BufferedReader reader; private static BufferedWriter writer;
    private static volatile boolean busy;
    private static final java.util.concurrent.ArrayBlockingQueue<String> COMMANDS=new java.util.concurrent.ArrayBlockingQueue<>(8);
    private static long nextPoll; private static String title="",artist="",status="",artKey="";
    private static int artW,artH; private static boolean hasArt;
    private static String controlMessage="";
    private static boolean canPlay,canPause,canToggle,canNext,canPrevious;
    static String title(){return title;}
    static String artist(){return artist;}
    static String status(){return status;}
    static String controlMessage(){return controlMessage;}
    static boolean supports(String action){return switch(action){case "play"->canPlay;case "pause"->canPause;case "toggle"->canToggle;case "next"->canNext;case "previous"->canPrevious;default->false;};}
    static void tick(){if(!Feat.on(Features.MEDIA)){stop();return;}if(System.currentTimeMillis()>=nextPoll){nextPoll=System.currentTimeMillis()+2000;command("poll");}}
    static void command(String action){
        if(!Set.of("poll","toggle","play","pause","next","previous").contains(action))return;
        if(!Feat.on(Features.MEDIA))return;
        if(busy){if(!action.equals("poll"))COMMANDS.offer(action);return;}
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")){status="Windows only";return;}
        busy=true;
        WORKER.execute(()->{try{
            if(process==null||!process.isAlive())start();
            writer.write(action);writer.newLine();writer.flush();
            var watchdog=CompletableFuture.delayedExecutor(8,TimeUnit.SECONDS);
            Process active=process;var completed=new java.util.concurrent.atomic.AtomicBoolean();
            watchdog.execute(()->{if(!completed.get())active.destroy();});
            String line;try{line=reader.readLine();}finally{completed.set(true);}
            if(line==null||line.length()>3_000_000)throw new IOException("Media bridge closed");
            var json=JsonParser.parseString(line).getAsJsonObject();
            Minecraft.getInstance().execute(()->{
                canPlay=json.has("canPlay")&&json.get("canPlay").getAsBoolean();
                canPause=json.has("canPause")&&json.get("canPause").getAsBoolean();
                canToggle=json.has("canToggle")&&json.get("canToggle").getAsBoolean();
                canNext=json.has("canNext")&&json.get("canNext").getAsBoolean();
                canPrevious=json.has("canPrevious")&&json.get("canPrevious").getAsBoolean();
                if(json.has("accepted")&&!json.get("accepted").isJsonNull())controlMessage=json.get("accepted").getAsBoolean()?SkyPilot.get().lang().t("Command accepted","操作を受け付けました"):SkyPilot.get().lang().t("The media app declined this command","再生アプリが操作を受け付けませんでした");
            });
            if(json.has("error")&&!json.get("error").getAsString().isEmpty())SkyPilot.LOG.debug("Windows media provider: {}",json.get("error").getAsString());
            String t=json.has("title")?json.get("title").getAsString():"",a=json.has("artist")?json.get("artist").getAsString():"",s=json.has("status")?json.get("status").getAsString():"";
            String cover=json.has("art")?json.get("art").getAsString():"";byte[] png=null;
            if(!cover.isEmpty()&&!cover.equals(artKey)){
                var original=javax.imageio.ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(cover)));
                if(original!=null){var thumb=new java.awt.image.BufferedImage(128,128,java.awt.image.BufferedImage.TYPE_INT_ARGB);var graphics=thumb.createGraphics();graphics.drawImage(original,0,0,128,128,null);graphics.dispose();var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(thumb,"png",out);png=out.toByteArray();}
            }
            byte[] image=png;
            Minecraft.getInstance().execute(()->{title=t;artist=a;status=s;if(!cover.equals(artKey)){artKey=cover;hasArt=false;Minecraft.getInstance().getTextureManager().release(ART);if(image!=null)try{var img=NativeImage.read(new ByteArrayInputStream(image));artW=img.getWidth();artH=img.getHeight();Minecraft.getInstance().getTextureManager().register(ART,new DynamicTexture(()->"SkyPilot media cover",img));hasArt=true;}catch(IOException ignored){}}});
        }catch(Exception e){SkyPilot.LOG.debug("Windows media unavailable",e);Minecraft.getInstance().execute(()->{title="";artist="";status="Media unavailable";hasArt=false;});stop();nextPoll=System.currentTimeMillis()+15000;}finally{busy=false;String queued=COMMANDS.poll();if(queued!=null)Minecraft.getInstance().execute(()->command(queued));}});
    }
    private static void start()throws Exception{
        try(var in=WindowsMedia.class.getResourceAsStream("/native/windows/SkyPilot.Media.exe")){
            if(in==null)throw new IOException("Windows bridge absent");byte[] bytes=in.readAllBytes();
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            Path dir=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".skypilot-tools").resolve(hash);Files.createDirectories(dir);Path exe=dir.resolve("SkyPilot.Media.exe");
            if(!Files.exists(exe)||!java.security.MessageDigest.isEqual(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(exe)),java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))Files.write(exe,bytes);
            process=new ProcessBuilder(exe.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
        }
    }
    static void stop(){var p=process;if(p!=null){p.destroy();process=null;}}
    static void render(GuiGraphicsExtractor g){
        if(!Feat.on(Features.MEDIA))return;var mc=Minecraft.getInstance();boolean preview=mc.gui.screen() instanceof HudEditorScreen;
        if(mc.player==null||mc.gui.screen()!=null&&!preview)return;
        int x=8,y=g.guiHeight()-88;UiTransforms.begin(g,"media",x,y);
        g.fill(x,y,x+200,y+42,0xD010182B);if(hasArt)g.blit(RenderPipelines.GUI_TEXTURED,ART,x+3,y+3,0f,0f,36,36,artW,artH,artW,artH);
        g.text(mc.font,mc.font.plainSubstrByWidth(title.isEmpty()?SkyPilot.get().lang().t("No media playing","再生中メディアなし"):title,150),x+44,y+4,Ui.WHITE,true);
        g.text(mc.font,mc.font.plainSubstrByWidth(artist,150),x+44,y+16,Ui.GRAY,true);g.text(mc.font,status,x+44,y+28,Ui.PURPLE,true);UiTransforms.end(g);
    }
}
