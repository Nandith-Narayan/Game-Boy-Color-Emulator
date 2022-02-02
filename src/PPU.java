import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;

public class PPU extends JFrame implements KeyListener {

    final boolean DISPLAY_DEBUG_TILES = false;
    final int H_BLANK=0, V_BLANK=1,OAM_SCAN=2,DRAWING=3;
    int ppuMode=V_BLANK, ppuModeOld;
    int drawStep = 1;
    int fetchedTileNum,internalXPos,pixelX;
    byte lowTileByte, highTileByte, BGPaletteToUse;
    int windowCounter = 0;
    boolean lyEqwy = false, inWindow = false,someSTATConditionTrue = false;
    int LY,LCDC;

    ArrayDeque<Pixel> pixelFIFO = new ArrayDeque<Pixel>();
    Pixel spriteFIFO[] = new Pixel[160];
    int spriteStart=0, spriteEnd=0;
    final int S_Y_POS=0,S_X_POS=1,S_TILE_NUM=2,S_FLAGS=3,S_DRAWN=4;
    int spriteBufferSize = 0, oamIndex=0, spriteToFetch=-1;
    int spriteBuffer[][] = new int[10][5];

    int totalCycles;
    int CYCLES_PER_FRAME = 70_224;
    int CYCLES_PER_LINE = 456;
    DebugDisplay disp;
    Graphics gImg;

    Color colors[];
    int palettes[][] = new int[2][4];
    int bgPalettes[] = new int[4];
    BufferedImage img,prev;
    boolean ready = false;
    long bt,at,frameTime;



    public PPU(){
        ppuMode = OAM_SCAN;
        totalCycles = 0;
        if(DISPLAY_DEBUG_TILES)
            disp = new DebugDisplay();

        colors = new Color[4];
        colors[0] = new Color(255,255,255);
        colors[1] = new Color(85*2,85*2,85*2);
        colors[2] = new Color(85,85,85);
        colors[3] = new Color(0,0,0);
        img = new BufferedImage(160,144, BufferedImage.TYPE_INT_ARGB);
        prev = new BufferedImage(160,144, BufferedImage.TYPE_INT_ARGB);
        gImg = img.getGraphics();
        gImg.setColor(Color.green);
        gImg.fillRect(0,0,160,144);

        this.setSize(160*2+30,144*2+45);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle("Game Boy Emulator :D");
        this.addKeyListener(this);
        this.setVisible(true);
    }

