import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

public class Memory {
    static byte bootRom[];
    static byte rom[];
    static byte ramExt[];
    public static byte vram[];
    static byte wram[];
    public static byte oam[];
    static byte ioreg[];
    static byte hram[];
    static byte iReg =0x00;

    static int mbcType;
    final static int NO_MBC=0,MBC_1=1,MBC_2=2,MBC_3=3,MBC_4=4,MBC_5=5;
    static int romBank=1, maxRomBanks;
    static int ramExtBank=1;
    static int wramBank=1;
    static int vramBank=0;
    static boolean ramExtEnable = false, timerEnabled;
    static int memModeSelect;
    final static int ROM_MODE = 0, RAM_MODE = 1;

    public static boolean aPressed,bPressed, startPressed, selectPressed, upPressed, downPressed, rightPressed, leftPressed;

    public static int timer, timerDenom, cycles, div;
    public static int ppuMode;
    public static boolean GBCMode = false, useBootRom=!true;
    public static Color palettes[][] = new Color[8][4];
    public static Color palettesSprite[][] = new Color[8][4];
    public static Color palettesScaled[][] = new Color[8][4];
    public static Color palettesScaledSprite[][] = new Color[8][4];
    public static int colorPaletteIndex, colorPaletteIndexSprite;
    public static boolean colorPaletteAutoIncrement = false, colorPaletteAutoIncrementSprite = false;
    static int HDMASrc, HDMADest;
    static byte tmp;
    static int pc;

    public static void initMemory(){
        // Load ROM file
        try {
            byte romData[];
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/cpu_instrs.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/01-special.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/02-interrupts.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/03-op sp,hl.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/04-op r,imm.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/06-ld r,r.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/07-jr,jp,call,ret,rst.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/09-op r,r.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/11-op a,(hl).gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Dr. Mario (World).gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/dmg-acid2.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/cgb-acid2.gbc"));
            romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Tetris (World) (Rev A).gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/daa.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Legend of Zelda, The - Link's Awakening (USA, Europe) (Rev A).gb"));
            romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/bergentruckung.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/dmg_sound.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/cgb_agb_boot.bin"));

            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/01-registers.gb"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb"));
            romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (GBC,SGB Enhanced).gb"));
            //romData = Files.readAllBytes(Paths.get("D:/yellow/pokeyellow/pokeyellow.gbc"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Pokemon - Gold Version (USA, Europe) (SGB Enhanced).gbc"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Super Mario Bros. Deluxe (USA, Europe).gbc"));
            //romData = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/Mario & Yoshi (Europe).gb"));
            //romData = Files.readAllBytes(Paths.get("C:\\Users\\nandi\\Downloads\\mts-20211031-1956-1ae00bf\\mts-20211031-1956-1ae00bf\\misc\\ppu\\vblank_stat_intr-C.gb"));

            //romData = Files.readAllBytes(Paths.get("C:\\Users\\nandi\\Downloads\\mts-20211031-1956-1ae00bf\\mts-20211031-1956-1ae00bf\\emulator-only\\mbc5\\rom_1Mb.gb"));
            //romData = Files.readAllBytes(Paths.get("C:\\Users\\nandi\\Downloads\\mts-20211031-1956-1ae00bf\\mts-20211031-1956-1ae00bf\\acceptance\\intr_timing.gb"));
            padRom(romData);
            bootRom = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/cgb_boot.bin"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*try {
            byte bios[] = Files.readAllBytes(Paths.get("D:/GameBoyEmu/roms/bootix_dmg.bin"));
            for(int i=0;i<256;i++){
                rom[i]=bios[i];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }*/


        System.out.println("ROM SIZE: "+rom.length);
        bootRom = new byte[256];
        ramExt = new byte[8192*4];
        vram = new byte[8192*2*2];
        wram = new byte[4096*8];
        oam = new byte[160];
        ioreg = new byte[128];
        hram = new byte[127];
        timerDenom = 1024;
        timer = 0;

