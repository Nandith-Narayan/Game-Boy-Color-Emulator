import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class APU {
    static SourceDataLine sdl;
    static int frameSequencer, ch1FrequencyTimer, ch1DutyPos, ch2FrequencyTimer, ch2DutyPos, ch3FrequencyTimer, ch3DutyPos, ch4FrequencyTimer;
    static int ch1PeriodTimer, ch1CurrentVolume, ch1LengthTimer, ch2PeriodTimer, ch2CurrentVolume, ch2LengthTimer, ch3LengthTimer, ch4PeriodTimer, ch4CurrentVolume, ch4LengthTimer;
    static int shadowFrequency, sweepTimer;
    static int lsfr=0x07FFF;
    static boolean sweepEnabled;

    static final int NR10=0x0FF10, NR11=0x0FF11, NR12=0x0FF12, NR13=0x0FF13, NR14=0x0FF14, NR21=0x0FF16, NR22=0x0FF17, NR23=0x0FF18, NR24=0x0FF19, NR31=0x0FF1B, NR32=0x0FF1C, NR33=0x0FF1D, NR34=0x0FF1E, NR41=0x0FF20, NR42=0x0FF21, NR43=0x0FF22, NR44=0x0FF23,NR50=0x0FF24, NR51=0x0FF25;
    static final int WAVE_TABLE_START = 0x0FF30;

    static final int[][] DUTY_TABLE = {{0,0,0,0,0,0,0,1}, {0,0,0,0,0,0,1,1}, {0,0,0,0,1,1,1,1}, {1,1,1,1,1,1,0,0}};
    static int totalCycles, bufferPos;
    static FileOutputStream out,out1,out2,out3,out4;

    static byte buffer[] = new byte[1024/64];

    public static void initiateAPU(){

        /*try {
            out1 = new FileOutputStream(new File("D:/GameBoyEmu/dst1.wav"));
            out2 = new FileOutputStream(new File("D:/GameBoyEmu/dst2.wav"));
            out3 = new FileOutputStream(new File("D:/GameBoyEmu/dst3.wav"));
            out4 = new FileOutputStream(new File("D:/GameBoyEmu/dst4.wav"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }*/


        AudioFormat af = new AudioFormat( (float )48000, 8, 1, true, false);

        try {
            out = new FileOutputStream("D:/GameBoyEmu/dst.wav");
            sdl = AudioSystem.getSourceDataLine(af);
            sdl.open();
            sdl.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }



        //sdl.drain();
        //sdl.stop();
    }

    public static void processCycles(int cycles){
        for(int i=0;i<cycles/4;i++){
            totalCycles += 4;
            // Update Channel 2 frequency timer
            ch2FrequencyTimer-=4;
            if(ch2FrequencyTimer <= 0){
                ch2DutyPos++;
                ch2DutyPos%=8;
                ch2FrequencyTimer = (Memory.read(NR23)&0x0FF) | ((Memory.read(NR24)&0x07)<<8);
                ch2FrequencyTimer = (2048 - ch2FrequencyTimer) * 4;
            }
            // Update Channel 1 frequency timer
            ch1FrequencyTimer-=4;
            if(ch1FrequencyTimer <= 0){
                ch1DutyPos++;
                ch1DutyPos%=8;
                ch1FrequencyTimer = (Memory.read(NR13)&0x0FF) | ((Memory.read(NR14)&0x07)<<8);
                ch1FrequencyTimer = (2048 - ch1FrequencyTimer) * 4;
            }
            // Update Channel 3 frequency timer
            ch3FrequencyTimer-=4;
            if(ch3FrequencyTimer <= 0){
                ch3DutyPos++;
                ch3DutyPos%=32;
                ch3FrequencyTimer = (Memory.read(NR33)&0x0FF) | ((Memory.read(NR34)&0x07)<<8);
                ch3FrequencyTimer = (2048 - ch3FrequencyTimer) * 4;
            }

            // Update Channel 4 frequency timer
            ch4FrequencyTimer-=4;
            if(ch4FrequencyTimer <= 0){
                int nr43 = Memory.read(NR43);
                int divisorCode = nr43 & 0x007;
                int widthMode = (nr43 & 0x008) >>> 3;
                int shiftAmount = (nr43 & 0x0F0) >>> 4;
                ch4FrequencyTimer = (divisorCode > 0 ? (divisorCode << 4) : 8) << shiftAmount;
                int xorVal = (lsfr&0x01) ^ ((lsfr&0x02)>>>1);
                lsfr = ((lsfr >>> 1) | (xorVal << 14));

                if(widthMode != 0){
                    lsfr &= 0x0BF;
                    lsfr |= (xorVal << 6);
                }

            }



            if(totalCycles % 88 == 0){
                generateSample();
            }
        }
    }

    public static void generateSample(){
        int nr51 = Memory.read(NR51);
        int nr50 = Memory.read(NR50);
        int ch2DutyChannel = (Memory.read(NR21) & 0x0C0) >>> 6;
        int ch1DutyChannel = (Memory.read(NR11) & 0x0C0) >>> 6;
        int ch2Val = (DUTY_TABLE[ch2DutyChannel][ch2DutyPos]);
        ch2Val *= ch2CurrentVolume;
        int ch1Val = (DUTY_TABLE[ch1DutyChannel][ch1DutyPos]);
        ch1Val *= ch1CurrentVolume;
        if(ch1LengthTimer <= 0 || (nr51&0x010) == 0){
            ch1Val = 7;
        }
        if(ch2LengthTimer <= 0|| (nr51&0x020) == 0){
            ch2Val = 7;
        }

        int waveSampleMask = 0x0F0;
        if(ch3DutyPos % 2 == 1){
            waveSampleMask = 0x00F;
        }

        int ch3Val = Memory.read(WAVE_TABLE_START+(ch3DutyPos/2))&waveSampleMask;

        if(ch3DutyPos % 2 == 0){
            ch3Val >>>= 4;
        }
        int waveShiftAmt = (Memory.read(NR32)&0x060) >>> 5;

        ch3Val >>>= waveShiftAmt;

        if(ch3LengthTimer <= 0 || (nr51&0x040) == 0){
            ch3Val = 7;
        }

        int ch4Val = (~lsfr) & 0x01;
        ch4Val *= ch4CurrentVolume;

        if(ch4LengthTimer <= 0 || (nr51&0x080) == 0){
            ch4Val = 7;
        }
        /*try {
            out1.write(ch1Val-7);
            out2.write(ch2Val-7);
            out3.write(ch3Val-7);
            out4.write(ch4Val-7);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        buffer[bufferPos] = (byte) ((ch1Val+ch2Val+ch3Val+ch4Val)/4);
        buffer[bufferPos] -= 7;
        // Master volume
        buffer[bufferPos] *= (byte)((nr50&0x070)>>>4);
        bufferPos++;
        if(bufferPos == buffer.length){
            bufferPos = 0;
            sdl.write(buffer,0,buffer.length);
            /*try {
                out.write(buffer,0,buffer.length);
            } catch (IOException e) {
                e.printStackTrace();
            }*/
           while(sdl.available() < 23500-buffer.length){

            }
        }



    }

    public static void tickFrameSequencer(){
        // tick Volume Envelope
        if(frameSequencer == 7){
            tickCh4VolumeEnvelope();
            tickCh2VolumeEnvelope();
            tickCh1VolumeEnvelope();
        }
        // tick Length Function
        if(frameSequencer%2 == 0){
            if(ch4LengthTimer >0 && (Memory.read(NR44)&0x040) != 0){
                ch4LengthTimer--;
            }
            if(ch3LengthTimer >0 && (Memory.read(NR34)&0x040) != 0){
                ch3LengthTimer--;
            }
            if(ch2LengthTimer >0 && (Memory.read(NR24)&0x040) != 0){
                ch2LengthTimer--;
            }
            if(ch1LengthTimer >0 && (Memory.read(NR14)&0x040) != 0){
                ch1LengthTimer--;
            }
        }
        // tick Sweep Function
        if(frameSequencer%4 == 2){
            if(sweepTimer > 0) {
                sweepTimer--;
            }
            if(sweepTimer == 0){
                byte nr10 = Memory.read(NR10);
                int sweepPeriod = (nr10 & 0x070)>>4;
                int sweepDir = (nr10 & 0x08) >>> 3;
                int sweepShift = nr10 & 0x07;
                if(sweepPeriod == 0){
                    sweepTimer = 8;
                }else {
                    sweepTimer = sweepPeriod;
                }
                if(sweepEnabled && sweepPeriod > 0){
                    int newFrequency = calculateSweepFrequency(sweepDir, sweepShift);

                    if(newFrequency <= 2047 && sweepShift > 0){
                        Memory.write(NR13, (byte) (newFrequency&0x0FF));
                        Memory.write(NR14, (byte) ((newFrequency&0x0700)>>8));
                        shadowFrequency = newFrequency;
                        calculateSweepFrequency(sweepDir, sweepShift);
                    }

                }

            }



        }

        frameSequencer++;
        frameSequencer %= 8;
    }

    public static int calculateSweepFrequency(int sweepDir, int sweepShift){
        int newFrequency = shadowFrequency >>> sweepShift;
        if(sweepDir == 1){
            newFrequency = shadowFrequency - newFrequency;
        }else{
            newFrequency = shadowFrequency + newFrequency;
        }

        if(newFrequency > 2047){
            sweepEnabled = false;
        }
        return newFrequency;
    }
    public static void tickCh4VolumeEnvelope(){
        byte nr42 = Memory.read(NR42);
        int period = nr42 & 0x07;
        int dir = (nr42 & 0x08) >>> 3;
        int volAdj;
        if(period != 0){
            if(ch4PeriodTimer > 0) {
                ch4PeriodTimer--;

                if (ch4PeriodTimer == 0) {
                    ch4PeriodTimer = period;

                    if ((ch4CurrentVolume < 0x0F && dir == 1) || (ch4CurrentVolume > 0x00 && dir == 0)) {
                        if (dir == 1) {
                            volAdj = 1;
                        } else {
                            volAdj = -1;
                        }
                        ch4CurrentVolume += volAdj;
                    }
                }
            }
        }
    }
    public static void tickCh2VolumeEnvelope(){
        byte nr22 = Memory.read(NR22);
        int period = nr22 & 0x07;
        int dir = (nr22 & 0x08) >>> 3;
        int volAdj;
        if(period != 0){
            if(ch2PeriodTimer > 0){
                ch2PeriodTimer--;
            }
            if(ch2PeriodTimer == 0){
                ch2PeriodTimer = period;
                if((ch2CurrentVolume < 0x0F && dir == 1) || (ch2CurrentVolume > 0x00 && dir == 0)){
                    if(dir == 1){
                        volAdj = 1;
                    }else{
                        volAdj = -1;
                    }
                    ch2CurrentVolume += volAdj;
                }
            }
        }
    }
    public static void tickCh1VolumeEnvelope(){
        byte nr12 = Memory.read(NR12);
        int period = nr12 & 0x07;
        int dir = (nr12 & 0x08) >>> 3;
        int volAdj;
        if(period != 0){
            if(ch1PeriodTimer > 0){
                ch1PeriodTimer--;
            }
            if(ch1PeriodTimer == 0){
                ch1PeriodTimer = period;
                if((ch1CurrentVolume < 0x0F && dir == 1) || (ch1CurrentVolume > 0x00 && dir == 0)){
                    if(dir == 1){
                        volAdj = 1;
                    }else{
                        volAdj = -1;
                    }
                    ch1CurrentVolume += volAdj;
                }
            }
        }
    }
    public static void triggerCh4EnvelopeEvent(){
        ch4PeriodTimer = Memory.read(NR42)&0x007;
        ch4CurrentVolume = (Memory.read(NR42)&0x0F0)>>>4;

        if(ch4LengthTimer <= 0){
            ch4LengthTimer =  64 - (Memory.read(NR41)&0x03F);
            ch4LengthTimer =  64;
        }
    }

    public static void triggerCh3Event(){


        if(ch3LengthTimer <= 0){
            ch3LengthTimer =  256 - (Memory.read(NR31)&0x0FF);
            ch3LengthTimer =  256;
        }
    }

    public static void triggerCh2EnvelopeEvent(){
        ch2PeriodTimer = Memory.read(NR22)&0x007;
        ch2CurrentVolume = (Memory.read(NR22)&0x0F0)>>>4;

        if(ch2LengthTimer <= 0){
            ch2LengthTimer =  64 - (Memory.read(NR21)&0x03F);
            ch2LengthTimer =  64;
        }
    }
    public static void triggerCh1EnvelopeEvent(){
        ch1PeriodTimer = Memory.read(NR12)&0x007;
        ch1CurrentVolume = (Memory.read(NR12)&0x0F0)>>>4;
        if(ch1LengthTimer <= 0){
            ch1LengthTimer =  64 - (Memory.read(NR11)&0x03F);
            ch1LengthTimer =  64;
        }

        shadowFrequency = (Memory.read(NR13)&0x0FF) | ((Memory.read(NR14)&0x07)<<8);
        byte nr10 = Memory.read(NR10);
        int sweepPeriod = (nr10 & 0x070)>>4;
        int sweepDir = (nr10 & 0x08) >>> 3;
        int sweepShift = nr10 & 0x07;
        if(sweepPeriod == 0){
            sweepTimer = 8;
        }else {
            sweepTimer = sweepPeriod;
        }
        if(sweepPeriod > 0 || sweepShift > 0){
            sweepEnabled = true;
        }
        if(sweepShift > 0){
            calculateSweepFrequency(sweepDir, sweepShift);
        }

    }



}