    @Override
    public void paint(Graphics g){
        g.setColor(Color.black);
        g.fillRect(0,0,15,this.getHeight());
        g.fillRect(160*2+15,0,15,this.getHeight());
        g.fillRect(0,287+32,this.getWidth(),15);
        g.setColor(Color.red);
        //g.drawString("FPS: "+(1_000_000_000.0/frameTime),20,320);
        if(ready)
        g.drawImage(prev,15,31,160*2,144*2,this);
        for(int i=0;i<8;i++) {
            for(int j=0;j<4;j++) {
                g.setColor(Memory.palettesScaledSprite[i][j]);
                g.fillRect(100+i*4,200+(j*4), 4,4);
                g.setColor(Memory.palettesScaled[i][j]);
                g.fillRect(100+i*4,216+(j*4), 4,4);
            }
        }

        repaint();
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void processCycles(int cyclesPassed){

        for(int i=0; i<cyclesPassed/2;i++){
            doCycle();
            totalCycles += 2;
            totalCycles %= CYCLES_PER_FRAME;
        }


        if(totalCycles%10_000 == 0 && DISPLAY_DEBUG_TILES)
            disp.run();
    }

    private void doCycle(){
        LY = Memory.read(0x0FF44)&0x0FF;
        LCDC = Memory.read(0x0FF40)&0x0FF;

        if(totalCycles%CYCLES_PER_LINE == 0){
            //System.out.println();

            spriteBufferSize=0;
            spriteToFetch = -1;

            // increment LY
            if(LY !=0 || totalCycles%CYCLES_PER_FRAME > 0)
            LY ++;
            if(LY==154){
                LY = 0;
            }
            Memory.write(0x0FF44, (byte) (LY));
            if(ppuMode!=V_BLANK)
                ppuMode = OAM_SCAN;

        }
        if(totalCycles%CYCLES_PER_FRAME == 0){
            at = System.nanoTime();
            frameTime = at-bt;
            /*if((frameTime/1_000_000) < 15){
                try {
                    Thread.sleep(15-(frameTime/1_000_000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
            bt = System.nanoTime();
            //Memory.write(0x0FF44, (byte) 0);
            ppuMode = OAM_SCAN;
        }

        if(totalCycles%CYCLES_PER_LINE < 80 && ppuMode!=V_BLANK && LY < 145){
            ppuMode = OAM_SCAN;
            //System.out.println(totalCycles%CYCLES_PER_LINE);
        }else if(totalCycles%CYCLES_PER_LINE == 80 && ppuMode!=V_BLANK && LY < 145){
            ppuMode = DRAWING;
        }else if(pixelX >= 160 && ppuMode!=V_BLANK && LY < 145 && ppuMode!=H_BLANK){
            //System.out.println(totalCycles%CYCLES_PER_LINE);

            pixelFIFO.clear();
            ppuMode = H_BLANK;

            if(inWindow){
                windowCounter++;
            }
        }
        if(totalCycles%CYCLES_PER_FRAME >= 65664){
            if(ppuMode!=V_BLANK) {
                //Memory.write(0x0FF44, (byte) 0);
                ready = false;
                byte IF = Memory.read(0x0FF0F);
                IF |= 0x01;
                Memory.write(0x0FF0F, IF);

                prev.getGraphics().drawImage(img, 0, 0, this);

                lyEqwy = false;
                inWindow = false;
                windowCounter = 0;
                ready = true;
            }

            ppuMode = V_BLANK;

        }

        int STAT = Memory.read(0x0FF41)&0x0FF;
        //if((STAT>>>4) != 4)
        //System.out.println(Integer.toHexString(STAT));
        int LYC = Memory.read(0x0FF45)&0x0FF;
        if(LY==LYC){
            STAT |= 0x04;


        }else{
            STAT &= 0xFB;
        }
        //System.out.println(Integer.toHexString(STAT));
        Memory.ppuMode = ppuMode;
        switch(ppuMode){
            case DRAWING:
                doDrawCycle();
                STAT |= 0x03;
                break;
            case H_BLANK:

                inWindow = false;
                //pixelX = 0;
                oamIndex = 0;
                drawStep=1;
                //internalXPos=0;
                STAT &= 0x0FC;
                break;
            case V_BLANK:
                spriteBufferSize = 0;
                spriteToFetch = -1;
                drawStep = 1;
                //internalXPos = 0;
                //Memory.write(0x0FF44, (byte) 0);
                if(pixelX!=0) {
                    //System.out.println(Memory.pc);
                }
                pixelX = 0;
                STAT &= 0x0FC;
                STAT |= 0x001;
                break;
            case OAM_SCAN:

                if(pixelFIFO.size()>0) {
                    pixelFIFO.clear();
                }
                if(spriteEnd>0) {
                    spriteStart = 0;
                    spriteEnd = 0;
                }
                internalXPos = 0;
                pixelX=0;
                int spriteY = Memory.oam[oamIndex+S_Y_POS]&0x0FF;
                int spriteX = Memory.oam[oamIndex+S_X_POS]&0x0FF;
                int spriteTile = Memory.oam[oamIndex+S_TILE_NUM]&0x0FF;
                int spriteFlag = Memory.oam[oamIndex+S_FLAGS]&0x0FF;
                boolean flipY = (spriteFlag & 0x00000040) != 0;

                int spriteHeight = 8;
                if((LCDC & 0x04) !=0){
                    spriteHeight = 16;
                }
                if(spriteBufferSize<10 && spriteX > 0 && (LY+16 >= spriteY) && (LY+16 < spriteY+spriteHeight)){
                    spriteBuffer[spriteBufferSize][S_Y_POS] = spriteY;
                    spriteBuffer[spriteBufferSize][S_X_POS] = spriteX;
                    if(spriteHeight == 16){
                        if((LY+16 < spriteY+8 && !flipY) || (LY+16 >= spriteY+8 && flipY)){
                            spriteTile&=0x000000FE;
                        }else{
                            spriteTile|=0x00000001;
                        }
                        spriteFlag &= 0x0BF;
                    }


                    spriteBuffer[spriteBufferSize][S_TILE_NUM] = spriteTile;
                    spriteBuffer[spriteBufferSize][S_FLAGS] = spriteFlag;
                    spriteBuffer[spriteBufferSize][S_DRAWN] = 0;
                    spriteBufferSize++;
                }


                oamIndex += 4;

                oamIndex %= 160;


                STAT &= 0x0FC;
                STAT |= 0x002;
                break;
        }

        //System.out.println(LY);
        //System.out.println(Integer.toBinaryString(STAT));
        boolean oldSTATCondition = someSTATConditionTrue;
        if((LY == LYC) && (STAT&0x040) != 0) {
            someSTATConditionTrue = true;
        }else if(ppuMode != ppuModeOld && ppuMode == OAM_SCAN && (STAT&0x020) != 0) {
            someSTATConditionTrue = true;
        }else if(ppuMode != ppuModeOld && ppuMode == V_BLANK && (STAT&0x010) != 0) {
            someSTATConditionTrue = true;
        }else if(ppuMode != ppuModeOld && ppuMode == H_BLANK && (STAT&0x008) != 0) {
            someSTATConditionTrue = true;
        }else{
            someSTATConditionTrue = false;
        }
        ppuModeOld = ppuMode;
        if(!oldSTATCondition && someSTATConditionTrue){
            byte IF = Memory.read(0x0FF0F);
            IF |= 0x02;
            Memory.write(0x0FF0F, IF);
        }



        Memory.write(0x0FF41, (byte)STAT);

    }

    private void doDrawCycle(){
        //int LYC = Memory.read(0x0FF45)&0x0FF;
        int WX = Memory.read(0x0FF4B)&0x0FF;
        int WY = Memory.read(0x0FF4A)&0x0FF;
        //int STAT = Memory.read(0x0FF41)&0x0FF;
        switch(drawStep){
            case 0: // Fetch Sprites

                boolean backgroundPriority = (spriteBuffer[spriteToFetch][S_FLAGS] & 0x00000080) == 0;
                boolean flipY = (spriteBuffer[spriteToFetch][S_FLAGS] & 0x00000040) != 0;
                boolean flipX = (spriteBuffer[spriteToFetch][S_FLAGS] & 0x00000020) != 0;

                int palletNum = (spriteBuffer[spriteToFetch][S_FLAGS] & 0x00000010) >>> 4;
                if(Memory.GBCMode){
                    palletNum = (spriteBuffer[spriteToFetch][S_FLAGS] & 0x00000007);
                    int vramBank = (spriteBuffer[spriteToFetch][S_FLAGS] & 0x00000008)>>3;

                    System.out.println(vramBank);
                }
                fetchSpriteTileBytes(spriteBuffer[spriteToFetch][S_TILE_NUM], flipY, spriteBuffer[spriteToFetch][S_Y_POS]);
                if(!flipX){
                    for (int j = 0; j < 8; j++) {
                        int color = ((lowTileByte & 0x00000080) >>> 7) | ((highTileByte & 0x00000080) >>> 6);
                        lowTileByte <<= 1;
                        highTileByte <<= 1;
                        if((spriteEnd-spriteStart)<=j) {
                            spriteFIFO[spriteEnd] = new Pixel(color, palletNum, backgroundPriority);
                            spriteEnd++;
                        }else{
                            /*if((spriteFIFO[(spriteEnd/8)*8+j].color==0 || ((spriteEnd/8)*8+j) >= spriteEnd ) && color != 0) {
                                spriteFIFO[(spriteEnd/8)*8+j] = new Pixel(color, palletNum, backgroundPriority);
                            }*/
                        }
                    }
                }else{
                    for (int j = 0; j < 8; j++) {
                        int color = ((lowTileByte & 0x00000001)) | (((highTileByte & 0x00000001)<<1));
                        lowTileByte >>>= 1;
                        highTileByte >>>= 1;
                        if((spriteEnd-spriteStart)<=j) {
                            spriteFIFO[spriteEnd] =new Pixel(color, palletNum, backgroundPriority);
                            spriteEnd++;
                        }else{
                            /*if((spriteFIFO[(spriteEnd/8)*8+j].color==0 || ((spriteEnd/8)*8+j) >= spriteEnd ) && color != 0) {
                                spriteFIFO[(spriteEnd/8)*8+j] = new Pixel(color, palletNum, backgroundPriority);
                            }*/
                        }
                    }
                }
                drawStep = 1;