        for(int i=0;i<palettes.length;i++){
            palettes[i][0] = new Color(0x01F,0x01F,0x01F);
            palettes[i][1] = Color.BLACK;
            palettes[i][2] = Color.BLACK;
            palettes[i][3] = Color.BLACK;
            palettesScaled[i][0] = Color.white;
            palettesScaled[i][1] = Color.BLACK;
            palettesScaled[i][2] = Color.BLACK;
            palettesScaled[i][3] = Color.BLACK;
            palettesSprite[i][0] = new Color(0x01F,0x01F,0x01F);
            palettesSprite[i][1] = Color.BLACK;
            palettesSprite[i][2] = Color.BLACK;
            palettesSprite[i][3] = Color.BLACK;
            palettesScaledSprite[i][0] = Color.white;
            palettesScaledSprite[i][1] = Color.BLACK;
            palettesScaledSprite[i][2] = Color.BLACK;
            palettesScaledSprite[i][3] = Color.BLACK;
        }
        setMBCType();
        setRomBankType();
        detectGBCmode();
        System.out.println(mbcType);
        //write(0x0000FF44, (byte) 0x00000090);

    }


    public static byte read(int address){
        address &= 0x0000FFFF;
        if(useBootRom && address < bootRom.length){
            return bootRom[address];
        }
        if(address < 0x08000){
            switch(mbcType){
                case NO_MBC:
                case MBC_1:
                    return readMBC1(address);
                case MBC_3:
                    return readMBC3(address);
                case MBC_5:
                    return readMBC5(address);
                default:
                    System.err.println("ERROR Unsupported MBC type: "+mbcType);
                    return -42;
            }
        }

        // 8000 - 9FFF - VRAM
        if(address < 40960) {
            /*if(ppuMode==3){
                return (byte) 0xFF;
                //System.out.println("EEE");
            }*/
            return vram[(address - 32768)+(vramBank*0x02000)];
        }
        // Reading from external RAM A000-BFFF - Banked RAM section
        if(address < 49152) {
            return ramExt[(address - 40960) + (ramExtBank * (8192))];

        }
        // Reading from Working WRAM C000-CFFF
        if(address < 53248)
            return wram[address-49152];
        // Reading from Working WRAM D000-DFFF - Banked WRAM section
        if(address < 57344)
            return wram[(address-53248)+(wramBank * (4096))];
        // Echo RAM
        if(address < 65024)
            return readMBC1(address-8192);
        // OAM
        if(address < 65184) {
            return oam[address - 65024];
        }
        // FEA0	FEFF - Not Usable
        if(address < 65280){
            return 0;
        }
        // FF04 DIV
        if(address == 0x0FF04){
            return (byte)(div >>> 10);
        }
        if(address == 0x0FF05){
            return (byte)timer;
        }
        if(address == 0x0FF51){
            return (byte)((HDMASrc&0x0FF00)>>>8);
        }
        if(address == 0x0FF52){
            return (byte)(HDMASrc&0x0FF);
        }
        if(address == 0x0FF53){
            return (byte)((HDMADest&0x0FF00)>>>8);
        }
        if(address == 0x0FF54){
            return (byte)(HDMADest&0x0FF);
        }
        // IO registers
        if(address < 65408)
            return ioreg[address-65280];
        // HRAM
        if(address < 65535)
            return hram[address-65408];
        if(address == 65535)
            return iReg;


        System.out.println(address);
        return -42;
    }

    public static byte readMBC1(int address){
        address &= 0x0000FFFF;
        // 0000-3FFF - First part of ROM
        if(address < 16384)
            return rom[address];
        // 4000-7FFF - Banked ROM section
        if(address < 32768)
            return rom[(address-16384)+((romBank%maxRomBanks)*(16384))];

        System.err.println("ERROR!" + Integer.toHexString(address));
        return -42;
    }
    public static byte readMBC3(int address){
        address &= 0x0000FFFF;
        // 0000-3FFF - First part of ROM
        if(address < 0x04000)
            return rom[address];
        // 4000-7FFF - Banked ROM section
        if(address < 0x08000) {

            return rom[(address - 16384) + ((romBank % maxRomBanks) * (16384))];

                /*LocalDateTime dt =LocalDateTime.now();
                switch((romBank % maxRomBanks)){
                    case 0x08:
                        return (byte)dt.getSecond();
                    case 0x09:
                        return (byte)dt.getMinute();
                    case 0x0A:
                        return (byte)dt.getHour();
                    case 0x0B:
                        return (byte)(dt.getDayOfYear()&0x0FF);
                    case 0x0C:
                        return (byte)((dt.getDayOfYear()&0x0100)>>>8);
                    default:
                        break;
                }*/

        }

        System.err.println("ERROR!" + Integer.toHexString(address));
        return -42;
    }
    public static byte readMBC5(int address){
        address &= 0x0000FFFF;
        // 0000-3FFF - First part of ROM
        if(address < 0x04000)
            return rom[address];
        // 4000-7FFF - Banked ROM section
        if(address < 0x08000) {
            return rom[(address - 16384) + ((romBank%maxRomBanks) * (16384))];
        }

        System.err.println("ERROR!");
        return -42;
    }
    public static void write(int address, byte val){
        if(useBootRom && address == 0x0FF50){
            useBootRom = false;
            System.out.println("E");
            return;
        }
        if(address < 0x08000){
            switch(mbcType){
                case NO_MBC:
                case MBC_1:
                    writeMBC1(address, val);
                    return;
                case MBC_3:
                    writeMBC3(address, val);
                    return;
                case MBC_5:
                    writeMBC5(address, val);
                    return;
                default:
                    System.err.println("ERROR Unsupported MBC type: "+mbcType);
                    return;
            }
        }

        // Writing to VRAM 8000 - 9FFF
        if(address < 40960) {
;

            vram[(address - 32768)+(vramBank*0x02000)] = val;
            return;
        }
        // Writing to External RAM A000-BFFF - Banked RAM section
        if(address < 49152) {
            ramExt[(address-40960)+(ramExtBank * (8192))] = val;
            return;
        }
        // Writing to Working WRAM C000-CFFF
        if(address < 53248) {
            wram[address-49152] = val;
            return;
        }
        // Writing to Working WRAM D000-DFFF - Banked WRAM section
        if(address < 57344) {
            wram[(address-53248)+(wramBank * (4096))] = val;

            return;
        }
        // Echo RAM
        if(address < 65024) {
            writeMBC1(address - 8192, val);
            return;
        }
        // OAM
        if(address < 65184) {
            oam[address - 65024] = val;
            return;
        }
        // FEA0 - FEFF Unusable
        if(address < 65280){
            return;
        }
        // JOYPAD input
        if(address == 0x0FF00){
            if((val&0x010) == 0){
                // Select Directional Input
                ioreg[0] |= 0x00F;
                if(downPressed){
                    ioreg[0] &= 0x0F7;
                }
                if(upPressed){
                    ioreg[0] &= 0x0FB;
                }
                if(leftPressed){
                    ioreg[0] &= 0x0FD;
                }
                if(rightPressed){
                    ioreg[0] &= 0x0FE;
                }
            }else if((val&0x020) == 0){
                // Select Button Input
                ioreg[0] |= 0x00F;
                if(startPressed){
                    ioreg[0] &= 0x0F7;
                }
                if(selectPressed){
                    ioreg[0] &= 0x0FB;
                }
                if(bPressed){
                    ioreg[0] &= 0x0FD;
                }
                if(aPressed){
                    ioreg[0] &= 0x0FE;
                }
            }else{
                ioreg[0] |= 0x00F;
            }
            ioreg[0] = (byte) ((ioreg[0] & 0x00F)| (val & 0x0F0));
            return;
        }
        if(address == 0x0FF04){
            div = 0;
            return;
        }
        // FF07 TAC
        if(address == 0x0FF07){
            timerEnabled = (val&0x04) != 0;
            int previousDenom = timerDenom;
            switch((val & 0x03)){
                case 0:
                    timerDenom = 1024;
                    break;
                case 1:
                    timerDenom = 16;
                    break;
                case 2:
                    timerDenom = 64;
                    break;
                case 3:
                    timerDenom = 256;
                    break;
            }
            if(previousDenom != timerDenom){
                cycles = 0;
            }
        }

        if(address == APU.NR14){
            APU.ch1LengthTimer =  64 - (val&0x03F);
        }
        if(address == APU.NR24){
            APU.ch2LengthTimer =  64 - (val&0x03F);
        }
        if(address == APU.NR34){
            APU.ch3LengthTimer =  256 - (val&0x0FF);
        }
        if(address == APU.NR44){
            APU.ch4LengthTimer =  64 - (val&0x03F);
        }

        if(address == APU.NR14){
            if((val&0x080) != 0){
                ioreg[address - 65280] = val;
                APU.triggerCh1EnvelopeEvent();
            }
        }

        if(address == APU.NR24){
            if((val&0x080) != 0){
                ioreg[address - 65280] = val;
                APU.triggerCh2EnvelopeEvent();
            }
        }

        if(address == APU.NR34){
            if((val&0x080) != 0){
                ioreg[address - 65280] = val;
                APU.triggerCh3Event();
            }
        }
        if(address == APU.NR44){
            if((val&0x080) != 0){
                ioreg[address - 65280] = val;
                APU.triggerCh4EnvelopeEvent();
            }
        }
        // FF04 DIV
        if(address == 0x0FF04){
            div = 0;
            return;
        }

        // FF46 - Initiate DMA transfer
        if(address == 0x0FF46){
            int dmaAddress = ((val&0x0FF) << 8) & 0x0FFFF;
            //System.out.println(dmaAddress);
            for(int i=0;i<160;i++){
                oam[i] = Memory.read(dmaAddress+i);
            }
            return;
        }

        if(address == 0x0FF4C && GBCMode){
            System.out.println(val);
        }
        //FF4D - prepare speed change
        if(address == 0x0FF4D && GBCMode){
            System.out.println(val);
        }

        //FF4F - swap VRAM Bank
        if(address == 0x0FF4F && GBCMode){
            vramBank=val&0x001;
        }

        //FF51 - HDMA source high byte
        if(address == 0x0FF51 && GBCMode){
            HDMASrc &= 0x0000FF;
            HDMASrc |= val << 8;
        }

        //FF52 - HDMA source low byte
        if(address == 0x0FF52 && GBCMode){
            HDMASrc &= 0x00FF00;
            HDMASrc |= val&0x0F0;
        }

        //FF53 - HDMA destination high byte
        if(address == 0x0FF53 && GBCMode){
            HDMADest &= 0x0000FF;
            HDMADest |= val << 8;
            HDMADest &= 0x01FF0;
        }

        //FF54 - HDMA destination low byte
        if(address == 0x0FF54 && GBCMode){
            HDMADest &= 0x00FF00;
            HDMADest |= val&0x0F0;
        }

        //FF55 - HDMA start
        if(address == 0x0FF55 && GBCMode){

            int lenHDMA = ((val&0x07F)+1)*0x010;
            //System.out.println("HDMA transfer SRC: "+Integer.toHexString(HDMASrc) +" DEST: "+Integer.toHexString(HDMADest)+" Length: "+lenHDMA);
            for(int i=0;i<lenHDMA;i++){
                vram[HDMADest+(vramBank*0x02000)] = Memory.read(HDMASrc);
                HDMADest++;
                HDMASrc++;
            }

            val = (byte) 0x0FF;
        }


        // FF68 - Background Palette Index
        if(address == 0x0FF68){
            colorPaletteIndex = val&0x03F;
            colorPaletteAutoIncrement = (val&0x080) !=0;
        }
        // FF69 - Background Palette Data
        if(address == 0x0FF69){
            //Update color
            Color c = palettes[colorPaletteIndex/8][(colorPaletteIndex%8)/2];
            Color newColor = null;
            switch(colorPaletteIndex%2){
                case 0:
                    newColor = new Color(val&0x01F,(c.getGreen()&0x0F8)|((val&0x0E0)>>>5),c.getBlue());
                    break;
                case 1:
                    newColor = new Color(c.getRed(),(c.getGreen()&0x007)|((val&0x003)<<3),(val&0x07C)>>>2);
                    break;
            }
            palettes[colorPaletteIndex/8][(colorPaletteIndex%8)/2] = newColor;
            int r = newColor.getRed();
            int g = newColor.getGreen();
            int b = newColor.getBlue();

            palettesScaled[colorPaletteIndex/8][(colorPaletteIndex%8)/2] = new Color((r<<3)|(r>>>2),(g<<3)|(g>>>2),(b<<3)|(b>>>2));
            //System.out.println(colorPaletteIndex/8+" "+(colorPaletteIndex%8)/2+" "+newColor.getRed()+" "+newColor.getGreen()+" "+newColor.getBlue());
            if(colorPaletteAutoIncrement){
                colorPaletteIndex = (colorPaletteIndex+1)%64;
                // update palette index register (0xFF68)
                ioreg[0x0FF68 - 65280] = (byte) ((ioreg[0x0FF68 - 65280]&0x080) + (colorPaletteIndex&0x03F));
            }
        }
        // FF6A - Sprite Palette Index
        if(address == 0x0FF6A){
            colorPaletteIndexSprite = val&0x03F;
            colorPaletteAutoIncrementSprite = (val&0x080) !=0;
        }
        // FF6B - Sprite Palette Data
        if(address == 0x0FF6B){
            //Update color
            Color c = palettesSprite[colorPaletteIndexSprite/8][(colorPaletteIndexSprite%8)/2];
            Color newColor = null;
            switch(colorPaletteIndexSprite%2){
                case 0:
                    newColor = new Color(val&0x01F,(c.getGreen()&0x0F8)|((val&0x0E0)>>>5),c.getBlue());
                    break;
                case 1:
                    newColor = new Color(c.getRed(),(c.getGreen()&0x007)|((val&0x003)<<3),(val&0x07C)>>>2);
                    break;
            }
            palettes[colorPaletteIndexSprite/8][(colorPaletteIndexSprite%8)/2] = newColor;
            int r = newColor.getRed();
            int g = newColor.getGreen();
            int b = newColor.getBlue();

            palettesScaledSprite[colorPaletteIndexSprite/8][(colorPaletteIndexSprite%8)/2] = new Color((r<<3)|(r>>>2),(g<<3)|(g>>>2),(b<<3)|(b>>>2));
            //System.out.println(colorPaletteIndexSprite/8+" "+(colorPaletteIndexSprite%8)/2+" "+newColor.getRed()+" "+newColor.getGreen()+" "+newColor.getBlue());
            if(colorPaletteAutoIncrementSprite){
                colorPaletteIndexSprite = (colorPaletteIndexSprite+1)%64;
                // update palette index register (0xFF68)
                ioreg[0x0FF6A - 65280] = (byte) ((ioreg[0x0FF6A - 65280]&0x080) + (colorPaletteIndexSprite&0x03F));
            }
        }
        // FF70 WRAM bank
        if(address == 0x0FF70 && GBCMode){
            wramBank = val&0x07;
            if(wramBank==0){
                wramBank = 1;
                val = 1;
            }
        }
        if(address == APU.NR10){
            val |= 0x080;
        }
        // IO registers
        if(address < 65408) {
            ioreg[address - 65280] = val;
            if(address == 0xFF02 && val== -127){
                try {
                    System.out.print(new String(new byte[]{read(address-1)},"ascii"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        // HRAM
        if(address < 65535) {
            hram[address - 65408] = val;
            return;
        }
        // Interrupts Enable Register IE
        if(address == 65535) {
            iReg = val;
            //System.out.println(Integer.toBinaryString(val));
            return;
        }

        ;



    }

    public static void writeMBC1(int address, byte val){

        // Writing to ROM 0000-1FFF sets RAM ENABLE
        if(address < 8192) {
            if ((val & 0x0000000A) == 0x0A)
                ramExtEnable = true;
            else
                ramExtEnable = false;
            return;
        }
        // Writing to ROM 2000-3FFF sets the lower 5 bits of the ROM bank
        if(address < 16384) {
            int bankNum = val & 0x0000001F;
            if(bankNum == 0){
                bankNum = 1;
            }
            romBank = (romBank & 0x000000E0) + bankNum;
            return;
        }

        // Writing to ROM 4000-5FFF sets the Upper 2 bits of the ROM bank
        // OR sets the RAM bank
        if(address < 24576) {
            if(memModeSelect == ROM_MODE){
                int bankNum = val & 0x000000E0;
                romBank = bankNum + (romBank % 32);
            }else{
                ramExtBank = val & 0x0000001F;
            }
            return;
        }
        // Writing to ROM 6000-7FFF selects the memory Mode - Either ROM or RAM
        if(address < 32768) {
            int bankNum = val & 0x00000001;
            if(bankNum == 0){
                memModeSelect = ROM_MODE;
            }else{
                memModeSelect = RAM_MODE;
            }
            return;
        }

        System.err.println("Reached end of MBC1");

    }
    public static void writeMBC3(int address, byte val){

        // Writing to ROM 0000-1FFF sets RAM ENABLE
        if(address < 0x02000) {
            if ((val & 0x0000000A) == 0x0A)
                ramExtEnable = true;
            else
                ramExtEnable = false;
            return;
        }
        // Writing to ROM 2000-3FFF sets the lower 8 bits of the ROM bank
        if(address < 0x04000) {
            int bankNum = val & 0x000000FF;

            romBank = (romBank & 0x00000100) + bankNum;

            return;
        }

        // Writing to ROM 4000-5FFF sets the Upper 2 bits of the ROM bank
        // OR sets the RAM bank
        if(address < 0x06000) {

            ramExtBank = val & 0x000000FF;

            return;
        }
        // Writing to ROM 6000-7FFF Latches RTC Clock data
        if(address < 0x08000) {
            // TODO: implement MBC3 RTC clock Latching
            return;
        }


    }
    public static void writeMBC5(int address, byte val){

        // Writing to ROM 0000-1FFF sets RAM ENABLE
        if(address < 0x02000) {
            if ((val & 0x0000000A) == 0x0A)
                ramExtEnable = true;
            else if(val == 0)
                ramExtEnable = false;

            return;
        }
        // Writing to ROM 2000-2FFF sets the lower 8 bits of the ROM bank
        if(address < 0x03000) {
            int bankNum = val & 0x000000FF;

            romBank = (romBank & 0x00000100) + bankNum;
            return;
        }

        // Writing to ROM 3000-3FFF sets the Upper 1 bits of the ROM bank
        if(address < 0x04000) {
            romBank &= 0x0FF;
            if((val&0x01) !=0) {
                romBank |= (1 << 8);
            }

            return;
        }
        // Writing to ROM 4000-5FFF sets RAM bank
        if(address < 0x06000) {
            ramExtBank = val & 0x0F;
            return;
        }


    }
    public static void tickTimer(){
        cycles++;
        div++;
        if((div%8192) == 0 && div != 0){
            APU.tickFrameSequencer();
        }
        if(timerEnabled && cycles%timerDenom == 0){
            timer += 1;
            if(timer >= 256){
                timer = Memory.read(0x0FF06)&0x0FF;
                byte IF = Memory.read(0x0FF0F);
                IF |= 0x04;
                Memory.write(0x0FF0F, IF);
            }
        }
    }

    // Reads ROM header and sets the MBC type
    private static void setMBCType(){
        byte val = rom[327];
        switch(val){
            case 0:
                mbcType = NO_MBC;
                break;
            case 0x01:
            case 0x02:
            case 0x03:
                mbcType = MBC_1;
                break;
            case 0x0F:
            case 0x010:
            case 0x011:
            case 0x012:
            case 0x013:
                mbcType = MBC_3;
                break;
            case 0x019:
            case 0x01A:
            case 0x01B:
            case 0x01C:
            case 0x01D:
            case 0x01E:
                mbcType = MBC_5;
                break;
            default:
                System.err.println("UNDEFINED MBC TYPE: "+val);
                break;
        }
    }
    private static void setRomBankType(){
        byte val = rom[328];
        switch(val){
            case 0:
                maxRomBanks = 1000;
                break;
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                maxRomBanks = 2 << val;
                break;
            case 0x052:
                maxRomBanks = 72;
                break;
            case 0x053:
                maxRomBanks = 80;
                break;
            case 0x054:
                maxRomBanks = 96;
                break;
            default:
                System.err.println("UNDEFINED ROM BANK TYPE: "+val);
                break;
        }
    }

    private static void detectGBCmode(){
        byte val = rom[0x0143];
        if(val != 0){
            GBCMode = true;
        }
    }
    private static void padRom(byte[] loadedRom){
        rom = new byte[2_097_152];
        for(int i=0;i<loadedRom.length;i++){
            rom[i] = loadedRom[i];
        }
    }

}