            case 1: // Fetch Tile Number
                // Don't fetch if 160 pixels have already been fetched.
                if(pixelX+pixelFIFO.size() >= 160){
                    break;
                }
                // set tile map base address
                int address = 0;
                if(!inWindow) {

                    // BG select bit (bit 3)
                    if ((LCDC & 0x00000008) == 0) {
                        address = 0x09800;
                    } else {
                        address = 0x09C00;
                    }
                }else{
                    // Window Tiles select bit (bit 6)
                    if ((LCDC & 0x00000040) == 0) {
                        address = 0x09800;
                    } else {
                        address = 0x09C00;
                    }
                }
                int scx = Memory.read(0x0FF43)&0x0FF;
                //scx -=7;
                int offsetX = internalXPos + (inWindow?0:(scx/8));
                offsetX &= 0x0000001F;
                int offsetY;
                if(!inWindow) {
                    int SCY = Memory.read(0x0FF42) & 0x0FF;
                    offsetY = 32 * (((LY + SCY) & 0x0FF) / 8);
                }else{
                    offsetY = 32*(windowCounter/8);
                }

                address += ((offsetX + offsetY) & 0x000003FF);
                // fetch the tile to be rendered
                fetchedTileNum = Memory.vram[address-0x08000] & 0x000000FF;
                if(Memory.GBCMode){
                    BGPaletteToUse = Memory.vram[(address-0x08000)+(0x02000)];
                }


                drawStep = 2;
                break;
            case 2: // Fetch Low Byte of Tile Data
                if(inWindow)
                    fetchWindowTileLowByte(fetchedTileNum);
                else
                    fetchTileLowByte(fetchedTileNum);
                drawStep = 3;
                break;
            case 3: // Fetch High Byte of Tile Data
                if(inWindow)
                    fetchWindowTileHighByte(fetchedTileNum);
                else
                    fetchTileHighByte(fetchedTileNum);
                drawStep = 4;
                break;
            case 4: // Push the 8 Background Pixels to the FIFO

                if(pixelFIFO.size() == 0){

                    for (int j = 0; j < 8; j++) {
                        int color = ((lowTileByte & 0x00000080) >>> 7) | ((highTileByte & 0x00000080) >>> 6);
                        lowTileByte <<= 1;
                        highTileByte <<= 1;
                        if((LCDC &0x00000001) == 0) {
                            color = 0;
                        }
                        pixelFIFO.add(new Pixel(color, BGPaletteToUse&0x07));


                    }

                    internalXPos += 1;
                    drawStep = 1;
                    int scx1 = Memory.read(0x0FF43)&0x0FF;

                    // Shift out scx % 8 pixels from the start of the scan line
                    if(pixelX==0){
                        if(pixelFIFO.size() >0){
                            for(int i=0;i<scx1%8;i++){
                                if(pixelFIFO.size()>0){
                                    pixelFIFO.pop();
                                }
                            }
                        }
                    }


                }else {
                    drawStep = 4;
                }
                break;
        }

        // draw pixel (if any)
        for(int pixelIndex=0;pixelIndex<2;pixelIndex++) {
            if (pixelFIFO.size() > 0) {

                updatePalettes();
                // If spriteFIFO is empty, push background pixel to screen
                if ((spriteEnd - spriteStart) == 0) {
                    Pixel p = pixelFIFO.pop();
                    if (Memory.GBCMode) {
                        gImg.setColor(Memory.palettesScaled[p.palette][p.color]);
                    } else {
                        gImg.setColor(colors[bgPalettes[p.color]]);
                    }

                    gImg.fillRect(pixelX++, LY, 1, 1);
                    // If spriteFIFO is not empty, mix pixels
                } else {
                    Pixel bgPix = pixelFIFO.pop();
                    Pixel spPix = spriteFIFO[spriteStart];
                    spriteStart++;

                    if ((LCDC & 0x002) == 0) {
                        if (Memory.GBCMode) {
                            gImg.setColor(Memory.palettesScaled[bgPix.palette][bgPix.color]);
                        } else {
                            gImg.setColor(colors[bgPalettes[bgPix.color]]);
                        }
                    } else if (spPix.color == 0) {
                        if (Memory.GBCMode) {
                            gImg.setColor(Memory.palettesScaled[bgPix.palette][bgPix.color]);
                        } else {
                            gImg.setColor(colors[bgPalettes[bgPix.color]]);
                        }
                    } else if ((!spPix.backgroundPriority) && bgPix.color != 0) {
                        if (Memory.GBCMode) {
                            gImg.setColor(Memory.palettesScaled[bgPix.palette][bgPix.color]);
                        } else {
                            gImg.setColor(colors[bgPalettes[bgPix.color]]);
                        }
                    } else {
                        if (Memory.GBCMode) {
                            gImg.setColor(Memory.palettesScaledSprite[spPix.palette][spPix.color]);
                        } else {
                            gImg.setColor(colors[palettes[spPix.palette][spPix.color]]);
                        }
                    }


                    gImg.fillRect(pixelX++, LY, 1, 1);
                }
                //System.out.println(pixelX);
            }
        }
        if(LY == WY){
            lyEqwy = true;
        }

        // Check if within window
        if(!inWindow && ((LCDC&0x020) != 0 && lyEqwy && pixelX >= (WX-7))){
            inWindow = true;
            internalXPos = 0;
            pixelFIFO.clear();
            drawStep=1;
        }else if(!((LCDC&0x020) != 0 && lyEqwy && pixelX >= (WX-7))){
            inWindow=false;
        }

        int minX = 2550;
        for(int sp=spriteBufferSize-1;sp>=0;sp--){
            if(spriteBuffer[sp][S_X_POS] <= pixelX+8 && spriteBuffer[sp][S_DRAWN] == 0){


                if(spriteBuffer[sp][S_X_POS]<= minX) {
                    spriteBuffer[sp][S_DRAWN] = 1;
                    minX = spriteBuffer[sp][S_X_POS];
                    spriteToFetch = sp;
                    drawStep = 0;
                    break;
                }

                //break;
            }

        }

    }

    public void updatePalettes(){
        int OBP0 = Memory.read(0x0FF48)&0x0FF;
        int OBP1 = Memory.read(0x0FF49)&0x0FF;
        int BGP = Memory.read(0x0FF47)&0x0FF;
        //palettes[0][0] = (OBP0 & 0x003);
        palettes[0][1] = (OBP0 & 0x00C) >>> 2;
        palettes[0][2] = (OBP0 & 0x030) >>> 4;
        palettes[0][3] = (OBP0 & 0x0C0) >>> 6;
        //palettes[1][0] = (OBP1 & 0x003);
        palettes[1][1] = (OBP1 & 0x00C) >>> 2;
        palettes[1][2] = (OBP1 & 0x030) >>> 4;
        palettes[1][3] = (OBP1 & 0x0C0) >>> 6;
        bgPalettes[0] = (BGP & 0x003);
        bgPalettes[1] = (BGP & 0x00C) >>> 2;
        bgPalettes[2] = (BGP & 0x030) >>> 4;
        bgPalettes[3] = (BGP & 0x0C0) >>> 6;

    }
    public void fetchTileLowByte(int tileNum){
        int tileAddressingMode = LCDC&0x00000010;
        int address;
        if(tileAddressingMode == 0){
            address = 0x01000 + (((byte)tileNum)*16);
        }else{
            address = 0x00000 + ((tileNum & 0x000000FF) * 16);
        }
        int SCY = Memory.read(0x0FF42)&0x0FF;
        int offset = 2 * ((LY + SCY) % 8);

        lowTileByte = Memory.vram[(address+offset)+(Memory.vramBank*0x02000)];

    }
    public void fetchTileHighByte(int tileNum){
        int tileAddressingMode = LCDC&0x00000010;
        int address;
        if(tileAddressingMode == 0){
            address = 0x01000 + (((byte)tileNum)*16);

        }else{
            address = 0x00000 + ((tileNum & 0x000000FF) * 16);
        }
        int SCY = Memory.read(0x0FF42)&0x0FF;
        int offset = 2 * ((LY + SCY) % 8);

        highTileByte = Memory.vram[(address+offset+1)+(Memory.vramBank*0x02000)];

    }
    public void fetchWindowTileLowByte(int tileNum){
        int tileAddressingMode = LCDC&0x00000010;
        int address;
        if(tileAddressingMode == 0){
            address = 0x01000 + (((byte)tileNum)*16);
        }else{
            address = 0x00000 + ((tileNum & 0x0000FF) * 16);
        }

        int offset = 2*(windowCounter%8);


        lowTileByte = Memory.vram[(address+offset)+(Memory.vramBank*0x02000)];

    }
    public void fetchWindowTileHighByte(int tileNum){
        int tileAddressingMode = LCDC&0x00000010;
        int address;
        if(tileAddressingMode == 0){
            address = 0x01000 + (((byte)tileNum)*16);
        }else{
            address = 0x00000 + ((tileNum & 0x0000FF) * 16);
        }
        int offset = 2*(windowCounter%8);

        highTileByte = Memory.vram[(address+offset+1)+(Memory.vramBank*0x02000)];


    }
    public void fetchSpriteTileBytes(int tileNum, boolean flipY, int spriteY){
        int address = 0x00000 + ((tileNum & 0x0000FF) * 16);

        int SCY = Memory.read(0x0FF42)&0x0FF;

        int offset =0;
        if(!flipY) {
            offset = 2 * ((LY+16-spriteY) % 8);
        }else{
            offset = 2 * (7-((LY+16-spriteY)% 8));
        }

        lowTileByte = Memory.vram[(address+offset)+(Memory.vramBank*0x02000)];
        highTileByte = Memory.vram[(address+offset+1)+(Memory.vramBank*0x02000)];

    }



    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if(e.getKeyCode()==KeyEvent.VK_RIGHT){
            Memory.rightPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_LEFT){
            Memory.leftPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_UP){
            Memory.upPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_DOWN){
            Memory.downPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_A){
            Memory.aPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_S){
            Memory.bPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_SPACE){
            Memory.startPressed = true;
        }
        if(e.getKeyCode()==KeyEvent.VK_DOWN){
            Memory.downPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if(e.getKeyCode()==KeyEvent.VK_RIGHT){
            Memory.rightPressed = false;
        }
        if(e.getKeyCode()==KeyEvent.VK_LEFT){
            Memory.leftPressed = false;
        }
        if(e.getKeyCode()==KeyEvent.VK_UP){
            Memory.upPressed = false;
        }
        if(e.getKeyCode()==KeyEvent.VK_DOWN){
            Memory.downPressed = false;
        }
        if(e.getKeyCode()==KeyEvent.VK_A){
            Memory.aPressed = false;
        }
        if(e.getKeyCode()==KeyEvent.VK_S){
            Memory.bPressed = false;
        }
        if(e.getKeyCode()==KeyEvent.VK_SPACE){
            Memory.startPressed = false;
        }
    }
}
